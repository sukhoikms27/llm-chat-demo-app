package com.example.myapplication.domain.model

/**
 * Ветка диалога.
 *
 * Ветка идентифицируется по листу дерева — [leafMessageId] (последнему сообщению).
 * Полный путь root→leaf восстанавливается по цепочке parentId в [ChatMessage].
 *
 * @param leafMessageId       ID последнего сообщения в ветке
 * @param parentLeafMessageId лист родительской ветки (для checkpoint, откуда создана ветка)
 * @param parentBranchId      ID родительской ветки (для выхода из ветки обратно в родительскую)
 */
data class DialogBranch(
    val id: Long = 0,
    val chatId: Long = 0,
    val leafMessageId: Long,
    val parentLeafMessageId: Long? = null,
    val parentBranchId: Long? = null,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
