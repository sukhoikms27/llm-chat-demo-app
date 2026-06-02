package com.example.myapplication.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelsResponse(
    val data: List<ModelInfo>,
    @SerialName("object") val obj: String
)

@Serializable
data class ModelInfo(
    val id: String,
    @SerialName("object") val obj: String,
    val created: Long,
    @SerialName("owned_by") val ownedBy: String
)
