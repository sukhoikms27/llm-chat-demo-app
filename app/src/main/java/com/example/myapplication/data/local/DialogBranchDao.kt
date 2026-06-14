package com.example.myapplication.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DialogBranchDao {
    @Query("SELECT * FROM dialog_branches WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getForChat(chatId: Long): List<DialogBranchEntity>

    @Query("SELECT * FROM dialog_branches WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeForChat(chatId: Long): Flow<List<DialogBranchEntity>>

    @Query("SELECT * FROM dialog_branches WHERE id = :id")
    suspend fun getById(id: Long): DialogBranchEntity?

    @Insert
    suspend fun insert(branch: DialogBranchEntity): Long

    @Query("UPDATE dialog_branches SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE dialog_branches SET leafMessageId = :leafId WHERE id = :id")
    suspend fun updateLeaf(id: Long, leafId: Long)

    @Query("DELETE FROM dialog_branches WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM dialog_branches WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: Long)

    @Query("DELETE FROM dialog_branches")
    suspend fun deleteAll()
}
