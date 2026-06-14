package com.example.myapplication.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ключевые факты диалога, извлекаемые стратегией Sticky Facts.
 * Хранятся как JSON-сериализованный Map<String, String>.
 */
data class DialogFacts(
    val id: Long = 0,
    val chatId: Long = 0,
    val factsJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toMap(): Map<String, String> = try {
        json.decodeFromString(factsJson)
    } catch (_: Exception) {
        emptyMap()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromMap(chatId: Long, facts: Map<String, String>, id: Long = 0): DialogFacts =
            DialogFacts(
                id = id,
                chatId = chatId,
                factsJson = json.encodeToString(facts),
            )
    }
}
