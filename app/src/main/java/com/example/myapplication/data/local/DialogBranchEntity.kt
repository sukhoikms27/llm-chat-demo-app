package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dialog_branches",
    indices = [Index("chatId")],
)
data class DialogBranchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val leafMessageId: Long,
    val parentLeafMessageId: Long?,
    val parentBranchId: Long?,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
