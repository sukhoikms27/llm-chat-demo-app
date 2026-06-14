package com.example.myapplication.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentsJson TEXT DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create chats table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chats (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)

        // 2. Create a default chat for existing messages
        db.execSQL("INSERT INTO chats (id, title, createdAt, updatedAt) VALUES (1, 'Чат 1', 0, 0)")

        // 3. Add columns to chat_messages
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN chatId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN parentId INTEGER DEFAULT NULL")

        // 4. Create context_summaries table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS context_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                rootMessageId INTEGER,
                content TEXT NOT NULL,
                summarizedCount INTEGER NOT NULL,
                tokenEstimate INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """)

        // 5. Create app_settings table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS app_settings (
                id INTEGER NOT NULL PRIMARY KEY,
                configJson TEXT NOT NULL
            )
        """)
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE chat_messages ADD COLUMN reasoningContent TEXT DEFAULT NULL")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dialog_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                factsJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dialog_branches (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                leafMessageId INTEGER NOT NULL,
                parentLeafMessageId INTEGER,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dialog_branches_chatId ON dialog_branches(chatId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dialog_branches ADD COLUMN parentBranchId INTEGER DEFAULT NULL")
    }
}
