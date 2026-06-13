package com.example.myapplication.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ContentPartText(
    val type: String = "text",
    val text: String,
)

@Serializable
data class ContentPartImageUrl(
    val type: String = "image_url",
    val image_url: ImageUrlData,
)

@Serializable
data class ImageUrlData(
    val url: String,
)
