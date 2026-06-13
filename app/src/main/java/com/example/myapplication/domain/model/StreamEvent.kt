package com.example.myapplication.domain.model

sealed interface StreamEvent {
    data class Chunk(val content: String) : StreamEvent
    data class ReasoningChunk(val content: String) : StreamEvent
    data class Done(val usage: MessageUsage? = null) : StreamEvent
}
