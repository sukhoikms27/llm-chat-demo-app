package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig

/**
 * Базовый интерфейс для стратегий управления контекстом.
 * Каждая стратегия определяет, какие сообщения из истории попадут в запрос к LLM.
 */
interface ContextStrategy {

    /** Отображаемое имя для UI */
    val displayName: String

    /** Описание стратегии для UI */
    val description: String

    /**
     * Собирает сообщения для отправки в LLM API.
     * @param history полная история диалога
     * @param summary текущее саммари (если есть)
     * @param config конфигурация генерации
     * @return список сообщений для отправки в API
     */
    fun buildContext(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage>

    /**
     * Нужно ли сжатие истории прямо сейчас.
     * Только для стратегий, использующих summarization.
     */
    fun needsCompression(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): Boolean = false
}
