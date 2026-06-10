package com.example.myapplication.data.models

/**
 * Цены за 1 млн токенов (USD).
 * Источник: https://docs.z.ai/guides/overview/pricing
 */
data class ModelPricing(
    val inputPerMillion: Double,
    val cachedPerMillion: Double,
    val outputPerMillion: Double,
)

object Pricing {
    private val prices = mapOf(
        "GLM-5.1" to ModelPricing(1.40, 0.26, 4.40),
        "GLM-5" to ModelPricing(1.00, 0.20, 3.20),
        "GLM-5-Turbo" to ModelPricing(1.20, 0.24, 4.00),
        "GLM-4.7" to ModelPricing(0.60, 0.11, 2.20),
        "GLM-4.5-air" to ModelPricing(0.20, 0.03, 1.10),
    )

    fun forModel(model: String): ModelPricing? = prices[model]

    /**
     * Рассчитывает стоимость запроса в USD.
     */
    fun calculateCost(model: String, usage: MessageUsage): Double? {
        val pricing = prices[model] ?: return null
        val inputTokens = (usage.prompt_tokens - usage.cached_tokens).coerceAtLeast(0)
        val inputCost = inputTokens * pricing.inputPerMillion / 1_000_000
        val cachedCost = usage.cached_tokens * pricing.cachedPerMillion / 1_000_000
        val outputCost = usage.completion_tokens * pricing.outputPerMillion / 1_000_000
        return inputCost + cachedCost + outputCost
    }
}
