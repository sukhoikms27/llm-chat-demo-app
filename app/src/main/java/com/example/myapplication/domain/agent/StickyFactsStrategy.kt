package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole

/**
 * Стратегия Sticky Facts / Key-Value Memory.
 *
 * В запрос к API: system(facts) + последние N сообщений.
 * После каждого ответа модели — извлечение ключевых фактов и их обновление.
 */
class StickyFactsStrategy(
    private val facts: Map<String, String>,
) : ContextStrategy {

    override val displayName: String = "Sticky Facts"
    override val description: String = "Извлечение фактов + последние N сообщений"

    override fun buildContext(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage> {
        val recentMessages = history.takeLast(config.recentMessageCount)

        return if (facts.isNotEmpty()) {
            val factsMsg = ChatMessage(
                role = MessageRole.SYSTEM,
                content = buildFactsSystemMessage(facts),
            )
            listOf(factsMsg) + recentMessages
        } else {
            recentMessages
        }
    }

    override fun needsCompression(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): Boolean = false

    companion object {

        /**
         * Системный промпт для извлечения фактов из диалога.
         * Модель должна вернуть JSON в формате Map.
         */
        val factsExtractionSystemPrompt = """
            Ты — система извлечения фактов из диалога.
            Проанализируй последние сообщения и извлеки ключевые факты.

            Категории фактов, которые нужно отслеживать:
            - goal — основная цель диалога или задачи
            - constraint — ограничения, требования, дедлайны
            - preference — предпочтения пользователя
            - decision — принятые решения
            - context — важный контекст (имена, цифры, ссылки)
            - open_question — нерешённые вопросы

            Правила:
            1. Возвращай ТОЛЬКО валидный JSON без markdown и пояснений
            2. Формат: объект со строковыми ключами и значениями
            3. Используй короткие ключи на английском
            4. Значения пиши кратко, на русском
            5. Если новых фактов нет — верни пустой объект {}
            6. Если есть существующие факты — обнови их при необходимости

            Пример ответа:
            {"goal": "Собрать ТЗ для мобильного приложения", "constraint": "Бюджет 500к", "decision": "Kotlin + Compose"}
        """.trimIndent()

        /**
         * Формирует сообщения для запроса на извлечение фактов.
         * Включает текущие факты для инкрементального обновления.
         */
        fun buildFactsExtractionRequest(
            recentMessages: List<ChatMessage>,
            currentFacts: Map<String, String>,
        ): List<ChatMessage> {
            val systemMsg = ChatMessage(
                role = MessageRole.SYSTEM,
                content = factsExtractionSystemPrompt,
            )

            val userContent = buildString {
                if (currentFacts.isNotEmpty()) {
                    appendLine("Текущие факты (обнови при необходимости):")
                    currentFacts.forEach { (key, value) ->
                        appendLine("- $key: $value")
                    }
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                appendLine("Извлеки факты из следующих сообщений:")
                appendLine()
                for (msg in recentMessages) {
                    val roleLabel = when (msg.role) {
                        MessageRole.USER -> "Пользователь"
                        MessageRole.ASSISTANT -> "Ассистент"
                        MessageRole.SYSTEM -> "Система"
                    }
                    appendLine("[$roleLabel]: ${msg.content}")
                    appendLine()
                }
                appendLine("Верни JSON с обновлёнными фактами.")
            }

            val userMsg = ChatMessage(
                role = MessageRole.USER,
                content = userContent,
            )

            return listOf(systemMsg, userMsg)
        }

        /**
         * Парсит ответ модели (JSON) в Map фактов.
         */
        fun parseFactsResponse(responseContent: String): Map<String, String> {
            val jsonStr = extractJson(responseContent) ?: return emptyMap()
            return try {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                json.decodeFromString<Map<String, String>>(jsonStr)
            } catch (_: Exception) {
                emptyMap()
            }
        }

        private fun extractJson(text: String): String? {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
            val codeBlockRegex = Regex("```(?:json)?\\s*(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL)
            codeBlockRegex.find(trimmed)?.let { return it.groupValues[1] }
            val jsonRegex = Regex("\\{.*}", RegexOption.DOT_MATCHES_ALL)
            jsonRegex.find(trimmed)?.let { return it.value }
            return null
        }

        private fun buildFactsSystemMessage(facts: Map<String, String>): String = buildString {
            appendLine("Ключевые факты из диалога (учитывай их при ответе):")
            appendLine()
            facts.forEach { (key, value) ->
                appendLine("- $key: $value")
            }
        }
    }
}
