package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BranchingStrategyTest {

    private lateinit var strategy: BranchingStrategy
    private val defaultConfig = GenerationConfig()

    @Before
    fun setUp() {
        strategy = BranchingStrategy()
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    @Test
    fun `buildContext takes last N messages`() {
        val history = makeMessages(20)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(defaultConfig.recentMessageCount, result.size)
        assertEquals("Message ${20 - defaultConfig.recentMessageCount + 1}", result.first().content)
        assertEquals("Message 20", result.last().content)
    }

    @Test
    fun `buildContext with N greater than history returns all history`() {
        val history = makeMessages(3)
        val result = strategy.buildContext(history, null, defaultConfig)

        assertEquals(3, result.size)
        assertEquals("Message 1", result.first().content)
        assertEquals("Message 3", result.last().content)
    }

    @Test
    fun `needsCompression always returns false`() {
        val history = makeMessages(100)
        assertFalse(strategy.needsCompression(history, defaultConfig))
    }

    @Test
    fun `displayName is correct`() {
        assertEquals("Ветвление", strategy.displayName)
    }
}
