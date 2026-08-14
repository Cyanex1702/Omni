package com.omniplayer.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omniplayer.app.model.MoodDefinition
import com.omniplayer.app.model.MoodLibraryState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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
        val CustomMoods = stringPreferencesKey("custom_moods_json")
        val MoodAssignments = stringPreferencesKey("mood_assignments_json")
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


    val moodLibrary: Flow<MoodLibraryState> = context.dataStore.data.map { values ->
        MoodLibraryState(
            customMoods = decodeCustomMoods(values[Keys.CustomMoods]),
            manualAssignments = decodeMoodAssignments(values[Keys.MoodAssignments]),
        )
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

    suspend fun createCustomMood(name: String, description: String) = context.dataStore.edit { values ->
        val cleanName = name.trim().take(32)
        if (cleanName.isBlank()) return@edit
        val current = decodeCustomMoods(values[Keys.CustomMoods]).toMutableList()
        if (current.any { it.name.equals(cleanName, ignoreCase = true) }) return@edit
        val palette = listOf(0xFFFFB74D, 0xFF26C6DA, 0xFFAB47BC, 0xFF66BB6A, 0xFFEC407A, 0xFF5C6BC0)
        current += MoodDefinition(
            id = "custom_${UUID.randomUUID()}",
            name = cleanName,
            description = description.trim().take(280),
            colorArgb = palette[current.size % palette.size],
            isCustom = true,
        )
        values[Keys.CustomMoods] = encodeCustomMoods(current)
    }

    suspend fun deleteCustomMood(id: String) = context.dataStore.edit { values ->
        val remaining = decodeCustomMoods(values[Keys.CustomMoods]).filterNot { it.id == id }
        val assignments = decodeMoodAssignments(values[Keys.MoodAssignments])
            .mapValues { (_, moods) -> moods - id }
            .filterValues { it.isNotEmpty() }
        values[Keys.CustomMoods] = encodeCustomMoods(remaining)
        values[Keys.MoodAssignments] = encodeMoodAssignments(assignments)
    }

    suspend fun toggleMoodAssignment(uri: String, moodId: String) = context.dataStore.edit { values ->
        if (uri.isBlank() || moodId.isBlank()) return@edit
        val assignments = decodeMoodAssignments(values[Keys.MoodAssignments]).toMutableMap()
        val selected = assignments[uri].orEmpty().toMutableSet()
        if (!selected.add(moodId)) selected.remove(moodId)
        if (selected.isEmpty()) assignments.remove(uri) else assignments[uri] = selected
        values[Keys.MoodAssignments] = encodeMoodAssignments(assignments)
    }

    private fun decodeCustomMoods(raw: String?): List<MoodDefinition> = runCatching {
        val array = JSONArray(raw.orEmpty().ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    add(
                        MoodDefinition(
                            id = id,
                            name = name,
                            description = item.optString("description"),
                            colorArgb = item.optLong("color", 0xFF9B6BFF),
                            isCustom = true,
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeCustomMoods(moods: List<MoodDefinition>): String = JSONArray().apply {
        moods.forEach { mood ->
            put(
                JSONObject()
                    .put("id", mood.id)
                    .put("name", mood.name)
                    .put("description", mood.description)
                    .put("color", mood.colorArgb),
            )
        }
    }.toString()

    private fun decodeMoodAssignments(raw: String?): Map<String, Set<String>> = runCatching {
        val root = JSONObject(raw.orEmpty().ifBlank { "{}" })
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val uri = keys.next()
                val array = root.optJSONArray(uri) ?: continue
                val moods = buildSet {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf(String::isNotBlank)?.let(::add)
                    }
                }
                if (moods.isNotEmpty()) put(uri, moods)
            }
        }
    }.getOrDefault(emptyMap())

    private fun encodeMoodAssignments(assignments: Map<String, Set<String>>): String = JSONObject().apply {
        assignments.forEach { (uri, moods) -> put(uri, JSONArray(moods.toList())) }
    }.toString()
}
