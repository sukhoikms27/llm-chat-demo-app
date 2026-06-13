package com.example.myapplication.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerationConfig(
    val configName: String? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val systemPrompt: String? = null,
    val user: String? = null,
    val useStreaming: Boolean = false,
    val enableThinking: Boolean = false,
    // Context management
    val contextCompressionEnabled: Boolean = true,
    val recentMessageCount: Int = 6,
    val summarizeInterval: Int = 4,
    val contextStrategy: ContextStrategyType = ContextStrategyType.SUMMARIZATION,
)
