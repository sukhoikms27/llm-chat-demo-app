package com.example.myapplication.data.repository

import com.example.myapplication.data.local.SettingsDao
import com.example.myapplication.data.local.SettingsEntity
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.repository.SettingsRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
    private val json: Json,
) : SettingsRepository {

    override suspend fun loadConfig(): GenerationConfig? {
        val entity = settingsDao.get() ?: return null
        return try {
            json.decodeFromString<GenerationConfig>(entity.configJson)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun saveConfig(config: GenerationConfig) {
        settingsDao.upsert(
            SettingsEntity(
                id = 0,
                configJson = json.encodeToString(config),
            )
        )
    }
}
