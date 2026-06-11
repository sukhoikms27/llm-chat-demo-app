package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

data class CumulativeUsage(
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    val messageCount: Int = 0,
)

interface LlmAgent {
    val conversationHistory: List<ChatMessage>
    val totalUsage: CumulativeUsage
    suspend fun initialize()
    suspend fun send(message: String): Result<AgentResponse>
    fun sendStream(message: String): Flow<StreamEvent>
    suspend fun clearHistory()
    fun setModel(modelId: String)
    fun setGenerationConfig(config: GenerationConfig)
}
