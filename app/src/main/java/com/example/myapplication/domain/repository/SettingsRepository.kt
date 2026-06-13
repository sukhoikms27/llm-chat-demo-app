package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.GenerationConfig

interface SettingsRepository {
    suspend fun loadConfig(): GenerationConfig?
    suspend fun saveConfig(config: GenerationConfig)
}
