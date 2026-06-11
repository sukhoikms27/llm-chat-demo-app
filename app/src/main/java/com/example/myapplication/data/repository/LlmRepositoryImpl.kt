package com.example.myapplication.data.repository

import com.example.myapplication.data.api.LlmApi
import com.example.myapplication.data.mapper.buildChatRequest
import com.example.myapplication.data.mapper.toDomain
import com.example.myapplication.data.mapper.toDto
import com.example.myapplication.data.models.StreamChunk
import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.ModelInfo
import com.example.myapplication.domain.repository.LlmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepositoryImpl @Inject constructor(
    private val api: LlmApi,
    private val json: Json,
) : LlmRepository {

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        config: GenerationConfig,
    ): AgentResponse = withContext(Dispatchers.IO) {
        val dtoMessages = messages.map { it.toDto() }
        val configDto = config.toDto()
        val request = buildChatRequest(model, dtoMessages, configDto, stream = false)
        val response = api.chatCompletions(request)
        response.toDomain()
    }

    override fun chatStream(
        model: String,
        messages: List<ChatMessage>,
        config: GenerationConfig,
    ): Flow<String> = callbackFlow {
        val dtoMessages = messages.map { it.toDto() }
        val configDto = config.toDto()
        val request = buildChatRequest(model, dtoMessages, configDto, stream = true)

        withContext(Dispatchers.IO) {
            val response = api.chatCompletionsStream(request)
            val source = response.source()

            try {
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        val chunk = json.decodeFromString<StreamChunk>(data)
                        val content = chunk.choices.firstOrNull()?.delta?.content
                        if (content != null) {
                            trySend(content)
                        }
                    }
                }
            } finally {
                source.close()
                response.close()
            }
        }
        close()
    }

    override fun getAvailableModels(): List<ModelInfo> = listOf(
        ModelInfo("GLM-5.1"),
        ModelInfo("GLM-5"),
        ModelInfo("GLM-5-Turbo"),
        ModelInfo("GLM-4.7"),
        ModelInfo("GLM-4.5-air"),
    )
}
