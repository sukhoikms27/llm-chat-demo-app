package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.ChatMessage

interface ChatHistoryRepository {
    suspend fun saveMessage(message: ChatMessage)
    suspend fun saveAll(messages: List<ChatMessage>)
    suspend fun loadHistory(): List<ChatMessage>
    suspend fun clearHistory()
}
