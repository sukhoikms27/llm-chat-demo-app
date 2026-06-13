package com.example.myapplication.data.repository

import com.example.myapplication.data.local.ChatMessageDao
import com.example.myapplication.data.local.ChatMessageEntity
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.repository.ChatHistoryRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepositoryImpl @Inject constructor(
    private val dao: ChatMessageDao,
) : ChatHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveMessage(message: ChatMessage) {
        dao.insert(message.toEntity())
    }

    override suspend fun saveAll(messages: List<ChatMessage>) {
        dao.insertAll(messages.map { it.toEntity() })
    }

    override suspend fun loadHistory(): List<ChatMessage> {
        return dao.getAll().map { it.toDomain() }
    }

    override suspend fun clearHistory() {
        dao.deleteAll()
    }

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        role = role.name,
        content = content,
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        cachedTokens = usage?.cachedTokens ?: 0,
        model = model,
        attachmentsJson = if (attachments.isNotEmpty()) json.encodeToString(attachments) else null,
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        role = try {
            MessageRole.valueOf(role)
        } catch (_: IllegalArgumentException) {
            MessageRole.USER
        },
        content = content,
        usage = MessageUsage(
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            cachedTokens = cachedTokens,
        ),
        model = model,
        attachments = try {
            attachmentsJson?.let { json.decodeFromString<List<FileAttachment>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        },
    )
}
