package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository

interface LlmAgentFactory {
    fun create(model: String, config: GenerationConfig = GenerationConfig()): LlmAgent
}

class LlmAgentFactoryImpl(
    private val repository: LlmRepository,
    private val historyRepository: ChatHistoryRepository,
    private val contextManager: ContextManager,
) : LlmAgentFactory {
    override fun create(model: String, config: GenerationConfig): LlmAgent {
        return LlmAgentImpl(repository, historyRepository, contextManager, model, config)
    }
}
