package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pulsechat_prefs")

class SessionPreferences(private val context: Context) {
    private val keyGithubRepo = stringPreferencesKey("github_repo")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")

    val githubRepoFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyGithubRepo] ?: ""
    }

    val darkThemeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyDarkTheme] ?: true
    }

    suspend fun saveGithubRepo(repo: String) {
        context.dataStore.edit { prefs ->
            prefs[keyGithubRepo] = repo.trim()
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyDarkTheme] = enabled
        }
    }
}
