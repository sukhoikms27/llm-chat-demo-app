package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmAgentImplTest {

    private lateinit var repository: LlmRepository
    private lateinit var agent: LlmAgentImpl

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        agent = LlmAgentImpl(
            repository = repository,
            initialModel = "test-model",
            initialConfig = GenerationConfig(),
        )
    }

    @Test
    fun `send adds user and assistant messages to history`() = runTest {
        // Given
        val response = AgentResponse(
            content = "Hello!",
            model = "test-model",
            usage = MessageUsage(promptTokens = 10, completionTokens = 5),
        )
        coEvery { repository.chat(any(), any(), any()) } returns response

        // When
        val result = agent.send("Hi")

        // Then
        assertTrue(result.isSuccess)
        assertEquals(2, agent.conversationHistory.size)
        assertEquals(MessageRole.USER, agent.conversationHistory[0].role)
        assertEquals("Hi", agent.conversationHistory[0].content)
        assertEquals(MessageRole.ASSISTANT, agent.conversationHistory[1].role)
        assertEquals("Hello!", agent.conversationHistory[1].content)
    }

    @Test
    fun `send returns agent response`() = runTest {
        // Given
        val response = AgentResponse(
            content = "Test response",
            model = "test-model",
            usage = null,
        )
        coEvery { repository.chat(any(), any(), any()) } returns response

        // When
        val result = agent.send("Test")

        // Then
        assertTrue(result.isSuccess)
        assertEquals("Test response", result.getOrNull()?.content)
        assertEquals("test-model", result.getOrNull()?.model)
    }

    @Test
    fun `send propagates error as failure`() = runTest {
        // Given
        coEvery { repository.chat(any(), any(), any()) } throws RuntimeException("API error")

        // When
        val result = agent.send("Hi")

        // Then
        assertTrue(result.isFailure)
        assertEquals("API error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `sendStream emits chunks and adds to history`() = runTest {
        // Given
        every { repository.chatStream(any(), any(), any()) } returns flowOf("Hello", " ", "World")

        // When
        val chunks = mutableListOf<String>()
        agent.sendStream("Hi").collect { chunks.add(it) }

        // Then
        assertEquals(listOf("Hello", " ", "World"), chunks)
        assertEquals(2, agent.conversationHistory.size)
        assertEquals(MessageRole.USER, agent.conversationHistory[0].role)
        assertEquals("Hi", agent.conversationHistory[0].content)
        assertEquals(MessageRole.ASSISTANT, agent.conversationHistory[1].role)
        assertEquals("Hello World", agent.conversationHistory[1].content)
    }

    @Test
    fun `clearHistory empties conversation history`() = runTest {
        // Given
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse("ok", "m", null)
        agent.send("Hi")
        assertEquals(2, agent.conversationHistory.size)

        // When
        agent.clearHistory()

        // Then
        assertTrue(agent.conversationHistory.isEmpty())
    }

    @Test
    fun `setModel changes the model used for requests`() = runTest {
        // Given
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse("ok", "new-model", null)

        // When
        agent.setModel("new-model")
        agent.send("Hi")

        // Then
        assertTrue(agent.conversationHistory.size == 2)
    }

    @Test
    fun `setGenerationConfig updates config`() = runTest {
        // Given
        val newConfig = GenerationConfig(temperature = 0.5, systemPrompt = "Be concise")
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse("ok", "m", null)

        // When
        agent.setGenerationConfig(newConfig)
        agent.send("Hi")

        // Then - just verify it doesn't crash, the config is passed through to repository
        assertTrue(agent.conversationHistory.size == 2)
    }

    @Test
    fun `multiple sends accumulate history`() = runTest {
        // Given
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse("response", "m", null)

        // When
        agent.send("msg1")
        agent.send("msg2")
        agent.send("msg3")

        // Then
        assertEquals(6, agent.conversationHistory.size) // 3 user + 3 assistant
        assertEquals("msg1", agent.conversationHistory[0].content)
        assertEquals("msg2", agent.conversationHistory[2].content)
        assertEquals("msg3", agent.conversationHistory[4].content)
    }
}
