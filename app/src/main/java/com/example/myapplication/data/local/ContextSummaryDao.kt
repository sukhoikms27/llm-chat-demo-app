package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContextSummaryDao {
    @Query("SELECT * FROM context_summaries WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: Long): ContextSummaryEntity?

    @Insert
    suspend fun insert(summary: ContextSummaryEntity)

    @Query("DELETE FROM context_summaries WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: Long)

    @Query("DELETE FROM context_summaries")
    suspend fun deleteAll()
}
