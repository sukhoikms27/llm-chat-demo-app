package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val do_sample: Boolean = true,
    val stream: Boolean = false,
    val temperature: Double? = null,
    val top_p: Double? = null,
    val max_tokens: Int? = null,
    val stop: List<String>? = null,
    val response_format: ResponseFormat? = null,
    val request_id: String? = null,
    val user_id: String? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val name: String? = null
)

@Serializable
data class ResponseFormat(
    val type: String = "text"
)
