package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.GenerationConfig

interface LlmAgentFactory {
    fun create(model: String, config: GenerationConfig = GenerationConfig()): LlmAgent
}
