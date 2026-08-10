package com.joetr.basil.data.recipe

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.joetr.basil.db.BasilDatabase
import com.joetr.basil.domain.model.ThemeMode
import com.joetr.basil.domain.repository.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class DefaultUserSettingsRepository(
    private val database: BasilDatabase,
    scope: CoroutineScope,
) : UserSettingsRepository {
    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val showStoreSearchLinks = MutableStateFlow(false)

    init {
        scope.launch {
            themeMode.value = loadThemeMode()
            showStoreSearchLinks.value = loadShowStoreSearchLinks()
        }
    }

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.upsertSetting(THEME_MODE_KEY, mode.name)
        }
        themeMode.value = mode
    }

    override fun observeShowStoreSearchLinks(): Flow<Boolean> = showStoreSearchLinks.asStateFlow()

    override suspend fun setShowStoreSearchLinks(enabled: Boolean) {
        withContext(Dispatchers.Default) {
            database.recipesQueries.upsertSetting(SHOW_STORE_SEARCH_LINKS_KEY, enabled.toString())
        }
        showStoreSearchLinks.value = enabled
    }

    private suspend fun loadThemeMode(): ThemeMode = withContext(Dispatchers.Default) {
        ThemeMode.fromStored(database.recipesQueries.selectSetting(THEME_MODE_KEY).awaitAsOneOrNull())
    }

    private suspend fun loadShowStoreSearchLinks(): Boolean = withContext(Dispatchers.Default) {
        database.recipesQueries.selectSetting(SHOW_STORE_SEARCH_LINKS_KEY).awaitAsOneOrNull()
            .equals("true", ignoreCase = true)
    }

    private companion object {
        const val THEME_MODE_KEY = "theme_mode"
        const val SHOW_STORE_SEARCH_LINKS_KEY = "show_store_search_links"
    }
}
