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

class StickyFactsStrategyTest {

    private lateinit var strategy: StickyFactsStrategy
    private val facts = mapOf("goal" to "Собрать ТЗ", "constraint" to "Бюджет 500к")

    @Before
    fun setUp() {
        strategy = StickyFactsStrategy(facts)
    }

    private fun makeMessages(count: Int): List<ChatMessage> = (1..count).map { i ->
        ChatMessage(
            role = if (i % 2 == 1) MessageRole.USER else MessageRole.ASSISTANT,
            content = "Message $i",
        )
    }

    private val defaultConfig = GenerationConfig()

    @Test
    fun `buildContext with facts prepends system message and takes last N`() {
        val history = makeMessages(20)
        val result = strategy.buildContext(history, null, defaultConfig)

        // system message + last N messages
        assertEquals(defaultConfig.recentMessageCount + 1, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertTrue(result[0].content.contains("факты"))
        assertTrue(result[0].content.contains("goal"))
        assertTrue(result[0].content.contains("Собрать ТЗ"))

        // Remaining messages should be the last N
        assertEquals(defaultConfig.recentMessageCount, result.size - 1)
        assertEquals("Message ${20 - defaultConfig.recentMessageCount + 1}", result[1].content)
        assertEquals("Message 20", result.last().content)
    }

    @Test
    fun `buildContext without facts returns only takeLast N`() {
        val emptyFactsStrategy = StickyFactsStrategy(emptyMap())
        val history = makeMessages(20)
        val result = emptyFactsStrategy.buildContext(history, null, defaultConfig)

        assertEquals(defaultConfig.recentMessageCount, result.size)
        // No system message
        assertFalse(result.any { it.role == MessageRole.SYSTEM && it.content.contains("Факты") })
        assertEquals("Message ${20 - defaultConfig.recentMessageCount + 1}", result[0].content)
    }

    @Test
    fun `buildContext with N greater than history returns system message plus all history`() {
        val history = makeMessages(3)
        val result = strategy.buildContext(history, null, defaultConfig)

        // system + all 3 messages
        assertEquals(4, result.size)
        assertEquals(MessageRole.SYSTEM, result[0].role)
        assertEquals("Message 1", result[1].content)
        assertEquals("Message 3", result.last().content)
    }

    @Test
    fun `needsCompression always returns false`() {
        val history = makeMessages(100)
        assertFalse(strategy.needsCompression(history, defaultConfig))
    }

    @Test
    fun `buildFactsExtractionRequest includes current facts when present`() {
        val recentMessages = makeMessages(4)
        val request = StickyFactsStrategy.buildFactsExtractionRequest(recentMessages, facts)

        // system + user message
        assertEquals(2, request.size)
        assertEquals(MessageRole.SYSTEM, request[0].role)
        assertEquals(MessageRole.USER, request[1].role)

        val userContent = request[1].content
        assertTrue("Should contain current facts", userContent.contains("Текущие факты"))
        assertTrue("Should contain 'goal' key", userContent.contains("goal"))
        assertTrue("Should contain value", userContent.contains("Собрать ТЗ"))
        assertTrue("Should contain recent messages", userContent.contains("Message 1"))
        assertTrue("Should contain extraction instruction", userContent.contains("Извлеки факты"))
    }

    @Test
    fun `buildFactsExtractionRequest without current facts omits facts section`() {
        val recentMessages = makeMessages(4)
        val request = StickyFactsStrategy.buildFactsExtractionRequest(recentMessages, emptyMap())

        assertEquals(2, request.size)
        val userContent = request[1].content
        assertFalse("Should not contain 'Текущие факты' section", userContent.contains("Текущие факты"))
        assertTrue("Should contain extraction instruction", userContent.contains("Извлеки факты"))
    }

    @Test
    fun `parseFactsResponse parses valid JSON`() {
        val response = """{"goal": "Написать код", "decision": "Использовать Kotlin"}"""
        val result = StickyFactsStrategy.parseFactsResponse(response)

        assertEquals(2, result.size)
        assertEquals("Написать код", result["goal"])
        assertEquals("Использовать Kotlin", result["decision"])
    }

    @Test
    fun `parseFactsResponse parses JSON wrapped in markdown code block`() {
        val response = """
            ```json
            {"goal": "Обновить БД", "constraint": "Android 14"}
            ```
        """.trimIndent()
        val result = StickyFactsStrategy.parseFactsResponse(response)

        assertEquals(2, result.size)
        assertEquals("Обновить БД", result["goal"])
        assertEquals("Android 14", result["constraint"])
    }

    @Test
    fun `parseFactsResponse returns empty map for garbage`() {
        val result1 = StickyFactsStrategy.parseFactsResponse("Это не JSON")
        assertTrue(result1.isEmpty())

        val result2 = StickyFactsStrategy.parseFactsResponse("")
        assertTrue(result2.isEmpty())

        val result3 = StickyFactsStrategy.parseFactsResponse("{}")
        assertTrue(result3.isEmpty())
    }

    @Test
    fun `displayName and description are not blank`() {
        assertTrue(strategy.displayName.isNotBlank())
        assertTrue(strategy.description.isNotBlank())
    }
}
