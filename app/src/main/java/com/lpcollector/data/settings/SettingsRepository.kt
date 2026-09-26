package com.lpcollector.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val tokenKey = stringPreferencesKey("discogs_token")
    private val vinylOnlyKey = booleanPreferencesKey("vinyl_only")

    val token: Flow<String> = context.dataStore.data.map { it[tokenKey].orEmpty() }
    val vinylOnly: Flow<Boolean> = context.dataStore.data.map { it[vinylOnlyKey] ?: true }

    suspend fun currentToken(): String = token.first()

    suspend fun setToken(value: String) {
        context.dataStore.edit { it[tokenKey] = value.trim() }
    }

    suspend fun setVinylOnly(value: Boolean) {
        context.dataStore.edit { it[vinylOnlyKey] = value }
    }
}
