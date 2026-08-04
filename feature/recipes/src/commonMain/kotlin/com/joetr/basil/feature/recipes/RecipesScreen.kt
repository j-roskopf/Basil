package com.joetr.basil.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joetr.basil.domain.model.RecipeQuery
import com.joetr.basil.domain.model.RecipeSort
import com.joetr.basil.domain.usecase.ObserveRecipesUseCase
import com.joetr.basil.ui.components.EmptyState
import com.joetr.basil.ui.components.HairlineDivider
import com.joetr.basil.ui.components.RecipeGridCard
import com.joetr.basil.ui.components.RecipeListRow
import com.joetr.basil.ui.components.ScreenTitleRow
import com.joetr.basil.ui.components.SearchField
import com.joetr.basil.ui.components.hostFromUrl
import com.joetr.basil.ui.theme.BasilSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

public class RecipesViewModel(
    private val observeRecipes: ObserveRecipesUseCase,
) {
    private val _query = MutableStateFlow(RecipeQuery())
    public val query: StateFlow<RecipeQuery> = _query.asStateFlow()
    public val recipes = _query.flatMapLatest { observeRecipes(it) }

    public fun setSearch(value: String) {
        _query.update { it.copy(search = value) }
    }

    public fun toggleFavouritesOnly() {
        _query.update { it.copy(favouritesOnly = !it.favouritesOnly) }
    }

    public fun setSort(sort: RecipeSort) {
        _query.update { it.copy(sort = sort) }
    }
}

@Composable
public fun RecipesScreen(
    viewModel: RecipesViewModel,
    onRecipeClick: (String) -> Unit,
    onAddRecipe: () -> Unit,
    modifier: Modifier = Modifier,
    detailOpenRecipeId: String? = null,
) {
    val recipes by viewModel.recipes.collectAsState(initial = emptyList())
    val query by viewModel.query.collectAsState()

    fun sharedRecipeId(recipeId: String): String? =
        if (detailOpenRecipeId == null) recipeId else null

    BoxWithConstraints(modifier.fillMaxSize()) {
            val phoneLayout = maxWidth < 600.dp
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(horizontal = BasilSpacing.gutter)) {
                    Spacer(Modifier.height(BasilSpacing.lg))
                    ScreenTitleRow(
                        title = if (phoneLayout) "All" else "Recipes",
                        onAdd = onAddRecipe,
                    )
                    if (!phoneLayout) {
                        Spacer(Modifier.height(BasilSpacing.sm))
                        Text(
                            if (recipes.isEmpty()) {
                                "Your personal recipe library"
                            } else {
                                "${recipes.size} recipes"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SearchField(
                        value = query.search,
                        onValueChange = viewModel::setSearch,
                        placeholder = "Search recipes",
                        modifier = Modifier.padding(vertical = BasilSpacing.lg),
                    )
                    if (!phoneLayout) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = Arrangement.spacedBy(BasilSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            com.joetr.basil.ui.components.Chip("Favourites", query.favouritesOnly, onClick = viewModel::toggleFavouritesOnly)
                            com.joetr.basil.ui.components.Chip(
                                "Recent",
                                query.sort == RecipeSort.UPDATED_DESC,
                                onClick = { viewModel.setSort(RecipeSort.UPDATED_DESC) },
                            )
                            com.joetr.basil.ui.components.Chip(
                                "A–Z",
                                query.sort == RecipeSort.TITLE_ASC,
                                onClick = { viewModel.setSort(RecipeSort.TITLE_ASC) },
                            )
                        }
                    }
                    Spacer(Modifier.height(if (phoneLayout) BasilSpacing.md else BasilSpacing.lg))
                }

                if (recipes.isEmpty()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            title = if (query.search.isBlank()) "No recipes yet" else "No matches",
                            body = if (query.search.isBlank()) {
                                "Import from a URL or create one manually."
                            } else {
                                "Try a different search term."
                            },
                        )
                    }
                } else if (phoneLayout) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = BasilSpacing.gutter,
                            end = BasilSpacing.gutter,
                            bottom = BasilSpacing.xl,
                        ),
                    ) {
                        items(recipes, key = { it.id }) { recipe ->
                            Column {
                                RecipeListRow(
                                    title = recipe.title,
                                    description = recipe.description,
                                    minutes = recipe.cookMinutes ?: recipe.prepMinutes,
                                    category = recipe.tags.firstOrNull(),
                                    sourceHost = hostFromUrl(recipe.sourceUrl),
                                    recipeId = sharedRecipeId(recipe.id),
                                    imageUrl = recipe.imageUrl,
                                    imageModel = recipe.localImageId?.let { "local-image://$it" },
                                    onClick = { onRecipeClick(recipe.id) },
                                )
                                HairlineDivider(Modifier.padding(start = 88.dp))
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(220.dp),
                        contentPadding = PaddingValues(
                            start = BasilSpacing.gutter,
                            end = BasilSpacing.gutter,
                            bottom = BasilSpacing.xl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(BasilSpacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(BasilSpacing.lg),
                    ) {
                        items(recipes, key = { it.id }) { recipe ->
                            RecipeGridCard(
                                title = recipe.title,
                                subtitle = listOfNotNull(
                                    (recipe.cookMinutes ?: recipe.prepMinutes)?.let { "$it min" },
                                    recipe.tags.firstOrNull(),
                                    hostFromUrl(recipe.sourceUrl),
                                ).joinToString(" · ").ifBlank { null },
                                recipeId = sharedRecipeId(recipe.id),
                                imageUrl = recipe.imageUrl,
                                imageModel = recipe.localImageId?.let { "local-image://$it" },
                                onClick = { onRecipeClick(recipe.id) },
                            )
                        }
                    }
                }
            }
    }
}
