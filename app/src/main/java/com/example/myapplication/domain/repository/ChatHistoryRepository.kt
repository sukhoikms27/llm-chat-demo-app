package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.Chat
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
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

    // Chats
    fun observeChats(): Flow<List<Chat>>
    suspend fun getChats(): List<Chat>
    suspend fun createChat(title: String): Long
    suspend fun deleteChat(id: Long)
    suspend fun touchChat(id: Long)
}
