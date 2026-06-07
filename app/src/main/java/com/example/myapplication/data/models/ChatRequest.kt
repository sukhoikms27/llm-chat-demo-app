package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val top_p: Double = 1.0,
    val max_tokens: Int = 2048,
    val min_tokens: Int = 0,
    val n: Int = 1,
    val stream: Boolean = false,
    val stop: List<String>? = null,
    val stop_token_ids: List<Int>? = null,
    val response_format: ResponseFormat? = null,
    val frequency_penalty: Double = 0.0,
    val presence_penalty: Double = 0.0,
    val repetition_penalty: Double = 1.0,
    val length_penalty: Double = 1.0,
    val seed: Long? = null,
    val logprobs: Boolean = false,
    val top_logprobs: Int = 0,
    val logit_bias: Map<String, Int>? = null,
    val user: String? = null,
    val top_k: Int = -1,
    val min_p: Double = 0.0,
    val best_of: Int = 0,
    val use_beam_search: Boolean = false,
    val early_stopping: Boolean = false,
    val ignore_eos: Boolean = false,
    val skip_special_tokens: Boolean = true,
    val spaces_between_special_tokens: Boolean = true,
    val echo: Boolean = false,
    val add_generation_prompt: Boolean = true,
    val include_stop_str_in_output: Boolean = false,
    val guided_json: String? = null,
    val guided_regex: String? = null,
    val guided_choice: List<String>? = null,
    val guided_grammar: String? = null,
    val guided_decoding_backend: String? = null,
    val guided_whitespace_pattern: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val name: String? = null
)

@Serializable
data class ResponseFormat(
    val type: String = "text"
)
