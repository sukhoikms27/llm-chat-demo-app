package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextStrategyType
import com.example.myapplication.domain.model.DialogBranch
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BranchOperationsTest {

    private lateinit var repository: LlmRepository
    private lateinit var historyRepository: ChatHistoryRepository
    private lateinit var contextManager: ContextManager

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        contextManager = ContextManager()
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            id = i.toLong(),
            parentId = if (i > 1) (i - 1).toLong() else null,
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private val branchingConfig = GenerationConfig(
        contextStrategy = ContextStrategyType.BRANCHING,
        recentMessageCount = 6,
    )

    private fun stubChatResponse() {
        coEvery { repository.chat(any(), any(), any()) } returns
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
    }

    @Test
    fun `initialize creates main branch when history exists`() = runTest {
        val history = makeMessages(4)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.getBranches(any()) } returns emptyList()
        coEvery { historyRepository.saveBranch(any()) } returns 1L

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        assertEquals(1, agent.branches.size)
        coVerify { historyRepository.saveBranch(any()) }
    }

    @Test
    fun `createBranch trims history to checkpoint`() = runTest {
        val history = makeMessages(6)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.getBranches(any()) } returns emptyList()
        coEvery { historyRepository.saveBranch(any()) } returns 10L
        stubChatResponse()

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        // Initial history should be 6 messages
        assertEquals(6, agent.conversationHistory.size)

        // Create branch from checkpoint at message 3 (id=3)
        agent.createBranch(3L)

        // History should be trimmed to checkpoint (3 messages)
        assertEquals(3, agent.conversationHistory.size)
        assertEquals("Message 3", agent.conversationHistory.last().content)
    }

    @Test
    fun `switchBranch loads path from leaf to root`() = runTest {
        val allMessages = makeMessages(6)
        coEvery { historyRepository.loadHistory(any()) } returns allMessages
        // Provide branches before initialize
        coEvery { historyRepository.getBranches(any()) } returns listOf(
            DialogBranch(id = 1, chatId = 1, leafMessageId = 6L, name = "main"),
            DialogBranch(id = 2, chatId = 1, leafMessageId = 4L, name = "variant"),
        )

        // loadBranchMessages returns path root->leaf
        coEvery { historyRepository.loadBranchMessages(any(), any()) } answers {
            val leafId = secondArg<Long>()
            // Return messages 1..leafId (simulating parentId chain)
            allMessages.filter { it.id <= leafId }
        }

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        agent.switchBranch(2L)

        // Should load messages 1..4
        assertEquals(4, agent.conversationHistory.size)
        assertEquals(2L, agent.activeBranchId)
    }

    @Test
    fun `send updates leaf of active branch`() = runTest {
        val history = makeMessages(4)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.getBranches(any()) } returns listOf(
            DialogBranch(id = 1, chatId = 1, leafMessageId = 4L, name = "main"),
        )
        coEvery { historyRepository.saveMessage(any()) } returns 100L

        stubChatResponse()

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        agent.send("new message")

        // Branch leaf should be updated to the new assistant message id
        coVerify { historyRepository.updateBranchLeaf(1L, 100L) }
    }

    @Test
    fun `multiple branches are independent`() = runTest {
        val history = makeMessages(4)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.getBranches(any()) } returns emptyList()
        coEvery { historyRepository.saveBranch(any()) } returns 2L
        stubChatResponse()

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        // Create branch from checkpoint 3
        val branchId = agent.createBranch(3L)

        // Active branch should be the new one
        assertEquals(branchId, agent.activeBranchId)
        assertEquals(3, agent.conversationHistory.size)

        // There should be 2 branches now (main + new)
        assertEquals(2, agent.branches.size)
        assertNotNull(agent.branches.find { it.id == branchId })
    }

    @Test
    fun `renameBranch updates branch name`() = runTest {
        coEvery { historyRepository.loadHistory(any()) } returns makeMessages(4)
        coEvery { historyRepository.getBranches(any()) } returns listOf(
            DialogBranch(id = 1, chatId = 1, leafMessageId = 4L, name = "main"),
        )

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()

        agent.renameBranch(1L, "New Name")

        assertEquals("New Name", agent.branches.find { it.id == 1L }?.name)
    }

    @Test
    fun `createBranch generates name via LLM`() = runTest {
        val history = makeMessages(4)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.getBranches(any()) } returns emptyList()
        coEvery { historyRepository.saveBranch(any()) } returns 10L

        // Only the name generation will call repository.chat
        coEvery { repository.chat(any(), any(), any()) } returns
            AgentResponse("Обсуждение архитектуры", "test-model", MessageUsage(1, 1))

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", branchingConfig)
        agent.initialize()
        agent.createBranch(3L)

        // Name generation should have called repository.chat once
        coVerify(exactly = 1) { repository.chat(any(), any(), any()) }

        // Branch name should be updated
        val branch = agent.branches.find { it.id == 10L }
        assertNotNull(branch)
        assertEquals("Обсуждение архитектуры", branch?.name)
    }
}
