package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String,
    val request_id: String? = null,
    val choices: List<Choice>,
    val model: String,
    val created: Long,
    val usage: Usage? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class Choice(
    val index: Int,
    val message: ResponseMessage,
    val finish_reason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null
)
