package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.pricing.Pricing
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LlmAgentImpl(
    private val repository: LlmRepository,
    private val historyRepository: ChatHistoryRepository,
    initialModel: String,
    initialConfig: GenerationConfig = GenerationConfig(),
) : LlmAgent {

    private val _history = mutableListOf<ChatMessage>()
    override val conversationHistory: List<ChatMessage> get() = _history.toList()

    private var currentModel: String = initialModel
    private var currentConfig: GenerationConfig = initialConfig

    private var _totalUsage = CumulativeUsage()
    override val totalUsage: CumulativeUsage get() = _totalUsage

    override suspend fun initialize() {
        val saved = historyRepository.loadHistory()
        if (saved.isNotEmpty()) {
            _history.addAll(saved)
            // Recalculate cumulative usage from saved history
            recalculateTotalUsage()
        }
    }

    override suspend fun send(message: String): Result<AgentResponse> = runCatching {
        val userMsg = ChatMessage(role = MessageRole.USER, content = message)
        _history += userMsg
        historyRepository.saveMessage(userMsg)

        val response = repository.chat(currentModel, _history, currentConfig)

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = response.content,
            usage = response.usage,
            model = response.model,
            reasoningContent = response.reasoningContent,
        )
        _history += assistantMsg
        historyRepository.saveMessage(assistantMsg)

        // Update cumulative usage
        response.usage?.let { accumulateUsage(it) }

        response
    }

    override fun sendStream(message: String): Flow<StreamEvent> = flow {
        val userMsg = ChatMessage(role = MessageRole.USER, content = message)
        _history += userMsg
        historyRepository.saveMessage(userMsg)

        val fullContent = StringBuilder()
        val fullReasoning = StringBuilder()
        var streamUsage: MessageUsage? = null

        repository.chatStream(currentModel, _history, currentConfig).collect { event ->
            when (event) {
                is StreamEvent.Chunk -> {
                    fullContent.append(event.content)
                    emit(event)
                }
                is StreamEvent.ReasoningChunk -> {
                    fullReasoning.append(event.content)
                    emit(event)
                }
                is StreamEvent.Done -> {
                    streamUsage = event.usage
                }
            }
        }

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = fullContent.toString(),
            usage = streamUsage,
            model = currentModel,
            reasoningContent = fullReasoning.toString().ifBlank { null },
        )
        _history += assistantMsg
        historyRepository.saveMessage(assistantMsg)

        // Update cumulative usage
        streamUsage?.let { accumulateUsage(it) }

        // Emit Done so the caller knows streaming finished with usage info
        emit(StreamEvent.Done(usage = streamUsage))
    }

    override suspend fun clearHistory() {
        _history.clear()
        _totalUsage = CumulativeUsage()
        historyRepository.clearHistory()
    }

    override fun setModel(modelId: String) {
        currentModel = modelId
    }

    override fun setGenerationConfig(config: GenerationConfig) {
        currentConfig = config
    }

    private fun accumulateUsage(usage: MessageUsage) {
        val cost = Pricing.calculateCost(currentModel, usage) ?: 0.0
        _totalUsage = _totalUsage.copy(
            totalPromptTokens = _totalUsage.totalPromptTokens + usage.promptTokens,
            totalCompletionTokens = _totalUsage.totalCompletionTokens + usage.completionTokens,
            totalTokens = _totalUsage.totalTokens + usage.promptTokens + usage.completionTokens,
            estimatedCost = _totalUsage.estimatedCost + cost,
            messageCount = _totalUsage.messageCount + 1,
        )
    }

    private fun recalculateTotalUsage() {
        var promptTokens = 0
        var completionTokens = 0
        var cost = 0.0
        var count = 0
        for (msg in _history) {
            msg.usage?.let { usage ->
                promptTokens += usage.promptTokens
                completionTokens += usage.completionTokens
                msg.model?.let { model ->
                    cost += Pricing.calculateCost(model, usage) ?: 0.0
                }
                count++
            }
        }
        _totalUsage = CumulativeUsage(
            totalPromptTokens = promptTokens,
            totalCompletionTokens = completionTokens,
            totalTokens = promptTokens + completionTokens,
            estimatedCost = cost,
            messageCount = count,
        )
    }
}
