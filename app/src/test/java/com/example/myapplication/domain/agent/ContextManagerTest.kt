package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextManagerTest {

    private lateinit var contextManager: ContextManager

    @Before
    fun setUp() {
        contextManager = ContextManager()
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private val defaultConfig = GenerationConfig()

    // --- needsCompression ---

    @Test
    fun `needsCompression returns false when history short`() {
        val history = makeMessages(8) // 6 + 4 = 10 threshold, 8 < 10
        assertFalse(contextManager.needsCompression(history, defaultConfig))
    }

    @Test
    fun `needsCompression returns true when threshold reached`() {
        val history = makeMessages(10) // 6 + 4 = 10, exactly at threshold
        assertTrue(contextManager.needsCompression(history, defaultConfig))
    }

    @Test
    fun `needsCompression returns false when compression disabled`() {
        val history = makeMessages(20)
        val config = defaultConfig.copy(contextCompressionEnabled = false)
        assertFalse(contextManager.needsCompression(history, config))
    }

    @Test
    fun `needsCompression respects custom recentMessageCount and summarizeInterval`() {
        val config = defaultConfig.copy(recentMessageCount = 3, summarizeInterval = 2)
        assertFalse(contextManager.needsCompression(makeMessages(4), config)) // 4 < 5
        assertTrue(contextManager.needsCompression(makeMessages(5), config))   // 5 >= 5
    }

    // --- getMessagesToSummarize ---

    @Test
    fun `getMessagesToSummarize returns all except last N`() {
        val history = makeMessages(10)
        val toSummarize = contextManager.getMessagesToSummarize(history, defaultConfig)
        assertEquals(4, toSummarize.size) // 10 - 6 = 4
        assertEquals("Message 1", toSummarize[0].content)
        assertEquals("Message 4", toSummarize[3].content)
    }

    @Test
    fun `getMessagesToSummarize returns empty when history shorter than N`() {
        val history = makeMessages(3)
        val toSummarize = contextManager.getMessagesToSummarize(history, defaultConfig)
        assertTrue(toSummarize.isEmpty())
    }

    // --- buildSummarizationRequest ---

    @Test
    fun `buildSummarizationRequest includes system prompt with instructions`() {
        val messages = makeMessages(4)
        val result = contextManager.buildSummarizationRequest(messages, null)
        assertEquals(2, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertTrue(result[0].content.contains("сжатию контекста"))
        assertTrue(result[0].content.contains("ФАКТЫ"))
        assertTrue(result[0].content.contains("РЕШЕНИЯ"))
        assertTrue(result[0].content.contains("ОТКРЫТЫЕ ВОПРОСЫ"))
    }

    @Test
    fun `buildSummarizationRequest includes user content with messages`() {
        val messages = makeMessages(2)
        val result = contextManager.buildSummarizationRequest(messages, null)
        assertEquals(MessageRole.USER, result[1].role)
        assertTrue(result[1].content.contains("Message 1"))
        assertTrue(result[1].content.contains("Message 2"))
    }

    @Test
    fun `buildSummarizationRequest includes previous summary when present`() {
        val messages = makeMessages(4)
        val previousSummary = ContextSummary(
            chatId = 1,
            rootMessageId = null,
            content = "Previous summary content here",
        )
        val result = contextManager.buildSummarizationRequest(messages, previousSummary)
        assertTrue(result[1].content.contains("Previous summary content here"))
        assertTrue(result[1].content.contains("предыдущее саммари"))
    }

    @Test
    fun `buildSummarizationRequest without previous summary does not reference it`() {
        val messages = makeMessages(2)
        val result = contextManager.buildSummarizationRequest(messages, null)
        assertFalse(result[1].content.contains("предыдущее саммари"))
    }

    // --- buildContextForRequest ---

    @Test
    fun `buildContextForRequest with summary returns system and recent N`() {
        val history = makeMessages(10)
        val summary = ContextSummary(
            chatId = 1,
            rootMessageId = null,
            content = "Summary of earlier conversation",
        )
        val result = contextManager.buildContextForRequest(history, summary, defaultConfig)
        // 1 system + recentMessageCount (6) = 7
        assertEquals(1 + defaultConfig.recentMessageCount, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertTrue(result[0].content.contains("Summary of earlier conversation"))
        // Last 6 messages preserved
        assertEquals("Message 5", result[1].content)
        assertEquals("Message 10", result.last().content)
    }

    @Test
    fun `buildContextForRequest without summary returns full history`() {
        val history = makeMessages(5)
        val result = contextManager.buildContextForRequest(history, null, defaultConfig)
        assertEquals(5, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `buildContextForRequest with blank summary returns full history`() {
        val history = makeMessages(5)
        val summary = ContextSummary(chatId = 1, rootMessageId = null, content = "")
        val result = contextManager.buildContextForRequest(history, summary, defaultConfig)
        assertEquals(5, result.size)
    }

    // --- estimateTokens ---

    @Test
    fun `estimateTokens returns approximately chars divided by 4`() {
        assertEquals(0, contextManager.estimateTokens(""))
        assertEquals(0, contextManager.estimateTokens("abc"))       // 3/4 = 0
        assertEquals(1, contextManager.estimateTokens("abcd"))      // 4/4 = 1
        assertEquals(5, contextManager.estimateTokens("abcdefghijklmnopqrst")) // 20/4 = 5
    }

    // --- estimateTokensSaved ---

    @Test
    fun `estimateTokensSaved returns zero when compressed is larger`() {
        val full = makeMessages(2)
        val compressed = makeMessages(5) // More messages = more tokens
        val saved = contextManager.estimateTokensSaved(full, compressed)
        assertEquals(0, saved)
    }

    @Test
    fun `estimateTokensSaved returns positive when compression reduces tokens`() {
        val full = makeMessages(10) // 10 messages
        val compressed = makeMessages(3) // 3 messages (summary + recent)
        val saved = contextManager.estimateTokensSaved(full, compressed)
        assertTrue(saved > 0)
    }

    // --- createSummary ---

    @Test
    fun `createSummary accumulates summarizedCount from previous summary`() {
        val previous = ContextSummary(
            chatId = 1,
            rootMessageId = null,
            content = "old",
            summarizedCount = 10,
        )
        val result = contextManager.createSummary(
            chatId = 1,
            rootMessageId = null,
            summaryContent = "new summary",
            summarizedCount = 5,
            previousSummary = previous,
        )
        assertEquals(15, result.summarizedCount) // 10 + 5
        assertEquals("new summary", result.content)
    }

    @Test
    fun `createSummary without previous summary starts from zero`() {
        val result = contextManager.createSummary(
            chatId = 1,
            rootMessageId = null,
            summaryContent = "first summary",
            summarizedCount = 4,
            previousSummary = null,
        )
        assertEquals(4, result.summarizedCount)
    }

    @Test
    fun `createSummary estimates token count`() {
        val result = contextManager.createSummary(
            chatId = 1,
            rootMessageId = null,
            summaryContent = "a".repeat(40), // 40/4 = 10 tokens
            summarizedCount = 4,
            previousSummary = null,
        )
        assertEquals(10, result.tokenEstimate)
    }
}
