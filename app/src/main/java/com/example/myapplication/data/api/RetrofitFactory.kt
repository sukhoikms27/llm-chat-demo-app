package com.example.myapplication.data.api

import com.example.myapplication.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitFactory {

    private const val BASE_URL = "https://api.z.ai/api/coding/paas/v4/"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun createAuthInterceptor(apiKey: String): Interceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        chain.proceed(request)
    }

    private fun createOkHttpClient(apiKey: String): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(createAuthInterceptor(apiKey))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun create(baseUrl: String = BASE_URL, apiKey: String = BuildConfig.API_KEY): LlmApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient(apiKey))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LlmApi::class.java)
    }
}
