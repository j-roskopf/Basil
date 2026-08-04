package com.joetr.basil.domain.recipe

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Scales ingredient quantities when adjusting recipe servings.
 */
public object IngredientScaler {
    private val ingredientPattern = Regex(
        pattern = """^(\d+(?:[./]\d+)?(?:\s*[-–]\s*\d+(?:[./]\d+)?)?\s*(?:g|kg|ml|l|oz|lb|tsp|tbsp|cups?|cans?|cloves?|pcs?|tbsp\.?|tsp\.?)?)\s+(.+)$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val quantityPattern = Regex(
        pattern = """^(\d+(?:[./]\d+)?(?:\s*[-–]\s*\d+(?:[./]\d+)?)?)\s*(g|kg|ml|l|oz|lb|tsp|tbsp|cups?|cans?|cloves?|pcs?|tbsp\.?|tsp\.?)?$""",
        option = RegexOption.IGNORE_CASE,
    )
    private val rangePattern = Regex("""^(\d+(?:[./]\d+)?)\s*[-–]\s*(\d+(?:[./]\d+)?)$""")

    public fun scaleIngredients(
        ingredients: List<String>,
        originalServings: Int,
        targetServings: Int,
    ): List<String> {
        if (originalServings <= 0 || targetServings <= 0 || originalServings == targetServings) {
            return ingredients
        }
        val factor = targetServings.toDouble() / originalServings.toDouble()
        return ingredients.map { scaleIngredient(it, factor) }
    }

    public fun scaleIngredient(raw: String, factor: Double): String {
        if (factor == 1.0) return raw
        val trimmed = raw.trim()
        val match = ingredientPattern.find(trimmed) ?: return raw
        val quantityPart = match.groupValues[1]
        val rest = match.groupValues[2]
        val scaledQuantity = scaleQuantityPart(quantityPart, factor)
        return "$scaledQuantity $rest"
    }

    private fun scaleQuantityPart(quantityPart: String, factor: Double): String {
        val quantityMatch = quantityPattern.find(quantityPart.trim()) ?: return quantityPart
        val numbersPart = quantityMatch.groupValues[1]
        val unit = quantityMatch.groupValues[2].takeIf { it.isNotBlank() }
        val scaledNumbers = scaleNumbersPart(numbersPart, factor)
        return buildString {
            append(scaledNumbers)
            if (unit != null) {
                append(' ')
                append(unit)
            }
        }
    }

    private fun scaleNumbersPart(numbersPart: String, factor: Double): String {
        val rangeMatch = rangePattern.find(numbersPart.trim())
        if (rangeMatch != null) {
            val low = parseNumber(rangeMatch.groupValues[1])?.times(factor)
            val high = parseNumber(rangeMatch.groupValues[2])?.times(factor)
            if (low == null || high == null) return numbersPart
            return "${formatNumber(low)}–${formatNumber(high)}"
        }
        val value = parseNumber(numbersPart.trim())
        if (value == null) return numbersPart
        return formatNumber(value * factor)
    }

    private fun parseNumber(raw: String): Double? {
        if (raw.contains('/')) {
            val parts = raw.split('/')
            if (parts.size == 2) {
                val numerator = parts[0].toDoubleOrNull()
                val denominator = parts[1].toDoubleOrNull()
                if (numerator != null && denominator != null && denominator != 0.0) {
                    return numerator / denominator
                }
            }
            return null
        }
        return raw.toDoubleOrNull()
    }

    private fun formatNumber(value: Double): String {
        val rounded = round(value * 1000) / 1000.0
        if (rounded <= 0.0) return "0"

        val whole = floor(rounded)
        val fraction = rounded - whole

        val fractionText = when {
            fraction < 0.01 -> null
            abs(fraction - 0.5) < 0.02 -> "1/2"
            abs(fraction - 0.25) < 0.02 -> "1/4"
            abs(fraction - 0.75) < 0.02 -> "3/4"
            abs(fraction - 0.333) < 0.03 -> "1/3"
            abs(fraction - 0.667) < 0.03 -> "2/3"
            else -> null
        }

        return when {
            fractionText != null && whole >= 1.0 -> "${whole.toInt()} $fractionText"
            fractionText != null -> fractionText
            abs(rounded - whole) < 0.01 -> whole.toInt().toString()
            else -> rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
