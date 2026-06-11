package com.example.myapplication.domain.model

data class AgentResponse(
    val content: String,
    val model: String,
    val usage: MessageUsage?,
)
