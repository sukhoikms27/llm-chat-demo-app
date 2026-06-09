package com.example.myapplication.domain.pricing

import com.example.myapplication.domain.model.MessageUsage

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

    fun calculateCost(model: String, usage: MessageUsage): Double? {
        val pricing = prices[model] ?: return null
        val inputTokens = (usage.promptTokens - usage.cachedTokens).coerceAtLeast(0)
        val inputCost = inputTokens * pricing.inputPerMillion / 1_000_000
        val cachedCost = usage.cachedTokens * pricing.cachedPerMillion / 1_000_000
        val outputCost = usage.completionTokens * pricing.outputPerMillion / 1_000_000
        return inputCost + cachedCost + outputCost
    }
}
