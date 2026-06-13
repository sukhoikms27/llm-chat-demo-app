package com.example.myapplication.domain.agent

import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.ContextSummary
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.MessageRole
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextManager @Inject constructor() {

    companion object {
        private const val CHARS_PER_TOKEN = 4
        private const val MAX_SUMMARY_WORDS = 1000
    }

    private val summarizationSystemPrompt = """
        Ты — эксперт по сжатию контекста диалога. Твоя задача — создать максимально плотное саммари,
        которое позволит продолжить разговор без потери контекста.

        Сохрани обязательно:
        1. ФАКТЫ: конкретные данные, цифры, имена, ссылки, названия
        2. РЕШЕНИЯ: что было решено, выбрано, утверждено
        3. ОТКРЫТЫЕ ВОПРОСЫ: что ещё не решено или обсуждается
        4. КОНТЕКСТ ПОЛЬЗОВАТЕЛЯ: предпочтения, ограничения, требования
        5. ХОД РАССУЖДЕНИЙ: ключевые аргументы и логика решений

        Формат саммари:
        - Используй структурированный список
        - Группируй по темам
        - Не более $MAX_SUMMARY_WORDS слов
        - Пиши на русском языке
        - Не добавляй ничего от себя — только информацию из диалога
    """.trimIndent()

    /**
     * Нужно ли сжатие прямо сейчас.
     * Сжатие запускается, когда history.size >= recentMessageCount + summarizeInterval.
     */
    fun needsCompression(history: List<ChatMessage>, config: GenerationConfig): Boolean {
        if (!config.contextCompressionEnabled) return false
        return history.size >= config.recentMessageCount + config.summarizeInterval
    }

    /**
     * Какие сообщения попадают в summarization (всё кроме последних recentMessageCount).
     */
    fun getMessagesToSummarize(
        history: List<ChatMessage>,
        config: GenerationConfig,
    ): List<ChatMessage> {
        val recentCount = config.recentMessageCount
        return if (history.size > recentCount) {
            history.dropLast(recentCount)
        } else {
            emptyList()
        }
    }

    /**
     * Формирует сообщения для summarization-запроса к LLM.
     * Включает предыдущий summary (если есть) для инкрементального сжатия.
     */
    fun buildSummarizationRequest(
        messages: List<ChatMessage>,
        previousSummary: ContextSummary?,
    ): List<ChatMessage> {
        val systemMsg = ChatMessage(
            role = MessageRole.SYSTEM,
            content = summarizationSystemPrompt,
        )

        val userContent = buildString {
            if (previousSummary != null && previousSummary.content.isNotBlank()) {
                appendLine("Вот предыдущее саммари (учти его при создании нового):")
                appendLine(previousSummary.content)
                appendLine()
                appendLine("---")
                appendLine()
            }
            appendLine("Сожми следующие сообщения диалога, сохранив всю важную информацию:")
            appendLine()
            for (msg in messages) {
                val roleLabel = when (msg.role) {
                    MessageRole.USER -> "Пользователь"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                }
                appendLine("[$roleLabel]: ${msg.content}")
                appendLine()
            }
        }

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = userContent,
        )

        return listOf(systemMsg, userMsg)
    }

    /**
     * Сборка финального контекста для запроса к API.
     * Если есть summary: [system(summary)] + recent N сообщений.
     * Если нет summary: полная история.
     */
    fun buildContextForRequest(
        history: List<ChatMessage>,
        summary: ContextSummary?,
        config: GenerationConfig,
    ): List<ChatMessage> {
        if (summary == null || summary.content.isBlank()) {
            return history
        }

        val summaryMsg = ChatMessage(
            role = MessageRole.SYSTEM,
            content = buildString {
                appendLine("Краткое содержание предыдущей части диалога:")
                appendLine()
                appendLine(summary.content)
            },
        )

        val recentMessages = history.takeLast(config.recentMessageCount)
        return listOf(summaryMsg) + recentMessages
    }

    /**
     * Оценка количества токенов (~chars / 4).
     */
    fun estimateTokens(text: String): Int = text.length / CHARS_PER_TOKEN

    /**
     * Оценка сэкономленных токенов (разница между полной историей и сжатой).
     */
    fun estimateTokensSaved(
        fullHistory: List<ChatMessage>,
        compressedContext: List<ChatMessage>,
    ): Int {
        val fullTokens = fullHistory.sumOf { estimateTokens(it.content) }
        val compressedTokens = compressedContext.sumOf { estimateTokens(it.content) }
        return (fullTokens - compressedTokens).coerceAtLeast(0)
    }

    /**
     * Формирует ContextSummary из ответа LLM на summarization-запрос.
     */
    fun createSummary(
        chatId: Long,
        rootMessageId: Long?,
        summaryContent: String,
        summarizedCount: Int,
        previousSummary: ContextSummary?,
    ): ContextSummary = ContextSummary(
        chatId = chatId,
        rootMessageId = rootMessageId,
        content = summaryContent,
        summarizedCount = (previousSummary?.summarizedCount ?: 0) + summarizedCount,
        tokenEstimate = estimateTokens(summaryContent),
    )
}
