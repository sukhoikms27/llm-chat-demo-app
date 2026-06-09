package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface LlmRepository {
    suspend fun chat(model: String, messages: List<ChatMessage>, config: GenerationConfig): AgentResponse
    fun chatStream(model: String, messages: List<ChatMessage>, config: GenerationConfig): Flow<String>
    fun getAvailableModels(): List<ModelInfo>
}
