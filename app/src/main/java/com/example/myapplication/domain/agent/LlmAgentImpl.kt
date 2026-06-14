package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.DialogBranch
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

    private var _branches = mutableListOf<DialogBranch>()
    override val branches: List<DialogBranch> get() = _branches.toList()

    private var _activeBranchId: Long? = null
    override val activeBranchId: Long? get() = _activeBranchId

    private var currentStrategy: ContextStrategy = createStrategy(initialConfig)
    override val currentStrategyName: String get() = currentStrategy.displayName

    private fun createStrategy(config: GenerationConfig): ContextStrategy {
        val factsMap = _currentFacts?.toMap() ?: emptyMap()
        return when (config.contextStrategy) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy()
            ContextStrategyType.SUMMARIZATION -> SummaryStrategy(contextManager)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(factsMap)
            ContextStrategyType.BRANCHING -> BranchingStrategy()
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

        // Branches
        _branches = historyRepository.getBranches(chatId).toMutableList()
        if (_branches.isEmpty() && _history.isNotEmpty()) {
            val leaf = _history.last().id
            val id = historyRepository.saveBranch(
                DialogBranch(
                    chatId = chatId,
                    leafMessageId = leaf,
                    parentLeafMessageId = null,
                    name = "main",
                )
            )
            _branches.add(
                DialogBranch(
                    id = id,
                    chatId = chatId,
                    leafMessageId = leaf,
                    parentLeafMessageId = null,
                    name = "main",
                )
            )
        }
        _activeBranchId = _branches.firstOrNull()?.id

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
        updateActiveBranchLeaf(assistantMsgId)

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
        updateActiveBranchLeaf(assistantMsgId)

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
        _branches.clear()
        _activeBranchId = null
        historyRepository.clearHistory(currentChatId)
        historyRepository.clearSummary(currentChatId)
        historyRepository.clearFacts(currentChatId)
        historyRepository.clearBranches(currentChatId)
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

    // --- Branching ---

    /**
     * Обновляет leaf-сообщение активной ветки после отправки/получения.
     */
    private suspend fun updateActiveBranchLeaf(leafMessageId: Long) {
        val branchId = _activeBranchId ?: return
        historyRepository.updateBranchLeaf(branchId, leafMessageId)
        _branches = _branches.map {
            if (it.id == branchId) it.copy(leafMessageId = leafMessageId) else it
        }.toMutableList()
    }

    override suspend fun createBranch(checkpointMessageId: Long): Long {
        val checkpointIndex = _history.indexOfFirst { it.id == checkpointMessageId }
        require(checkpointIndex >= 0) { "Checkpoint message not found in history" }

        // Обрезаем историю до checkpoint
        while (_history.size > checkpointIndex + 1) {
            _history.removeAt(_history.size - 1)
        }

        val parentLeaf = _branches.find { it.id == _activeBranchId }?.leafMessageId
        val parentBranchId = _activeBranchId

        val tempName = ""
        val id = historyRepository.saveBranch(
            DialogBranch(
                chatId = currentChatId,
                leafMessageId = checkpointMessageId,
                parentLeafMessageId = parentLeaf,
                parentBranchId = parentBranchId,
                name = tempName,
            )
        )
        _branches.add(
            DialogBranch(
                id = id,
                chatId = currentChatId,
                leafMessageId = checkpointMessageId,
                parentLeafMessageId = parentLeaf,
                parentBranchId = parentBranchId,
                name = tempName,
            )
        )
        _activeBranchId = id

        Log.i(TAG, "Branch created: id=$id, checkpoint=$checkpointMessageId, parentLeaf=$parentLeaf, parentBranch=$parentBranchId")

        // Генерируем имя через LLM (не блокируем создание ветки)
        generateBranchName(id, _history)

        return id
    }

    override suspend fun switchBranch(branchId: Long) {
        val branch = _branches.find { it.id == branchId }
            ?: throw IllegalArgumentException("Branch $branchId not found")

        _history.clear()
        _history.addAll(
            historyRepository.loadBranchMessages(currentChatId, branch.leafMessageId)
        )
        _activeBranchId = branchId

        Log.i(TAG, "Switched to branch $branchId (${branch.name}), " +
            "historySize=${_history.size}")
    }

    override suspend fun exitBranch(): Boolean {
        val current = _branches.find { it.id == _activeBranchId } ?: return false
        val parentId = current.parentBranchId ?: return false
        return try {
            switchBranch(parentId)
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Parent branch $parentId not found: ${e.message}")
            false
        }
    }

    override suspend fun renameBranch(branchId: Long, name: String) {
        historyRepository.renameBranch(branchId, name)
        _branches = _branches.map {
            if (it.id == branchId) it.copy(name = name) else it
        }.toMutableList()
    }

    override suspend fun deleteBranch(branchId: Long) {
        historyRepository.deleteBranch(branchId)
        _branches.removeAll { it.id == branchId }
        if (_activeBranchId == branchId) {
            _activeBranchId = _branches.firstOrNull()?.id
            // Reload history for new active branch
            _activeBranchId?.let { switchBranch(it) }
        }
    }

    /**
     * Генерирует имя ветки через LLM-запрос.
     * Берёт несколько сообщений вокруг checkpoint и просит модель придумать название.
     */
    private suspend fun generateBranchName(
        branchId: Long,
        branchHistory: List<ChatMessage>,
    ) {
        val recent = branchHistory.takeLast(4)
        if (recent.isEmpty()) return

        val systemMsg = ChatMessage(
            role = MessageRole.SYSTEM,
            content = "Ты — система генерации названий для веток диалога. " +
                "Проанализируй сообщения и придумай короткое название (3-5 слов) " +
                "для ветки диалога. Верни ТОЛЬКО название, без кавычек и пояснений.",
        )
        val userContent = buildString {
            appendLine("Придумай название для ветки диалога:")
            appendLine()
            for (msg in recent) {
                val role = when (msg.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                }
                appendLine("[$role]: ${msg.content.take(200)}")
                appendLine()
            }
        }
        val userMsg = ChatMessage(role = MessageRole.USER, content = userContent)

        try {
            val response = repository.chat(currentModel, listOf(systemMsg, userMsg), currentConfig)
            val name = response.content.trim().take(50)
            if (name.isNotEmpty()) {
                renameBranch(branchId, name)
                Log.i(TAG, "Branch $branchId named: '$name'")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Branch name generation failed: ${e.message}")
            renameBranch(branchId, "Ветка $branchId")
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
