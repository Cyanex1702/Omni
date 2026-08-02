package com.omniplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "omni_preferences")

data class OmniSettings(
    val wifiOnly: Boolean = true,
    val resumePlayback: Boolean = true,
    val gaplessPlayback: Boolean = false,
    val theme: String = "amoled",
    val playerAppearance: String = "square",
    val simultaneousDownloads: Int = 2,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val WifiOnly = booleanPreferencesKey("wifi_only")
        val ResumePlayback = booleanPreferencesKey("resume_playback")
        val GaplessPlayback = booleanPreferencesKey("gapless_playback")
        val Theme = stringPreferencesKey("theme")
        val PlayerAppearance = stringPreferencesKey("player_appearance")
        val Favorites = stringPreferencesKey("favorite_uris")
        val Recent = stringPreferencesKey("recent_uris")
        val SimultaneousDownloads = intPreferencesKey("simultaneous_downloads")
    }

    val settings: Flow<OmniSettings> = context.dataStore.data.map { values ->
        OmniSettings(
            wifiOnly = values[Keys.WifiOnly] ?: true,
            resumePlayback = values[Keys.ResumePlayback] ?: true,
            gaplessPlayback = values[Keys.GaplessPlayback] ?: false,
            theme = values[Keys.Theme] ?: "amoled",
            playerAppearance = values[Keys.PlayerAppearance] ?: "square",
            simultaneousDownloads = (values[Keys.SimultaneousDownloads] ?: 2).coerceIn(1, 3),
        )
    }

    val favorites: Flow<Set<String>> = context.dataStore.data.map { values ->
        values[Keys.Favorites].orEmpty().split('|').filter(String::isNotBlank).toSet()
    }

    val recentUris: Flow<List<String>> = context.dataStore.data.map { values ->
        values[Keys.Recent].orEmpty().split('|').filter(String::isNotBlank).distinct().take(20)
    }

    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.WifiOnly] = value }
    suspend fun setResumePlayback(value: Boolean) = context.dataStore.edit { it[Keys.ResumePlayback] = value }
    suspend fun setGaplessPlayback(value: Boolean) = context.dataStore.edit { it[Keys.GaplessPlayback] = value }
    suspend fun setTheme(value: String) = context.dataStore.edit { it[Keys.Theme] = value }
    suspend fun setPlayerAppearance(value: String) = context.dataStore.edit {
        it[Keys.PlayerAppearance] = value.takeIf { option -> option in setOf("square", "vinyl", "wave") } ?: "square"
    }
    suspend fun setSimultaneousDownloads(value: Int) = context.dataStore.edit {
        it[Keys.SimultaneousDownloads] = value.coerceIn(1, 3)
    }

    suspend fun toggleFavorite(uri: String) = context.dataStore.edit { values ->
        val favorites = values[Keys.Favorites].orEmpty()
            .split('|').filter(String::isNotBlank).toMutableSet()
        if (!favorites.add(uri)) favorites.remove(uri)
        values[Keys.Favorites] = favorites.joinToString("|")
    }

    suspend fun markRecent(uri: String) = context.dataStore.edit { values ->
        val recent = values[Keys.Recent].orEmpty()
            .split('|')
            .filter(String::isNotBlank)
            .filterNot { it == uri }
            .toMutableList()
        recent.add(0, uri)
        values[Keys.Recent] = recent.take(20).joinToString("|")
    }
}
