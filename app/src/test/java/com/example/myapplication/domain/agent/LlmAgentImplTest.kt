package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmAgentImplTest {

    private lateinit var repository: LlmRepository
    private lateinit var historyRepository: ChatHistoryRepository
    private lateinit var contextManager: ContextManager
    private lateinit var agent: LlmAgentImpl

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        contextManager = ContextManager()
        agent = LlmAgentImpl(
            repository = repository,
            historyRepository = historyRepository,
            contextManager = contextManager,
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
        val streamUsage = MessageUsage(promptTokens = 8, completionTokens = 6)
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.Chunk("Hello"),
            StreamEvent.Chunk(" "),
            StreamEvent.Chunk("World"),
            StreamEvent.Done(usage = streamUsage),
        )

        // When
        val chunks = mutableListOf<StreamEvent>()
        agent.sendStream("Hi").collect { chunks.add(it) }

        // Then
        assertEquals(4, chunks.size)
        assertEquals("Hello", (chunks[0] as StreamEvent.Chunk).content)
        assertEquals(" ", (chunks[1] as StreamEvent.Chunk).content)
        assertEquals("World", (chunks[2] as StreamEvent.Chunk).content)
        assertTrue(chunks[3] is StreamEvent.Done)

        assertEquals(2, agent.conversationHistory.size)
        assertEquals(MessageRole.USER, agent.conversationHistory[0].role)
        assertEquals("Hi", agent.conversationHistory[0].content)
        assertEquals(MessageRole.ASSISTANT, agent.conversationHistory[1].role)
        assertEquals("Hello World", agent.conversationHistory[1].content)
    }

    @Test
    fun `sendStream saves usage in assistant message`() = runTest {
        // Given
        val streamUsage = MessageUsage(promptTokens = 15, completionTokens = 10)
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.Chunk("Response"),
            StreamEvent.Done(usage = streamUsage),
        )

        // When
        agent.sendStream("Hi").collect {}

        // Then
        val assistantMsg = agent.conversationHistory.last()
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
        assertEquals(streamUsage, assistantMsg.usage)
    }

    @Test
    fun `clearHistory empties conversation history and resets totalUsage`() = runTest {
        // Given
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse("ok", "m", MessageUsage(5, 3))
        agent.send("Hi")
        assertEquals(2, agent.conversationHistory.size)
        assertTrue(agent.totalUsage.totalTokens > 0)

        // When
        agent.clearHistory()

        // Then
        assertTrue(agent.conversationHistory.isEmpty())
        assertEquals(0, agent.totalUsage.totalTokens)
        assertEquals(0.0, agent.totalUsage.estimatedCost, 0.001)
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

        // Then - just verify it doesn't crash
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

    @Test
    fun `totalUsage accumulates from send calls`() = runTest {
        // Given — use a model from the Pricing table so cost is calculated
        agent.setModel("GLM-4.5-air")
        coEvery { repository.chat(any(), any(), any()) } returns AgentResponse(
            "ok", "GLM-4.5-air", MessageUsage(promptTokens = 10, completionTokens = 5)
        )

        // When
        agent.send("msg1")
        agent.send("msg2")

        // Then
        val usage = agent.totalUsage
        assertEquals(20, usage.totalPromptTokens)   // 10 * 2
        assertEquals(10, usage.totalCompletionTokens) // 5 * 2
        assertEquals(30, usage.totalTokens)           // 20 + 10
        assertEquals(2, usage.messageCount)
        assertTrue(usage.estimatedCost > 0)
    }

    @Test
    fun `totalUsage accumulates from sendStream calls`() = runTest {
        // Given
        val streamUsage = MessageUsage(promptTokens = 8, completionTokens = 6)
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.Chunk("Hello"),
            StreamEvent.Done(usage = streamUsage),
        )

        // When
        agent.sendStream("msg1").collect {}
        agent.sendStream("msg2").collect {}

        // Then
        val usage = agent.totalUsage
        assertEquals(16, usage.totalPromptTokens)   // 8 * 2
        assertEquals(12, usage.totalCompletionTokens) // 6 * 2
        assertEquals(28, usage.totalTokens)           // 16 + 12
        assertEquals(2, usage.messageCount)
    }

    @Test
    fun `totalUsage is zero when no messages sent`() {
        val usage = agent.totalUsage
        assertEquals(0, usage.totalPromptTokens)
        assertEquals(0, usage.totalCompletionTokens)
        assertEquals(0, usage.totalTokens)
        assertEquals(0.0, usage.estimatedCost, 0.001)
        assertEquals(0, usage.messageCount)
    }

    @Test
    fun `sendStream accumulates reasoning content in assistant message`() = runTest {
        // Given
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.ReasoningChunk("Let me think..."),
            StreamEvent.ReasoningChunk(" step by step."),
            StreamEvent.Chunk("The answer is 42."),
            StreamEvent.Done(usage = null),
        )

        // When
        agent.sendStream("Hi").collect {}

        // Then
        val assistantMsg = agent.conversationHistory.last()
        assertEquals(MessageRole.ASSISTANT, assistantMsg.role)
        assertEquals("The answer is 42.", assistantMsg.content)
        assertEquals("Let me think... step by step.", assistantMsg.reasoningContent)
    }

    @Test
    fun `sendStream emits reasoning chunks`() = runTest {
        // Given
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.ReasoningChunk("thinking"),
            StreamEvent.Chunk("answer"),
            StreamEvent.Done(usage = null),
        )

        // When
        val events = mutableListOf<StreamEvent>()
        agent.sendStream("Hi").collect { events.add(it) }

        // Then
        assertEquals(3, events.size)
        assertTrue(events[0] is StreamEvent.ReasoningChunk)
        assertEquals("thinking", (events[0] as StreamEvent.ReasoningChunk).content)
        assertTrue(events[1] is StreamEvent.Chunk)
        assertEquals("answer", (events[1] as StreamEvent.Chunk).content)
        assertTrue(events[2] is StreamEvent.Done)
    }

    @Test
    fun `send includes reasoning content from non-streaming response`() = runTest {
        // Given
        val response = AgentResponse(
            content = "The answer is 42.",
            model = "test-model",
            usage = null,
            reasoningContent = "I thought about it carefully.",
        )
        coEvery { repository.chat(any(), any(), any()) } returns response

        // When
        agent.send("Hi")

        // Then
        val assistantMsg = agent.conversationHistory.last()
        assertEquals("The answer is 42.", assistantMsg.content)
        assertEquals("I thought about it carefully.", assistantMsg.reasoningContent)
    }

    @Test
    fun `sendStream sets null reasoningContent when no reasoning chunks`() = runTest {
        // Given
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.Chunk("Just a regular response."),
            StreamEvent.Done(usage = null),
        )

        // When
        agent.sendStream("Hi").collect {}

        // Then
        val assistantMsg = agent.conversationHistory.last()
        assertEquals(null, assistantMsg.reasoningContent)
    }

    @Test
    fun `sendStream with only reasoning and no content chunks`() = runTest {
        // Given — simulates API that sends reasoning but no content
        every { repository.chatStream(any(), any(), any()) } returns flowOf(
            StreamEvent.ReasoningChunk("I'm thinking about this..."),
            StreamEvent.Done(usage = null),
        )

        // When
        agent.sendStream("Hi").collect {}

        // Then
        val assistantMsg = agent.conversationHistory.last()
        assertEquals("", assistantMsg.content)
        assertEquals("I'm thinking about this...", assistantMsg.reasoningContent)
    }
}
