package com.omniplayer.app.data

import android.content.Context
import android.util.AtomicFile
import com.omniplayer.app.model.AcousticMoodProfile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AcousticMoodRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "acoustic_mood_profiles.json"))
    private val mutex = Mutex()
    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<Map<String, AcousticMoodProfile>> = _profiles.asStateFlow()

    suspend fun put(profile: AcousticMoodProfile) = mutex.withLock {
        val updated = _profiles.value.toMutableMap().apply { put(profile.mediaUri, profile) }
        writeProfiles(updated)
        _profiles.value = updated
    }

    suspend fun clear() = mutex.withLock {
        writeProfiles(emptyMap())
        _profiles.value = emptyMap()
    }

    private fun readProfiles(): Map<String, AcousticMoodProfile> = runCatching {
        if (!file.baseFile.exists()) return@runCatching emptyMap()
        val text = file.openRead().bufferedReader().use { it.readText() }
        val array = JSONArray(text.ifBlank { "[]" })
        buildMap {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val uri = item.optString("uri")
                if (uri.isBlank()) continue
                val scoresJson = item.optJSONObject("scores") ?: JSONObject()
                val scores = buildMap {
                    val keys = scoresJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, scoresJson.optDouble(key, 0.0).toFloat())
                    }
                }
                put(
                    uri,
                    AcousticMoodProfile(
                        mediaUri = uri,
                        sizeBytes = item.optLong("size"),
                        dateModifiedSeconds = item.optLong("modified"),
                        analyzedAtMillis = item.optLong("analyzedAt"),
                        energy = item.optDouble("energy").toFloat(),
                        brightness = item.optDouble("brightness").toFloat(),
                        tempoBpm = item.optDouble("tempo").toFloat(),
                        dynamicRange = item.optDouble("dynamic").toFloat(),
                        moodScores = scores,
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    private suspend fun writeProfiles(profiles: Map<String, AcousticMoodProfile>) = withContext(Dispatchers.IO) {
        val json = JSONArray().apply {
            profiles.values.forEach { profile ->
                val scores = JSONObject().apply {
                    profile.moodScores.forEach { (mood, score) -> put(mood, score.toDouble()) }
                }
                put(
                    JSONObject()
                        .put("uri", profile.mediaUri)
                        .put("size", profile.sizeBytes)
                        .put("modified", profile.dateModifiedSeconds)
                        .put("analyzedAt", profile.analyzedAtMillis)
                        .put("energy", profile.energy.toDouble())
                        .put("brightness", profile.brightness.toDouble())
                        .put("tempo", profile.tempoBpm.toDouble())
                        .put("dynamic", profile.dynamicRange.toDouble())
                        .put("scores", scores),
                )
            }
        }.toString()
        val stream = file.startWrite()
        try {
            val writer = stream.bufferedWriter()
            writer.write(json)
            writer.flush()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }
}
