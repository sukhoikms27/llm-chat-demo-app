package com.example.myapplication.data.repository

import com.example.myapplication.data.local.ChatDao
import com.example.myapplication.data.local.ChatMessageDao
import com.example.myapplication.data.local.ChatMessageEntity
import com.example.myapplication.data.local.ContextSummaryDao
import com.example.myapplication.data.local.ContextSummaryEntity
import com.example.myapplication.domain.model.Chat
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.MessageUsage
import com.example.myapplication.domain.repository.ChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepositoryImpl @Inject constructor(
    private val messageDao: ChatMessageDao,
    private val chatDao: ChatDao,
    private val summaryDao: ContextSummaryDao,
) : ChatHistoryRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveMessage(message: ChatMessage): Long {
        return messageDao.insert(message.toEntity())
    }

    override suspend fun saveAll(messages: List<ChatMessage>) {
        messageDao.insertAll(messages.map { it.toEntity() })
    }

    override suspend fun loadHistory(chatId: Long): List<ChatMessage> {
        return messageDao.getForChat(chatId).map { it.toDomain() }
    }

    override suspend fun clearHistory(chatId: Long) {
        messageDao.deleteForChat(chatId)
    }

    override suspend fun clearAll() {
        messageDao.deleteAll()
        summaryDao.deleteAll()
    }

    override suspend fun saveSummary(summary: ContextSummary) {
        summaryDao.insert(summary.toEntity())
    }

    override suspend fun loadLatestSummary(chatId: Long): ContextSummary? {
        return summaryDao.getLatestForChat(chatId)?.toDomain()
    }

    override suspend fun clearSummary(chatId: Long) {
        summaryDao.deleteForChat(chatId)
    }

    override fun observeChats(): Flow<List<Chat>> {
        return chatDao.observeAll().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getChats(): List<Chat> {
        return chatDao.getAll().map { it.toDomain() }
    }

    override suspend fun createChat(title: String): Long {
        val now = System.currentTimeMillis()
        return chatDao.insert(
            com.example.myapplication.data.local.ChatEntity(
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun deleteChat(id: Long) {
        messageDao.deleteForChat(id)
        summaryDao.deleteForChat(id)
        chatDao.delete(id)
    }

    override suspend fun touchChat(id: Long) {
        chatDao.touch(id, System.currentTimeMillis())
    }

    // --- Mappers ---

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id,
        chatId = chatId,
        parentId = parentId,
        role = role.name,
        content = content,
        promptTokens = usage?.promptTokens ?: 0,
        completionTokens = usage?.completionTokens ?: 0,
        cachedTokens = usage?.cachedTokens ?: 0,
        model = model,
        reasoningContent = reasoningContent,
        createdAt = System.currentTimeMillis(),
        attachmentsJson = if (attachments.isNotEmpty()) json.encodeToString(attachments) else null,
    )

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        chatId = chatId,
        parentId = parentId,
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
        reasoningContent = reasoningContent,
        attachments = try {
            attachmentsJson?.let { json.decodeFromString<List<FileAttachment>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        },
    )

    private fun ContextSummary.toEntity() = ContextSummaryEntity(
        id = id,
        chatId = chatId,
        rootMessageId = rootMessageId,
        content = content,
        summarizedCount = summarizedCount,
        tokenEstimate = tokenEstimate,
        createdAt = createdAt,
    )

    private fun ContextSummaryEntity.toDomain() = ContextSummary(
        id = id,
        chatId = chatId,
        rootMessageId = rootMessageId,
        content = content,
        summarizedCount = summarizedCount,
        tokenEstimate = tokenEstimate,
        createdAt = createdAt,
    )

    private fun com.example.myapplication.data.local.ChatEntity.toDomain() = Chat(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
