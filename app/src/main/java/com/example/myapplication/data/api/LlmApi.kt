package com.example.myapplication.data.api

import com.example.myapplication.data.models.ChatRequest
import com.example.myapplication.data.models.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LlmApi {
    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: ChatRequest): ChatResponse
}



//Крестьянка пришла на базар продавать яйца. Первая покупательница купила у нее половину всех яиц и еще пол-яйца. Вторая покупательница приобрела половину оставшихся яиц и еще пол-яйца. Третья купила всего одно яйцо. После этого у крестьянки не осталось ничего. Сколько яиц она принесла на базар?