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

class SlidingWindowStrategyTest {

    private lateinit var strategy: SlidingWindowStrategy

    @Before
    fun setUp() {
        strategy = SlidingWindowStrategy()
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private val defaultConfig = GenerationConfig()

    @Test
    fun `buildContext returns only last N messages when history is longer`() {
        val history = makeMessages(20)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(defaultConfig.recentMessageCount, result.size)
        // Should be the last 6 messages (messages 15..20)
        assertEquals("Message 15", result[0].content)
        assertEquals("Message 20", result.last().content)
    }

    @Test
    fun `buildContext returns full history when shorter than N`() {
        val history = makeMessages(3)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(3, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `buildContext returns exactly N when history equals N`() {
        val history = makeMessages(defaultConfig.recentMessageCount)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(defaultConfig.recentMessageCount, result.size)
    }

    @Test
    fun `buildContext ignores summary`() {
        val history = makeMessages(20)
        val summary = ContextSummary(
            chatId = 1,
            rootMessageId = null,
            content = "Some summary content",
        )
        val result = strategy.buildContext(history, summary, defaultConfig)

        // No system message prepended — just last N
        assertEquals(defaultConfig.recentMessageCount, result.size)
        assertEquals(MessageRole.USER, result[0].role) // message 15 is odd → user
        assertEquals("Message 15", result[0].content)
    }

    @Test
    fun `buildContext respects custom recentMessageCount`() {
        val history = makeMessages(20)
        val config = defaultConfig.copy(recentMessageCount = 3)
        val result = strategy.buildContext(history, null, config)

        assertEquals(3, result.size)
        assertEquals("Message 18", result[0].content)
        assertEquals("Message 20", result.last().content)
    }

    @Test
    fun `buildContext with empty history returns empty list`() {
        val result = strategy.buildContext(emptyList(), null, defaultConfig)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `needsCompression always returns false`() {
        val history = makeMessages(100)
        assertFalse(strategy.needsCompression(history, defaultConfig))
    }

    @Test
    fun `displayName and description are not blank`() {
        assertTrue(strategy.displayName.isNotBlank())
        assertTrue(strategy.description.isNotBlank())
    }
}
