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
import com.example.myapplication.domain.model.MessageRole
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

        _uiState.update { it.copy(
            inputText = "",
            isLoading = true,
            streamingText = "",
            error = null,
        ) }

        viewModelScope.launch {
            try {
                agent.sendStream(userMessage).collect { chunk ->
                    _uiState.update { it.copy(streamingText = it.streamingText + chunk) }
                }
                _uiState.update { it.copy(
                    messages = agent.conversationHistory,
                    streamingText = "",
                    isLoading = false,
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    streamingText = "",
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
        }
    }
}
