package com.joetr.basil.domain.parser

import com.joetr.basil.domain.model.ExtractedRecipe
import com.joetr.basil.domain.model.ExtractionConfidence
import com.joetr.basil.domain.model.RecipeStep

/**
 * Heuristic parser for on-device OCR text from cookbook pages.
 */
public object OcrRecipeParser {
  public fun parse(rawText: String): ExtractedRecipe {
    val lines = rawText.lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    if (lines.isEmpty()) {
      return ExtractedRecipe(confidence = ExtractionConfidence.NONE, rawText = rawText)
    }

    val titleIndex = guessTitleIndex(lines)
    val title = lines[titleIndex]
    val body = lines.drop(titleIndex + 1)

    val ingredientHeader = body.indexOfFirst { it.matches(Regex("(?i)ingredients?")) }
    val stepHeader = body.indexOfFirst {
      it.matches(Regex("(?i)(instructions?|directions?|method|steps?)"))
    }

    val ingredients = mutableListOf<String>()
    val steps = mutableListOf<RecipeStep>()

    when {
      ingredientHeader >= 0 && stepHeader > ingredientHeader -> {
        body.subList(ingredientHeader + 1, stepHeader)
          .filter { looksLikeIngredient(it) }
          .forEach { ingredients += it }
        body.drop(stepHeader + 1)
          .filter { it.length > 3 }
          .forEach { line ->
            steps += RecipeStep(text = line, minutes = StepDurationParser.parse(line))
          }
      }
      ingredientHeader >= 0 -> {
        body.drop(ingredientHeader + 1)
          .takeWhile { looksLikeIngredient(it) }
          .forEach { ingredients += it }
        body.drop(ingredientHeader + 1 + ingredients.size)
          .filter { it.length > 3 }
          .forEach { line ->
            steps += RecipeStep(text = line, minutes = StepDurationParser.parse(line))
          }
      }
      else -> {
        val splitIndex = body.indexOfFirst { !looksLikeIngredient(it) }.takeIf { it > 0 } ?: body.size / 2
        body.take(splitIndex).filter { looksLikeIngredient(it) }.forEach { ingredients += it }
        body.drop(splitIndex).filter { it.length > 3 }.forEach { line ->
          steps += RecipeStep(text = line, minutes = StepDurationParser.parse(line))
        }
      }
    }

    val confidence = when {
      ingredients.isNotEmpty() && steps.isNotEmpty() -> ExtractionConfidence.PARTIAL
      ingredients.isNotEmpty() || steps.isNotEmpty() -> ExtractionConfidence.PARTIAL
      else -> ExtractionConfidence.NONE
    }

    return ExtractedRecipe(
      confidence = confidence,
      title = title,
      ingredients = ingredients,
      steps = steps,
      rawText = rawText,
    )
  }

  private fun looksLikeIngredient(line: String): Boolean {
    if (line.length > 80) return false
    return line.matches(Regex("""^[\d¼½¾⅓⅔⅛/.-].*""")) ||
      line.matches(Regex("(?i).*(cup|tsp|tbsp|oz|g|kg|ml|lb|clove|pinch).*"))
  }

  private fun guessTitleIndex(lines: List<String>): Int {
    val skip = Regex("(?i)^(ingredients?|instructions?|directions?|method|steps?|serves?|yield|makes?|prep|cook|total|page\\s*\\d+|\\d+\\s*$)")
    lines.forEachIndexed { index, line ->
      if (line.length in 3..80 && !looksLikeIngredient(line) && !skip.matches(line)) {
        return index
      }
    }
    return lines.indexOfFirst { !skip.matches(it) }.takeIf { it >= 0 } ?: 0
  }
}
