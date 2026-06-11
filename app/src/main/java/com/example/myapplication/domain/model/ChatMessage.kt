package com.example.myapplication.domain.model

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val usage: MessageUsage? = null,
    val model: String? = null,
    val reasoningContent: String? = null,
)

enum class MessageRole { SYSTEM, USER, ASSISTANT }

data class MessageUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
)
