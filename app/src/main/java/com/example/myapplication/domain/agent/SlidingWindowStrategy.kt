package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig

/**
 * Стратегия скользящего окна.
 * В запрос к API отправляются только последние N сообщений.
 * Старые сообщения отбрасываются без сжатия.
 */
class SlidingWindowStrategy : ContextStrategy {

    override val displayName: String = "Sliding Window"
    override val description: String = "Только последние N сообщений, остальное отбрасывается"

    override fun buildContext(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage> {
        // summary игнорируется — окно само по себе ограничивает контекст
        return history.takeLast(config.recentMessageCount)
    }

    override fun needsCompression(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): Boolean = false
}
