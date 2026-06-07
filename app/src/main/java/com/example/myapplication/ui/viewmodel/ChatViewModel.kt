package com.example.myapplication.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.api.RetrofitFactory
import com.example.myapplication.data.models.ChatMessage
import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.ModelInfo
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableList

@Immutable
data class ChatUiState(
    val models: List<ModelInfo> = emptyList(),
    val selectedModel: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val responseText: String = "",
    val isLoading: Boolean = false,
    val isModelsLoading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = true
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            isConfigured = BuildConfig.API_KEY.isNotBlank()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var api = RetrofitFactory.create()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        val current = _uiState.value
        _uiState.value = current.copy(
            isLoading = false,
            isModelsLoading = false,
            error = "Ошибка: ${throwable.message}"
        )
    }

    init {
        if (_uiState.value.isConfigured) {
            loadModels()
        }
    }

    fun loadModels() {
        viewModelScope.launch(exceptionHandler) {
            _uiState.value = _uiState.value.copy(isModelsLoading = true, error = null)
            val models = listOf(
                ModelInfo("GLM-5.1"),
                ModelInfo("GLM-5"),
                ModelInfo("GLM-5-Turbo"),
                ModelInfo("GLM-4.7"),
                ModelInfo("GLM-4.5-air"))
            _uiState.value = _uiState.value.copy(
                models = models,
                selectedModel = models.firstOrNull()?.id ?: "",
                isModelsLoading = false
            )
        }
    }

    fun onSelectedModelChanged(modelId: String) {
        _uiState.value = _uiState.value.copy(selectedModel = modelId)
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val state = _uiState.value
        val userMessage = state.inputText.trim()
        if (userMessage.isEmpty() || state.selectedModel.isEmpty()) return

        val updatedMessages = state.messages + ChatMessage(role = "user", content = userMessage)

        _uiState.value = _uiState.value.copy(
            messages = updatedMessages.toImmutableList(),
            inputText = "",
            isLoading = true,
            responseText = "",
            error = null
        )

        viewModelScope.launch(exceptionHandler) {
            val request = ChatRequest(
                model = state.selectedModel,
                messages = updatedMessages
            )
            val response = api.chatCompletions(request)
            val assistantMessage = response.choices.firstOrNull()?.message

            if (assistantMessage != null) {
                val newMessages = updatedMessages + assistantMessage
                _uiState.value = _uiState.value.copy(
                    messages = newMessages.toImmutableList(),
                    responseText = assistantMessage.content,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Пустой ответ от модели"
                )
            }
        }
    }

    fun clearChat() {
        _uiState.value = _uiState.value.copy(
            messages = emptyList(),
            responseText = ""
        )
    }
}
