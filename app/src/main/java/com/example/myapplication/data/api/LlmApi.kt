package com.example.myapplication.data.api

import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LlmApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): ChatResponse
}
