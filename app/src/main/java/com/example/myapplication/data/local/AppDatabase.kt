package com.example.myapplication.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter

@Database(
    entities = [
        ChatMessageEntity::class,
        ChatEntity::class,
        ContextSummaryEntity::class,
        SettingsEntity::class,
        DialogFactsEntity::class,
        DialogBranchEntity::class,
    ],
    version = 7,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatDao(): ChatDao
    abstract fun contextSummaryDao(): ContextSummaryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun dialogFactsDao(): DialogFactsDao
    abstract fun dialogBranchDao(): DialogBranchDao
}
