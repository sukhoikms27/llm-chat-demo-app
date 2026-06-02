package com.example.myapplication.data.api

import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.ChatResponse
import com.example.myapplication.data.models.ModelsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LlmApi {
    @GET("v1/models")
    suspend fun getModels(): ModelsResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): ChatResponse
}
