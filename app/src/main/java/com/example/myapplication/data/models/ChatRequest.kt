package com.example.myapplication.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val thinking: ThinkingConfig? = null,
    val request_id: String? = null,
    val user_id: String? = null,
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: JsonElement,
    val name: String? = null,
    val usage: MessageUsage? = null,
    val model: String? = null,
    val reasoning_content: String? = null,
)

@Serializable
data class MessageUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val cached_tokens: Int = 0,
)

@Serializable
data class ResponseFormat(
    val type: String = "text"
)

@Serializable
data class ThinkingConfig(
    val type: String, // "enabled" or "disabled"
)
