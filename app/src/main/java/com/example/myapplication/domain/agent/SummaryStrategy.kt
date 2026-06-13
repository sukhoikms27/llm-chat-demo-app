package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig

/**
 * Стратегия сжатия через summarization.
 * Обёртка над существующим ContextManager.
 * В запрос: summary (system message) + последние N сообщений.
 * Периодически запускает сжатие старых сообщений через LLM.
 */
class SummaryStrategy(
    private val contextManager: ContextManager,
) : ContextStrategy {

    override val displayName: String = "Сжатие (Summary)"
    override val description: String = "Автоматическое суммирование старых сообщений"

    override fun buildContext(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage> =
        contextManager.buildContextForRequest(history, summary, config)

    override fun needsCompression(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): Boolean =
        contextManager.needsCompression(history, config)
}
