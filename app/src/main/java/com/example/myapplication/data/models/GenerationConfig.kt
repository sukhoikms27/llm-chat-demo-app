package com.example.myapplication.data.models

/**
 * Бизнес-сущность: параметры генерации ответа.
 * Отвечает за то, *как* модель генерирует ответ — независимо от содержимого (messages).
 *
 * Используется в ViewModel, может выноситься в настройки UI, сериализоваться в пресеты.
 */
data class GenerationConfig(
    val configName: String? = null,
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
    val default = GenerationConfig(
        configName = "Стандарт",
    )

    /** Краткий и точный ответ */
    val concise = GenerationConfig(
        configName = "Пошагово",
    )

    /** Развернутый ответ с деталями */
    val promted = GenerationConfig(
        configName = "Промтинг",
    )

    /** Творческий ответ */
    val experts = GenerationConfig(
        configName = "Группа экспертов",
        systemPrompt = """
                    Ты — группа узкоспециализированных экспертов, собранная для всестороннего решения сложной задачи. 

                    В состав группы входят следующие эксперты:
                    1. Дискретный математик (Логика и абстракция)
                    2. Системный архитектор / Аналитик данных (Структура и данные)
                    3. Специалист по алгоритмам и структурам данных (Алгоритмы и оптимизация)
                    4. Специалист по обеспечению качества / QA Automation (Критический анализ и тесты)
                    
                    Правила генерации ответа:
                    1. Каждый эксперт должен дать независимый, аргументированный ответ строго из своей профессиональной роли.
                    2. Стиль речи, терминология и логика мышления каждого эксперта должны соответствовать его профессии.
                    3. Если область знаний эксперта напрямую не связана с задачей, он ВСЕ РАВНО обязан предложить полезный мета-взгляд, найти неочевидную точку соприкосновения или четко обозначить ограничения своей области для этой проблемы.
                    4. Ответ должен быть строго структурирован. Оформи его четырьмя разделами. Заголовком каждого раздела должно быть имя роли эксперта (выделенное жирным шрифтом или как заголовок Markdown).
                """.trimIndent(),
    )

    /** Список всех пресетов для UI */
    val all = listOf(default, concise, promted, experts)
}

/**
 * Человекочитаемое название пресета.
 */
val GenerationConfig.presetLabel: String
    get() = when (this) {
        GenerationPresets.default -> "Стандарт"
        GenerationPresets.concise -> "Пошагово"
        GenerationPresets.promted -> "Промтинг"
        GenerationPresets.experts -> "Группа экспертов"
        else -> "Свой"
    }
