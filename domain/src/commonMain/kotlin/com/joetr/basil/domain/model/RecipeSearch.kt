package com.joetr.basil.domain.model

public fun Recipe.matchesSearch(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        description?.contains(needle, ignoreCase = true) == true ||
        notes?.contains(needle, ignoreCase = true) == true ||
        sourceUrl?.contains(needle, ignoreCase = true) == true ||
        ingredients.any { it.contains(needle, ignoreCase = true) } ||
        steps.any { it.text.contains(needle, ignoreCase = true) } ||
        tags.any { it.contains(needle, ignoreCase = true) }
}
