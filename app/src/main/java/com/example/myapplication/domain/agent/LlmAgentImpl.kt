package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LlmAgentImpl(
    private val repository: LlmRepository,
    initialModel: String,
    initialConfig: GenerationConfig = GenerationConfig(),
) : LlmAgent {

    private val _history = mutableListOf<ChatMessage>()
    override val conversationHistory: List<ChatMessage> get() = _history.toList()

    private var currentModel: String = initialModel
    private var currentConfig: GenerationConfig = initialConfig

    override suspend fun send(message: String): Result<AgentResponse> = runCatching {
        val userMsg = ChatMessage(role = MessageRole.USER, content = message)
        _history += userMsg

        val response = repository.chat(currentModel, _history, currentConfig)

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = response.content,
            usage = response.usage,
            model = response.model,
        )
        _history += assistantMsg
        response
    }

    override fun sendStream(message: String): Flow<String> = flow {
        val userMsg = ChatMessage(role = MessageRole.USER, content = message)
        _history += userMsg

        val fullContent = StringBuilder()
        repository.chatStream(currentModel, _history, currentConfig).collect { chunk ->
            fullContent.append(chunk)
            emit(chunk)
        }

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = fullContent.toString(),
            model = currentModel,
        )
        _history += assistantMsg
    }

    override fun clearHistory() {
        _history.clear()
    }

    override fun setModel(modelId: String) {
        currentModel = modelId
    }

    override fun setGenerationConfig(config: GenerationConfig) {
        currentConfig = config
    }
}
