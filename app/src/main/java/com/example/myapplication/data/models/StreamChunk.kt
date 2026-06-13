package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null,
    val finish_reason: String? = null,
)

@Serializable
data class StreamDelta(
    val content: String? = null,
    val role: String? = null,
)
