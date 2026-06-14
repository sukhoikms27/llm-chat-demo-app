package com.example.myapplication.domain.model

import kotlinx.serialization.Serializable

/**
 * Тип стратегии управления контекстом.
 * Сохраняется в GenerationConfig, сериализуется в JSON.
 */
@Serializable
enum class ContextStrategyType(val displayName: String) {
    SUMMARIZATION("Сжатие (Summary)"),
    SLIDING_WINDOW("Sliding Window"),
    STICKY_FACTS("Sticky Facts"),
    BRANCHING("Ветвление"),
}
