package com.example.myapplication.data.mapper

import com.example.myapplication.data.models.ChatMessage
import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.MessageUsage
import com.example.myapplication.domain.model.AgentResponse
import com.example.myapplication.domain.model.ChatMessage as DomainChatMessage
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage as DomainMessageUsage

// Domain → DTO

fun DomainChatMessage.toDto(): ChatMessage = ChatMessage(
    role = when (role) {
        MessageRole.SYSTEM -> "system"
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
    },
    content = content,
    usage = usage?.toDto(),
    model = model,
)

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

fun ChatMessage.toDomain(): DomainChatMessage = DomainChatMessage(
    role = when (role) {
        "system" -> MessageRole.SYSTEM
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        else -> MessageRole.USER
    },
    content = content,
    usage = usage?.toDomain(),
    model = model,
)

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
    val finalMessages = if (configDto.systemPrompt != null && messages.none { it.role == "system" }) {
        listOf(ChatMessage(role = "system", content = configDto.systemPrompt)) + messages
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
