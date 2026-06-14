package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig

/**
 * Стратегия ветвления диалога.
 *
 * Каждая ветка — это поддерево в дереве parentId.
 * В запрос к API отправляются последние N сообщений активной ветки.
 * Управление ветками (create/switch) осуществляется в [com.example.myapplication.domain.agent.LlmAgentImpl],
 * стратегия отвечает только за формирование контекста.
 */
class BranchingStrategy : ContextStrategy {

    override val displayName: String = "Ветвление"
    override val description: String = "Ветви диалога: checkpoint, независимые ветки, переключение"

    override fun buildContext(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage> {
        return history.takeLast(config.recentMessageCount)
    }

    override fun needsCompression(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): Boolean = false
}
