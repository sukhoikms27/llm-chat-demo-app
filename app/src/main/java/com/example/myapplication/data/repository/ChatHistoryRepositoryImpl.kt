package com.example.myapplication.data.repository

import com.example.myapplication.data.local.ChatDao
import com.example.myapplication.data.local.ChatMessageDao
import com.example.myapplication.data.local.ChatMessageEntity
import com.example.myapplication.data.local.ContextSummaryDao
import com.example.myapplication.data.local.ContextSummaryEntity
import com.example.myapplication.data.local.DialogFactsDao
import com.example.myapplication.data.local.DialogFactsEntity
import com.example.myapplication.data.local.DialogBranchDao
import com.example.myapplication.data.local.DialogBranchEntity
import com.example.myapplication.domain.model.Chat
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.DialogBranch
import com.example.myapplication.domain.model.DialogFacts
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
    private val factsDao: DialogFactsDao,
    private val branchDao: DialogBranchDao,
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
        factsDao.deleteForChat(1L)
        branchDao.deleteAll()
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

    override suspend fun saveFacts(facts: DialogFacts) {
        factsDao.insert(facts.toEntity())
    }

    override suspend fun loadLatestFacts(chatId: Long): DialogFacts? {
        return factsDao.getLatestForChat(chatId)?.toDomain()
    }

    override suspend fun clearFacts(chatId: Long) {
        factsDao.deleteForChat(chatId)
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
        factsDao.deleteForChat(id)
        branchDao.deleteForChat(id)
        chatDao.delete(id)
    }

    override suspend fun saveBranch(branch: DialogBranch): Long {
        return branchDao.insert(branch.toEntity())
    }

    override suspend fun getBranches(chatId: Long): List<DialogBranch> {
        return branchDao.getForChat(chatId).map { it.toDomain() }
    }

    override suspend fun getBranch(id: Long): DialogBranch? {
        return branchDao.getById(id)?.toDomain()
    }

    override suspend fun renameBranch(id: Long, name: String) {
        branchDao.rename(id, name)
    }

    override suspend fun updateBranchLeaf(id: Long, leafMessageId: Long) {
        branchDao.updateLeaf(id, leafMessageId)
    }

    override suspend fun deleteBranch(id: Long) {
        branchDao.delete(id)
    }

    override suspend fun clearBranches(chatId: Long) {
        branchDao.deleteForChat(chatId)
    }

    override suspend fun loadBranchMessages(chatId: Long, leafMessageId: Long): List<ChatMessage> {
        val all = messageDao.getForChat(chatId).map { it.toDomain() }
        val path = mutableListOf<ChatMessage>()
        var current = all.find { it.id == leafMessageId }
        while (current != null) {
            path.add(0, current)
            val parentId = current.parentId ?: break
            current = all.find { it.id == parentId }
        }
        return path
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

    private fun DialogFacts.toEntity() = DialogFactsEntity(
        id = id,
        chatId = chatId,
        factsJson = factsJson,
        updatedAt = updatedAt,
    )

    private fun DialogFactsEntity.toDomain() = DialogFacts(
        id = id,
        chatId = chatId,
        factsJson = factsJson,
        updatedAt = updatedAt,
    )

    private fun DialogBranch.toEntity() = DialogBranchEntity(
        id = id,
        chatId = chatId,
        leafMessageId = leafMessageId,
        parentLeafMessageId = parentLeafMessageId,
        parentBranchId = parentBranchId,
        name = name,
        createdAt = createdAt,
    )

    private fun DialogBranchEntity.toDomain() = DialogBranch(
        id = id,
        chatId = chatId,
        leafMessageId = leafMessageId,
        parentLeafMessageId = parentLeafMessageId,
        parentBranchId = parentBranchId,
        name = name,
        createdAt = createdAt,
    )
}
