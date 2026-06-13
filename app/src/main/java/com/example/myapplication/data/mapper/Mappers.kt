package com.example.myapplication.data.mapper

import com.example.myapplication.data.models.ChatMessage
import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.MessageUsage
import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage as DomainChatMessage
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage as DomainMessageUsage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Domain → DTO

fun DomainChatMessage.toDto(): ChatMessage {
    val contentElement = if (attachments.isNotEmpty()) {
        buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", content)
            })
            for (att in attachments) {
                when {
                    att.mimeType.startsWith("image/") && att.base64Content != null -> {
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                put("url", "data:${att.mimeType};base64,${att.base64Content}")
                            })
                        })
                    }
                    att.base64Content != null -> {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "[File: ${att.fileName}]\n${att.base64Content}")
                        })
                    }
                }
            }
        }
    } else {
        JsonPrimitive(content)
    }

    return ChatMessage(
        role = when (role) {
            MessageRole.SYSTEM -> "system"
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
        },
        content = contentElement,
        usage = usage?.toDto(),
        model = model,
        reasoning_content = reasoningContent,
    )
}

fun GenerationConfig.toDto(): GenerationConfigDto = GenerationConfigDto(
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    stop = stop,
    systemPrompt = systemPrompt,
    user = user,
)

data class GenerationConfigDto(
    val temperature: Double = 1.0,
    val topP: Double = 0.95,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val systemPrompt: String? = null,
    val user: String? = null,
)

// DTO → Domain

fun ChatMessage.toDomain(): DomainChatMessage {
    val textContent = when (val c = content) {
        is JsonPrimitive -> c.content
        is JsonArray -> {
            // Extract text from first text part
            c.firstNotNullOfOrNull { element ->
                if (element is JsonObject && element["type"]?.toString()?.removeSurrounding("\"") == "text") {
                    element["text"]?.toString()?.removeSurrounding("\"")
                } else null
            } ?: ""
        }
        else -> content.toString()
    }

    return DomainChatMessage(
        role = when (role) {
            "system" -> MessageRole.SYSTEM
            "user" -> MessageRole.USER
            "assistant" -> MessageRole.ASSISTANT
            else -> MessageRole.USER
        },
        content = textContent,
        usage = usage?.toDomain(),
        model = model,
        reasoningContent = reasoning_content,
    )
}

fun MessageUsage.toDomain(): DomainMessageUsage = DomainMessageUsage(
    promptTokens = prompt_tokens,
    completionTokens = completion_tokens,
    cachedTokens = cached_tokens,
)

fun com.example.myapplication.data.models.ChatResponse.toDomain(): AgentResponse {
    val choice = choices.firstOrNull()
    return AgentResponse(
        content = choice?.message?.content.orEmpty(),
        model = model,
        usage = usage?.toDomain(),
        reasoningContent = choice?.message?.reasoning_content,
    )
}

fun com.example.myapplication.data.models.Usage.toDomain(): DomainMessageUsage =
    DomainMessageUsage(
        promptTokens = prompt_tokens,
        completionTokens = completion_tokens,
        cachedTokens = prompt_tokens_details?.cached_tokens ?: 0,
    )

private fun DomainMessageUsage.toDto(): MessageUsage = MessageUsage(
    prompt_tokens = promptTokens,
    completion_tokens = completionTokens,
    cached_tokens = cachedTokens,
)

fun buildChatRequest(
    model: String,
    messages: List<ChatMessage>,
    configDto: GenerationConfigDto,
    stream: Boolean = false,
): ChatRequest {
    val systemMsg = configDto.systemPrompt?.let { prompt ->
        ChatMessage(role = "system", content = JsonPrimitive(prompt))
    }

    val finalMessages = if (systemMsg != null && messages.none { it.role == "system" }) {
        listOf(systemMsg) + messages
    } else {
        messages
    }

    return ChatRequest(
        model = model,
        messages = finalMessages,
        temperature = configDto.temperature,
        top_p = configDto.topP,
        max_tokens = configDto.maxTokens,
        stop = configDto.stop,
        stream = stream,
        user_id = configDto.user,
    )
}
