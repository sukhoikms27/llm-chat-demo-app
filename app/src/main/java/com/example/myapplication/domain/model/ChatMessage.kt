package com.example.myapplication.domain.model

data class ChatMessage(
    val id: Long = 0,
    val chatId: Long = 0,
    val parentId: Long? = null,
    val role: MessageRole,
    val content: String,
    val usage: MessageUsage? = null,
    val model: String? = null,
    val reasoningContent: String? = null,
    val attachments: List<FileAttachment> = emptyList(),
)

enum class MessageRole { SYSTEM, USER, ASSISTANT }

data class MessageUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
)
