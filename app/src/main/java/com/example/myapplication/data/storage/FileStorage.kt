package com.example.myapplication.data.storage

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.myapplication.domain.model.FileAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val imagesDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "llm-chat").also { it.mkdirs() }
    private val documentsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS), "llm-chat").also { it.mkdirs() }

    fun saveFile(uri: Uri, fileName: String, mimeType: String): FileAttachment {
        val targetDir = if (mimeType.startsWith("image/")) imagesDir else documentsDir
        val file = File(targetDir, fileName)
        var counter = 0
        var targetFile = file
        while (targetFile.exists()) {
            counter++
            val nameWithoutExt = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            targetFile = File(targetDir, if (ext.isNotEmpty()) "${nameWithoutExt}_$counter.$ext" else "${nameWithoutExt}_$counter")
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        return FileAttachment(
            uri = uri.toString(),
            fileName = targetFile.name,
            mimeType = mimeType,
            localPath = targetFile.absolutePath,
        )
    }

    fun loadBase64(attachment: FileAttachment): FileAttachment {
        if (attachment.base64Content != null) return attachment

        val uri = Uri.parse(attachment.uri)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: attachment.localPath?.let { File(it).readBytes() }
            ?: return attachment

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return attachment.copy(base64Content = base64)
    }

    fun readTextContent(attachment: FileAttachment): String? {
        return try {
            val uri = Uri.parse(attachment.uri)
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: attachment.localPath?.let { File(it).readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun isImageType(mimeType: String): Boolean = mimeType.startsWith("image/")

    fun isTextType(mimeType: String): Boolean =
        mimeType.startsWith("text/") ||
            mimeType == "application/json" ||
            mimeType == "application/xml" ||
            mimeType == "application/javascript"
}
