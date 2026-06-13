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

class SummaryStrategyTest {

    private lateinit var contextManager: ContextManager
    private lateinit var strategy: SummaryStrategy

    @Before
    fun setUp() {
        contextManager = ContextManager()
        strategy = SummaryStrategy(contextManager)
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private val defaultConfig = GenerationConfig()

    @Test
    fun `buildContext without summary returns full history`() {
        val history = makeMessages(5)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(5, result.size)
        assertEquals(history, result)
    }

    @Test
    fun `buildContext with summary returns system msg plus recent N`() {
        val history = makeMessages(10)
        val summary = ContextSummary(
            chatId = 1,
            rootMessageId = null,
            content = "Summary of earlier conversation",
        )
        val result = strategy.buildContext(history, summary, defaultConfig)

        // 1 system + recentMessageCount (6) = 7
        assertEquals(1 + defaultConfig.recentMessageCount, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertTrue(result[0].content.contains("Summary of earlier conversation"))
    }

    @Test
    fun `needsCompression returns true at threshold`() {
        val history = makeMessages(10) // 6 + 4 = 10
        assertTrue(strategy.needsCompression(history, defaultConfig))
    }

    @Test
    fun `needsCompression returns false below threshold`() {
        val history = makeMessages(8) // 8 < 10
        assertFalse(strategy.needsCompression(history, defaultConfig))
    }

    @Test
    fun `needsCompression respects disabled flag`() {
        val history = makeMessages(20)
        val config = defaultConfig.copy(contextCompressionEnabled = false)
        assertFalse(strategy.needsCompression(history, config))
    }

    @Test
    fun `displayName and description are not blank`() {
        assertTrue(strategy.displayName.isNotBlank())
        assertTrue(strategy.description.isNotBlank())
    }
}
