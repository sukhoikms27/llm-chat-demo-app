package com.example.myapplication.domain.model

data class GenerationConfig(
    val configName: String? = null,
    val temperature: Double = 1.0,
    val topP: Double = 0.95,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val systemPrompt: String? = null,
    val user: String? = null,
)
