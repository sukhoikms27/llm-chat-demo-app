package com.example.myapplication.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FileAttachment(
    val uri: String,
    val fileName: String,
    val mimeType: String,
    val localPath: String? = null,
    val base64Content: String? = null,
)
