package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.DialogFacts
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.ContextStrategyType
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.model.StreamEvent
import android.util.Log
import com.example.myapplication.domain.pricing.Pricing
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class LlmAgentImpl(
    private val repository: LlmRepository,
    private val historyRepository: ChatHistoryRepository,
    private val contextManager: ContextManager,
    initialModel: String,
    initialConfig: GenerationConfig = GenerationConfig(),
) : LlmAgent {

    companion object {
        private const val TAG = "LlmAgent"
    }

    private val _history = mutableListOf<ChatMessage>()
    override val conversationHistory: List<ChatMessage> get() = _history.toList()

    private var currentModel: String = initialModel
    private var currentConfig: GenerationConfig = initialConfig
    private var currentChatId: Long = 1L

    private var _totalUsage = CumulativeUsage()
    override val totalUsage: CumulativeUsage get() = _totalUsage

    private var _currentSummary: ContextSummary? = null
    override val currentSummary: ContextSummary? get() = _currentSummary

    private var _currentFacts: DialogFacts? = null
    override val currentFacts: Map<String, String>? get() = _currentFacts?.toMap()

    private var currentStrategy: ContextStrategy = createStrategy(initialConfig)
    override val currentStrategyName: String get() = currentStrategy.displayName

    private fun createStrategy(config: GenerationConfig): ContextStrategy {
        val factsMap = _currentFacts?.toMap() ?: emptyMap()
        return when (config.contextStrategy) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy()
            ContextStrategyType.SUMMARIZATION -> SummaryStrategy(contextManager)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(factsMap)
        }
    }

    override suspend fun initialize(chatId: Long) {
        currentChatId = chatId
        val saved = historyRepository.loadHistory(chatId)
        if (saved.isNotEmpty()) {
            _history.addAll(saved)
            recalculateTotalUsage()
        }
        _currentSummary = historyRepository.loadLatestSummary(chatId)
        _currentFacts = historyRepository.loadLatestFacts(chatId)
        currentStrategy = createStrategy(currentConfig)
    }

    override suspend fun send(
        message: String,
        attachments: List<FileAttachment>,
    ): Result<AgentResponse> = runCatching {
        val userMsg = ChatMessage(
            chatId = currentChatId,
            parentId = _history.lastOrNull()?.id,
            role = MessageRole.USER,
            content = message,
            attachments = attachments,
        )
        val userMsgId = historyRepository.saveMessage(userMsg)
        _history += userMsg.copy(id = userMsgId)

        val contextForRequest = currentStrategy.buildContext(
            _history, _currentSummary, currentConfig
        )
        Log.d(TAG, "send: historySize=${_history.size}, contextSent=${contextForRequest.size} msgs, " +
            "hasSummary=${_currentSummary != null}, strategy=${currentStrategyName}")
        val response = repository.chat(currentModel, contextForRequest, currentConfig)

        val assistantMsg = ChatMessage(
            chatId = currentChatId,
            parentId = userMsgId,
            role = MessageRole.ASSISTANT,
            content = response.content,
            usage = response.usage,
            model = response.model,
            reasoningContent = response.reasoningContent,
        )
        val assistantMsgId = historyRepository.saveMessage(assistantMsg)
        _history += assistantMsg.copy(id = assistantMsgId)

        response.usage?.let { accumulateUsage(it) }
        trackTokenSavings(contextForRequest)

        maybeCompressHistory()
        maybeExtractFacts()

        historyRepository.touchChat(currentChatId)
        response
    }

    override fun sendStream(
        message: String,
        attachments: List<FileAttachment>,
    ): Flow<StreamEvent> = flow {
        val userMsg = ChatMessage(
            chatId = currentChatId,
            parentId = _history.lastOrNull()?.id,
            role = MessageRole.USER,
            content = message,
            attachments = attachments,
        )
        val userMsgId = historyRepository.saveMessage(userMsg)
        _history += userMsg.copy(id = userMsgId)

        val contextForRequest = currentStrategy.buildContext(
            _history, _currentSummary, currentConfig
        )
        Log.d(TAG, "sendStream: historySize=${_history.size}, contextSent=${contextForRequest.size} msgs, " +
            "hasSummary=${_currentSummary != null}, strategy=${currentStrategyName}")

        val fullContent = StringBuilder()
        val fullReasoning = StringBuilder()
        var streamUsage: MessageUsage? = null

        repository.chatStream(currentModel, contextForRequest, currentConfig).collect { event ->
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
            chatId = currentChatId,
            parentId = userMsgId,
            role = MessageRole.ASSISTANT,
            content = fullContent.toString(),
            usage = streamUsage,
            model = currentModel,
            reasoningContent = fullReasoning.toString().ifBlank { null },
        )
        val assistantMsgId = historyRepository.saveMessage(assistantMsg)
        _history += assistantMsg.copy(id = assistantMsgId)

        streamUsage?.let { accumulateUsage(it) }
        trackTokenSavings(contextForRequest)

        maybeCompressHistory()
        maybeExtractFacts()

        historyRepository.touchChat(currentChatId)

        emit(StreamEvent.Done(usage = streamUsage))
    }

    override suspend fun clearHistory() {
        _history.clear()
        _totalUsage = CumulativeUsage()
        _currentSummary = null
        _currentFacts = null
        historyRepository.clearHistory(currentChatId)
        historyRepository.clearSummary(currentChatId)
        historyRepository.clearFacts(currentChatId)
    }

    override fun setModel(modelId: String) {
        currentModel = modelId
    }

    override fun setGenerationConfig(config: GenerationConfig) {
        currentConfig = config
        currentStrategy = createStrategy(config)
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

    private fun trackTokenSavings(contextForRequest: List<ChatMessage>) {
        val saved = contextManager.estimateTokensSaved(_history, contextForRequest)
        if (saved > 0) {
            _totalUsage = _totalUsage.copy(
                tokensSaved = _totalUsage.tokensSaved + saved,
            )
            Log.i(TAG, "Token savings: +$saved tokens (total saved: ${_totalUsage.tokensSaved})")
        }
    }

    private suspend fun maybeCompressHistory() {
        if (!currentStrategy.needsCompression(_history, currentConfig)) {
            Log.d(TAG, "Compression check: not needed " +
                "(historySize=${_history.size}, strategy=${currentStrategyName})")
            return
        }

        val messagesToSummarize = contextManager.getMessagesToSummarize(_history, currentConfig)
        if (messagesToSummarize.isEmpty()) {
            Log.d(TAG, "Compression: no messages to summarize")
            return
        }

        val tokensBefore = _history.sumOf { contextManager.estimateTokens(it.content) }
        Log.i(TAG, "Compression triggered: historySize=${_history.size}, " +
            "summarizing ${messagesToSummarize.size} messages, " +
            "keeping last ${currentConfig.recentMessageCount}, " +
            "estimated tokens before: $tokensBefore")

        val summarizationMessages = contextManager.buildSummarizationRequest(
            messagesToSummarize, _currentSummary
        )
        Log.d(TAG, "Sending summarization request to model '$currentModel' " +
            "(incremental=${_currentSummary != null}, " +
            "prevSummarizedCount=${_currentSummary?.summarizedCount ?: 0})")

        val summaryResponse = repository.chat(currentModel, summarizationMessages, currentConfig)
        val tokensAfter = contextManager.estimateTokens(summaryResponse.content)

        _currentSummary = contextManager.createSummary(
            chatId = currentChatId,
            rootMessageId = _history.firstOrNull()?.parentId,
            summaryContent = summaryResponse.content,
            summarizedCount = messagesToSummarize.size,
            previousSummary = _currentSummary,
        )
        historyRepository.saveSummary(_currentSummary!!)

        Log.i(TAG, "Compression done: summary tokens=$tokensAfter, " +
            "totalSummarized=${_currentSummary!!.summarizedCount} msgs, " +
            "summary length=${summaryResponse.content.length} chars\n" +
            "Summary preview: ${summaryResponse.content}...")
    }

    /**
     * Post-send хук для стратегии Sticky Facts.
     * Извлекает факты из последних сообщений и обновляет _currentFacts.
     */
    private suspend fun maybeExtractFacts() {
        if (currentConfig.contextStrategy != ContextStrategyType.STICKY_FACTS) return
        if (_history.size < 2) return // Нужно хотя бы user + assistant

        val currentFactsMap = _currentFacts?.toMap() ?: emptyMap()
        val recentMessages = _history.takeLast(4) // Анализируем последние 2-3 пары

        val factsRequest = StickyFactsStrategy.buildFactsExtractionRequest(
            recentMessages, currentFactsMap
        )

        Log.d(TAG, "Facts extraction: sending request (currentFacts=${currentFactsMap.size}, " +
            "recentMsgs=${recentMessages.size})")

        try {
            val factsResponse = repository.chat(currentModel, factsRequest, currentConfig)
            val extractedFacts = StickyFactsStrategy.parseFactsResponse(factsResponse.content)

            if (extractedFacts.isNotEmpty()) {
                // Merge: обновленные факты заменяют старые, новые добавляются
                val mergedFacts = currentFactsMap + extractedFacts
                _currentFacts = DialogFacts.fromMap(currentChatId, mergedFacts)
                historyRepository.saveFacts(_currentFacts!!)

                // Пересоздаём стратегию с обновлёнными фактами
                currentStrategy = createStrategy(currentConfig)

                Log.i(TAG, "Facts extracted: ${extractedFacts.size} facts " +
                    "(total now ${mergedFacts.size}): $extractedFacts")
            } else {
                Log.d(TAG, "Facts extraction: no new facts")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Facts extraction failed: ${e.message}")
        }
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
