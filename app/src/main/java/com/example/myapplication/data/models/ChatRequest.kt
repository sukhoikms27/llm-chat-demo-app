package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2048,
    val stream: Boolean = false
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val name: String? = null
)
