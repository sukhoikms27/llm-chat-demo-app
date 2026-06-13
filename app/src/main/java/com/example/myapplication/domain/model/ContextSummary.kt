package com.example.myapplication.domain.model

data class ContextSummary(
    val id: Long = 0,
    val chatId: Long,
    val rootMessageId: Long?,
    val content: String,
    val summarizedCount: Int = 0,
    val tokenEstimate: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
