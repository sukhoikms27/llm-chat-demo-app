package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DialogFactsDao {
    @Query("SELECT * FROM dialog_facts WHERE chatId = :chatId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: Long): DialogFactsEntity?

    @Insert
    suspend fun insert(entity: DialogFactsEntity)

    @Query("DELETE FROM dialog_facts WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: Long)
}
