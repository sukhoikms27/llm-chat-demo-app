package com.example.myapplication.data.models

/**
 * Бизнес-сущность: параметры генерации ответа.
 * Отвечает за то, *как* модель генерирует ответ — независимо от содержимого (messages).
 *
 * Используется в ViewModel, может выноситься в настройки UI, сериализоваться в пресеты.
 */
data class GenerationConfig(
    val temperature: Double = 1.0,
    val topP: Double = 0.95,
    val maxTokens: Int = 2048,
    val stop: List<String>? = null,
    val systemPrompt: String? = null,
    val user: String? = null,
)

/**
 * Сборка ChatRequest из бизнес-компонентов: модель + сообщения + конфиг генерации.
 * ChatRequest — плоский транспортный DTO для API, GenerationConfig — структурированная бизнес-модель.
 *
 * Если config содержит systemPrompt — внедряем его как первое сообщение с role="system".
 */
fun buildChatRequest(
    model: String,
    messages: List<ChatMessage>,
    config: GenerationConfig = GenerationConfig()
): ChatRequest {
    val finalMessages = if (config.systemPrompt != null && messages.none { it.role == "system" }) {
        listOf(ChatMessage(role = "system", content = config.systemPrompt)) + messages
    } else {
        messages
    }

    return ChatRequest(
        model = model,
        messages = finalMessages,
        temperature = config.temperature,
        top_p = config.topP,
        max_tokens = config.maxTokens,
        stop = config.stop,
        user_id = config.user,
    )
}

/**
 * Пресеты генерации — готовые конфигурации под типовые сценарии.
 */
object GenerationPresets {

    /** Стандартные параметры — без предконфигурации, все значения по умолчанию */
    val default = GenerationConfig()

    /** Краткий и точный ответ */
    val concise = GenerationConfig(
        temperature = 0.3,
        maxTokens = 256,
        stop = listOf("\n\n"),
        systemPrompt = "Отвечай кратко, в 1-2 предложения.",
    )

    /** Развернутый ответ с деталями */
    val detailed = GenerationConfig(
        temperature = 0.7,
        maxTokens = 2048,
        systemPrompt = "Отвечай подробно, с деталями и примерами.",
    )

    /** Творческий ответ */
    val creative = GenerationConfig(
        temperature = 1.0,
        topP = 0.95,
        maxTokens = 2048,
        systemPrompt = "Отвечай творчески, используй метафоры и нестандартные аналогии.",
    )

    /** Список всех пресетов для UI */
    val all = listOf(default, concise, detailed, creative)
}

/**
 * Человекочитаемое название пресета.
 */
val GenerationConfig.presetLabel: String
    get() = when (this) {
        GenerationPresets.default -> "Стандарт"
        GenerationPresets.concise -> "Кратко"
        GenerationPresets.detailed -> "Подробно"
        GenerationPresets.creative -> "Творчески"
        else -> "Свой"
    }
