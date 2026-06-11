package com.example.myapplication.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.domain.agent.LlmAgent
import com.example.myapplication.domain.agent.LlmAgentFactory
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.GenerationPresets
import com.example.myapplication.domain.model.ModelInfo
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.repository.LlmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ChatUiState(
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val streamingText: String = "",
    val isLoading: Boolean = false,
    val isModelsLoading: Boolean = false,
    val isHistoryLoaded: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true,
    val generationConfig: GenerationConfig = GenerationPresets.default,
    // Token stats
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentFactory: LlmAgentFactory,
    private val repository: LlmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            isConfigured = BuildConfig.API_KEY.isNotBlank()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val agent: LlmAgent = agentFactory.create(
        model = "GLM-5.1",
        config = GenerationPresets.default,
    )

    init {
        if (_uiState.value.isConfigured) {
            loadModels()
        }
        viewModelScope.launch {
            agent.initialize()
            updateTokenStats()
            _uiState.update { it.copy(
                messages = agent.conversationHistory,
                isHistoryLoaded = true,
            ) }
        }
    }

    fun loadModels() {
        val models = repository.getAvailableModels()
        _uiState.update { it.copy(
            models = models,
            selectedModel = models.firstOrNull()?.id ?: "",
            isModelsLoading = false,
        ) }
    }

    fun onSelectedModelChanged(modelId: String) {
        agent.setModel(modelId)
        _uiState.update { it.copy(selectedModel = modelId) }
    }

    fun onInputTextChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onGenerationConfigChanged(config: GenerationConfig) {
        agent.setGenerationConfig(config)
        _uiState.update { it.copy(generationConfig = config) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val userMessage = state.inputText.trim()
        if (userMessage.isEmpty() || state.selectedModel.isEmpty()) return

        // Add user message to UI immediately
        val userMsg = ChatMessage(
            role = com.example.myapplication.domain.model.MessageRole.USER,
            content = userMessage,
        )

        _uiState.update { it.copy(
            inputText = "",
            isLoading = true,
            streamingText = "",
            error = null,
            messages = it.messages + userMsg,
        ) }

        if (state.generationConfig.useStreaming) {
            sendStreaming(userMessage)
        } else {
            sendNonStreaming(userMessage)
        }
    }

    private fun sendStreaming(message: String) {
        viewModelScope.launch {
            try {
                agent.sendStream(message).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            _uiState.update { it.copy(streamingText = it.streamingText + event.content) }
                        }
                        is StreamEvent.ReasoningChunk -> {
                            // Reasoning handled internally, not shown in UI
                        }
                        is StreamEvent.Done -> {
                            // Streaming finished — usage is available
                        }
                    }
                }
                _uiState.update { it.copy(
                    messages = agent.conversationHistory,
                    streamingText = "",
                    isLoading = false,
                ) }
                updateTokenStats()
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    streamingText = "",
                    error = "Ошибка: ${e.message}",
                ) }
            }
        }
    }

    private fun sendNonStreaming(message: String) {
        viewModelScope.launch {
            try {
                val result = agent.send(message)
                if (result.isFailure) {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Ошибка: ${result.exceptionOrNull()?.message}",
                    ) }
                    return@launch
                }
                _uiState.update { it.copy(
                    messages = agent.conversationHistory,
                    isLoading = false,
                ) }
                updateTokenStats()
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Ошибка: ${e.message}",
                ) }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            agent.clearHistory()
            _uiState.update { it.copy(
                messages = emptyList(),
                streamingText = "",
            ) }
            updateTokenStats()
        }
    }

    private fun updateTokenStats() {
        val usage = agent.totalUsage
        _uiState.update { it.copy(
            totalPromptTokens = usage.totalPromptTokens,
            totalCompletionTokens = usage.totalCompletionTokens,
            totalTokens = usage.totalTokens,
            estimatedCost = usage.estimatedCost,
        ) }
    }
}
