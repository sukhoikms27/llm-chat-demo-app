package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextStrategyType
import com.example.myapplication.domain.model.DialogFacts
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextStrategyIntegrationTest {

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
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private fun stubChatResponse() {
        coEvery { repository.chat(any(), any(), any()) } returns
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
    }

    @Test
    fun `SLIDING_WINDOW sends only last N messages to API`() = runTest {
        val history = makeMessages(20)
        coEvery { historyRepository.loadHistory(any()) } returns history
        stubChatResponse()

        val capturedContexts = mutableListOf<List<ChatMessage>>()
        coEvery { repository.chat(any(), any(), any()) } answers {
            capturedContexts.add(secondArg())
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
        }

        val config = GenerationConfig(
            contextStrategy = ContextStrategyType.SLIDING_WINDOW,
            recentMessageCount = 6,
        )
        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", config)
        agent.initialize()
        agent.send("new message")

        // First call to repository.chat is the actual send (not compression — SLIDING_WINDOW never compresses)
        val sentMessages = capturedContexts.first()
        assertTrue(
            "Expected at most ${config.recentMessageCount + 1} messages, got ${sentMessages.size}",
            sentMessages.size <= config.recentMessageCount + 1
        )
    }

    @Test
    fun `SUMMARIZATION preserves existing behavior with full history`() = runTest {
        val history = makeMessages(5)
        coEvery { historyRepository.loadHistory(any()) } returns history

        val capturedContexts = mutableListOf<List<ChatMessage>>()
        coEvery { repository.chat(any(), any(), any()) } answers {
            capturedContexts.add(secondArg())
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
        }

        val config = GenerationConfig(
            contextStrategy = ContextStrategyType.SUMMARIZATION,
            recentMessageCount = 6,
        )
        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", config)
        agent.initialize()
        agent.send("new message")

        // No compression (history < 10 threshold), so full history sent
        assertEquals(1, capturedContexts.size)
        // History (5) + new user message = 6, all sent since no compression
        val sentMessages = capturedContexts.first()
        assertTrue("Expected full history sent (>=6), got ${sentMessages.size}", sentMessages.size >= 6)
    }

    @Test
    fun `setGenerationConfig switches strategy at runtime`() = runTest {
        val history = makeMessages(5)
        coEvery { historyRepository.loadHistory(any()) } returns history

        val capturedContexts = mutableListOf<List<ChatMessage>>()
        coEvery { repository.chat(any(), any(), any()) } answers {
            capturedContexts.add(secondArg())
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
        }

        // Start with SUMMARIZATION
        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model",
            GenerationConfig(contextStrategy = ContextStrategyType.SUMMARIZATION))
        agent.initialize()
        agent.send("msg1")

        val summarizationSentCount = capturedContexts.last().size

        // Switch to SLIDING_WINDOW
        agent.setGenerationConfig(
            GenerationConfig(contextStrategy = ContextStrategyType.SLIDING_WINDOW, recentMessageCount = 4)
        )
        agent.send("msg2")

        val slidingSentCount = capturedContexts.last().size

        assertTrue(
            "Sliding window ($slidingSentCount) should send fewer messages than summarization ($summarizationSentCount)",
            slidingSentCount <= summarizationSentCount
        )
    }

    @Test
    fun `currentStrategyName reflects active strategy`() = runTest {
        coEvery { historyRepository.loadHistory(any()) } returns emptyList()
        stubChatResponse()

        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model",
            GenerationConfig(contextStrategy = ContextStrategyType.SLIDING_WINDOW))
        agent.initialize()

        assertEquals("Sliding Window", agent.currentStrategyName)

        agent.setGenerationConfig(
            GenerationConfig(contextStrategy = ContextStrategyType.SUMMARIZATION)
        )
        assertEquals("Сжатие (Summary)", agent.currentStrategyName)
    }

    @Test
    fun `STICKY_FACTS with existing facts sends system message with facts in context`() = runTest {
        val history = makeMessages(5)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.loadLatestFacts(any()) } returns
            DialogFacts.fromMap(1L, mapOf("goal" to "Собрать ТЗ"))

        val capturedContexts = mutableListOf<List<ChatMessage>>()
        coEvery { repository.chat(any(), any(), any()) } answers {
            capturedContexts.add(secondArg())
            AgentResponse("ok", "test-model", MessageUsage(1, 1))
        }

        val config = GenerationConfig(
            contextStrategy = ContextStrategyType.STICKY_FACTS,
            recentMessageCount = 6,
        )
        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", config)
        agent.initialize()

        // Facts should be loaded
        assertEquals("Sticky Facts", agent.currentStrategyName)
        assertTrue("currentFacts should be loaded", agent.currentFacts?.isNotEmpty() == true)

        agent.send("new message")

        // First chat call is the actual send — should contain system message with facts
        val sentMessages = capturedContexts.first()
        val systemMsg = sentMessages.find { it.role == MessageRole.SYSTEM }
        assertTrue("Should contain system message with facts", systemMsg?.content?.contains("Собрать ТЗ") == true)
    }

    @Test
    fun `STICKY_FACTS triggers facts extraction after send`() = runTest {
        val history = makeMessages(5)
        coEvery { historyRepository.loadHistory(any()) } returns history
        coEvery { historyRepository.loadLatestFacts(any()) } returns null

        var chatCallCount = 0
        coEvery { repository.chat(any(), any(), any()) } answers {
            chatCallCount++
            if (chatCallCount == 1) {
                // First call: the actual response
                AgentResponse("ok", "test-model", MessageUsage(1, 1))
            } else {
                // Second call: facts extraction response
                AgentResponse("{\"goal\": \"Новая цель\"}", "test-model", MessageUsage(1, 1))
            }
        }

        val config = GenerationConfig(
            contextStrategy = ContextStrategyType.STICKY_FACTS,
            recentMessageCount = 6,
        )
        val agent = LlmAgentImpl(repository, historyRepository, contextManager, "test-model", config)
        agent.initialize()
        agent.send("new message")

        // Should have called repository.chat twice: 1 for response, 1 for facts extraction
        assertEquals("Expected 2 chat calls", 2, chatCallCount)

        // Facts should be extracted and saved
        coVerify(atLeast = 1) { historyRepository.saveFacts(any()) }
        assertTrue("currentFacts should be populated", agent.currentFacts?.containsKey("goal") == true)
    }
}
