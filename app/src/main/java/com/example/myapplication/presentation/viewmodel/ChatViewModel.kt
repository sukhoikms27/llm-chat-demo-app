package com.example.myapplication.presentation.viewmodel

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.domain.agent.LlmAgent
import com.example.myapplication.domain.agent.LlmAgentFactory
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.GenerationPresets
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.ModelInfo
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.data.storage.FileStorage
import com.example.myapplication.domain.repository.LlmRepository
import com.example.myapplication.domain.repository.SettingsRepository
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
    val pendingAttachments: List<FileAttachment> = emptyList(),
    // Token stats
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    // Context management
    val tokensSaved: Int = 0,
    val hasActiveSummary: Boolean = false,
    val currentFacts: Map<String, String> = emptyMap(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentFactory: LlmAgentFactory,
    private val repository: LlmRepository,
    private val application: Application,
    private val fileStorage: FileStorage,
    private val settingsRepository: SettingsRepository,
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
            // Load saved settings first
            val savedConfig = settingsRepository.loadConfig()
            if (savedConfig != null) {
                agent.setGenerationConfig(savedConfig)
                _uiState.update { it.copy(generationConfig = savedConfig) }
            }

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
        viewModelScope.launch {
            settingsRepository.saveConfig(config)
        }
    }

    fun addAttachment(attachment: FileAttachment) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments + attachment) }
    }

    fun removeAttachment(index: Int) {
        _uiState.update { it.copy(pendingAttachments = it.pendingAttachments.toMutableList().apply { removeAt(index) }) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val userMessage = state.inputText.trim()
        if ((userMessage.isEmpty() && state.pendingAttachments.isEmpty()) || state.selectedModel.isEmpty()) return

        val attachments = state.pendingAttachments.map { attachment ->
            if (attachment.base64Content != null) return@map attachment

            when {
                attachment.mimeType.startsWith("image/") -> fileStorage.loadBase64(attachment)
                fileStorage.isTextType(attachment.mimeType) -> {
                    val text = fileStorage.readTextContent(attachment)
                    if (text != null) attachment.copy(base64Content = text) else attachment
                }
                else -> fileStorage.loadBase64(attachment)
            }
        }

        // Add user message to UI immediately
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = userMessage,
            attachments = attachments,
        )

        _uiState.update { it.copy(
            inputText = "",
            isLoading = true,
            streamingText = "",
            error = null,
            messages = it.messages + userMsg,
            pendingAttachments = emptyList(),
        ) }

        if (state.generationConfig.useStreaming) {
            sendStreaming(userMessage, attachments)
        } else {
            sendNonStreaming(userMessage, attachments)
        }
    }

    private fun sendStreaming(message: String, attachments: List<FileAttachment>) {
        viewModelScope.launch {
            try {
                agent.sendStream(message, attachments).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> {
                            _uiState.update { it.copy(streamingText = it.streamingText + event.content) }
                        }
                        is StreamEvent.ReasoningChunk -> {
                            // Reasoning handled internally
                        }
                        is StreamEvent.Done -> {
                            // Streaming finished
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

    private fun sendNonStreaming(message: String, attachments: List<FileAttachment>) {
        viewModelScope.launch {
            try {
                val result = agent.send(message, attachments)
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
            tokensSaved = usage.tokensSaved,
            hasActiveSummary = agent.currentSummary != null,
            currentFacts = agent.currentFacts ?: emptyMap(),
        ) }
    }
}
