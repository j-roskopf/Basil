package com.joetr.basil.domain.share

import com.joetr.basil.domain.model.Recipe

public object RecipeShareTextFormatter {
    public fun format(recipe: Recipe, link: String? = null): String = buildString {
        appendLine(recipe.title)
        recipe.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appendLine()
            appendLine(it)
        }

        val details = buildList {
            recipe.servings?.let { add("$it servings") }
            recipe.prepMinutes?.let { add("prep ${it} min") }
            recipe.cookMinutes?.let { add("cook ${it} min") }
        }
        if (details.isNotEmpty()) {
            appendLine()
            appendLine(details.joinToString(" · "))
        }

        if (recipe.ingredients.isNotEmpty()) {
            appendLine()
            appendLine("Ingredients")
            recipe.ingredients.forEach { appendLine("• $it") }
        }

        if (recipe.steps.isNotEmpty()) {
            appendLine()
            appendLine("Steps")
            recipe.steps.forEachIndexed { index, step ->
                val duration = step.minutes?.let { " ($it min)" }.orEmpty()
                appendLine("${index + 1}. ${step.text}$duration")
            }
        }

        recipe.sourceUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appendLine()
            appendLine("Source: $it")
        }
        link?.trim()?.takeIf { it.isNotEmpty() }?.let {
            appendLine()
            appendLine("Shared from Basil: $it")
        }
    }.trim()
}
