package com.example.myapplication.data.api

import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.ChatResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface LlmApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): ChatResponse

    @Streaming
    @POST("chat/completions")
    suspend fun chatCompletionsStream(@Body request: ChatRequest): ResponseBody
}
