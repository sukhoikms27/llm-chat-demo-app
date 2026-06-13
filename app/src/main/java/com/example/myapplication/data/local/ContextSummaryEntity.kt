package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "context_summaries")
data class ContextSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val rootMessageId: Long?,
    val content: String,
    val summarizedCount: Int,
    val tokenEstimate: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
