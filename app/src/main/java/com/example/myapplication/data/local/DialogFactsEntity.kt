package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dialog_facts")
data class DialogFactsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val factsJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
