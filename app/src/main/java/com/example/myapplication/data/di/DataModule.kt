package com.example.myapplication.data.di

import android.content.Context
import androidx.room.Room
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.api.LlmApi
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.ChatMessageDao
import com.example.myapplication.data.local.MIGRATION_1_2
import com.example.myapplication.data.repository.ChatHistoryRepositoryImpl
import com.example.myapplication.data.repository.LlmRepositoryImpl
import com.example.myapplication.domain.repository.ChatHistoryRepository
import com.example.myapplication.domain.repository.LlmRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private const val BASE_URL = "https://api.z.ai/api/coding/paas/v4/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideJson(): Json = json

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "llm-chat-db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.chatMessageDao()

    @Provides
    @Singleton
    fun provideChatHistoryRepository(dao: ChatMessageDao): ChatHistoryRepository =
        ChatHistoryRepositoryImpl(dao)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(360, TimeUnit.SECONDS)
            .writeTimeout(360, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideLlmApi(client: OkHttpClient): LlmApi {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LlmApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLlmRepository(api: LlmApi, json: Json): LlmRepository =
        LlmRepositoryImpl(api, json)

    private fun createAuthInterceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .build()
        chain.proceed(request)
    }
}
