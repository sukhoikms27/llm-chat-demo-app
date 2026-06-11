package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import kotlinx.coroutines.flow.Flow

interface LlmAgent {
    val conversationHistory: List<ChatMessage>
    suspend fun send(message: String): Result<AgentResponse>
    fun sendStream(message: String): Flow<String>
    fun clearHistory()
    fun setModel(modelId: String)
    fun setGenerationConfig(config: GenerationConfig)
}
