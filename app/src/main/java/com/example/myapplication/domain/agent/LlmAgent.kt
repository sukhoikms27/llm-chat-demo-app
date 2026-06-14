package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.DialogBranch
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

data class CumulativeUsage(
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0,
    val estimatedCost: Double = 0.0,
    val messageCount: Int = 0,
    val tokensSaved: Int = 0,
)

interface LlmAgent {
    val conversationHistory: List<ChatMessage>
    val totalUsage: CumulativeUsage
    val currentSummary: ContextSummary?
    val currentStrategyName: String
    val currentFacts: Map<String, String>?
    val branches: List<DialogBranch>
    val activeBranchId: Long?

    suspend fun initialize(chatId: Long = 1L)
    suspend fun send(message: String, attachments: List<FileAttachment> = emptyList()): Result<com.example.myapplication.domain.model.AgentResponse>
    fun sendStream(message: String, attachments: List<FileAttachment> = emptyList()): Flow<StreamEvent>
    suspend fun clearHistory()
    fun setModel(modelId: String)
    fun setGenerationConfig(config: GenerationConfig)

    /** Branching */
    suspend fun createBranch(checkpointMessageId: Long): Long
    suspend fun switchBranch(branchId: Long)
    suspend fun exitBranch(): Boolean
    suspend fun renameBranch(branchId: Long, name: String)
    suspend fun deleteBranch(branchId: Long)
}
