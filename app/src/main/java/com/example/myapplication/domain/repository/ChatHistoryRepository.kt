package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.Chat
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.DialogBranch
import com.example.myapplication.domain.model.DialogFacts
import kotlinx.coroutines.flow.Flow

interface ChatHistoryRepository {
    suspend fun saveMessage(message: ChatMessage): Long
    suspend fun saveAll(messages: List<ChatMessage>)
    suspend fun loadHistory(chatId: Long): List<ChatMessage>
    suspend fun clearHistory(chatId: Long)
    suspend fun clearAll()

    // Summary
    suspend fun saveSummary(summary: ContextSummary)
    suspend fun loadLatestSummary(chatId: Long): ContextSummary?
    suspend fun clearSummary(chatId: Long)

    // Facts
    suspend fun saveFacts(facts: DialogFacts)
    suspend fun loadLatestFacts(chatId: Long): DialogFacts?
    suspend fun clearFacts(chatId: Long)

    // Chats
    fun observeChats(): Flow<List<Chat>>
    suspend fun getChats(): List<Chat>
    suspend fun createChat(title: String): Long
    suspend fun deleteChat(id: Long)
    suspend fun touchChat(id: Long)

    // Branches
    suspend fun saveBranch(branch: DialogBranch): Long
    suspend fun getBranches(chatId: Long): List<DialogBranch>
    suspend fun getBranch(id: Long): DialogBranch?
    suspend fun renameBranch(id: Long, name: String)
    suspend fun updateBranchLeaf(id: Long, leafMessageId: Long)
    suspend fun deleteBranch(id: Long)
    suspend fun clearBranches(chatId: Long)
    suspend fun loadBranchMessages(chatId: Long, leafMessageId: Long): List<ChatMessage>
}
