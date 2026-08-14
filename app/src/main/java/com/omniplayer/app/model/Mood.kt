package com.omniplayer.app.model

import java.util.Locale

data class MoodDefinition(
    val id: String,
    val name: String,
    val description: String,
    val colorArgb: Long,
    val isCustom: Boolean = false,
)

data class MoodLibraryState(
    val customMoods: List<MoodDefinition> = emptyList(),
    val manualAssignments: Map<String, Set<String>> = emptyMap(),
) {
    val moods: List<MoodDefinition>
        get() = BuiltInMoods.all + customMoods.filterNot { custom ->
            BuiltInMoods.all.any { it.id == custom.id }
        }
}

data class MoodRecommendation(
    val media: OmniMedia,
    val mood: MoodDefinition,
    val score: Float,
    val manuallyAssigned: Boolean,
    val reason: String,
)

data class AcousticMoodProfile(
    val mediaUri: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val analyzedAtMillis: Long,
    val energy: Float,
    val brightness: Float,
    val tempoBpm: Float,
    val dynamicRange: Float,
    val moodScores: Map<String, Float>,
) {
    fun isCurrent(media: OmniMedia): Boolean =
        mediaUri == media.uri.toString() && sizeBytes == media.sizeBytes &&
            dateModifiedSeconds == media.dateModifiedSeconds
}

object BuiltInMoods {
    const val CHILL = "chill"
    const val WORKOUT = "workout"
    const val FOCUS = "focus"
    const val PARTY = "party"
    const val ROMANCE = "romance"
    const val SLEEP = "sleep"

    val all = listOf(
        MoodDefinition(CHILL, "Chill", "Calm, laid-back, ambient, lo-fi, acoustic and mellow songs for slowing down.", 0xFF9B6BFF),
        MoodDefinition(WORKOUT, "Workout", "Energetic, powerful, fast, rock, hip-hop, metal and electronic music for movement.", 0xFFFF4F9A),
        MoodDefinition(FOCUS, "Focus", "Instrumental, classical, soundtrack, piano, ambient and steady music for concentration.", 0xFF4C82FF),
        MoodDefinition(PARTY, "Party", "Dance, pop, EDM, house, disco, funk and upbeat songs with a celebratory feeling.", 0xFFFF7043),
        MoodDefinition(ROMANCE, "Romance", "Love songs, romantic ballads, R&B, soul and intimate music for warm moments.", 0xFFFF5C7A),
        MoodDefinition(SLEEP, "Sleep", "Soft, peaceful, slow, meditation, ambient, piano and gentle music for resting.", 0xFF7770D8),
    )
}

object MoodRecommendationEngine {
    private val stopWords = setOf(
        "and", "the", "for", "with", "that", "this", "from", "into", "your", "songs", "song",
        "music", "mood", "feeling", "feelings", "when", "some", "very", "overall", "like",
    )

    private val extraKeywords = mapOf(
        BuiltInMoods.CHILL to setOf("chill", "calm", "mellow", "ambient", "lofi", "lo-fi", "acoustic", "jazz", "indie", "night", "rain", "dream"),
        BuiltInMoods.WORKOUT to setOf("workout", "gym", "energy", "energetic", "power", "rock", "metal", "rap", "hip hop", "hip-hop", "edm", "drum", "bass", "run"),
        BuiltInMoods.FOCUS to setOf("focus", "study", "instrumental", "classical", "soundtrack", "score", "piano", "ambient", "lofi", "lo-fi", "concentration", "deep work"),
        BuiltInMoods.PARTY to setOf("party", "dance", "pop", "edm", "house", "disco", "funk", "club", "celebration", "remix", "festival"),
        BuiltInMoods.ROMANCE to setOf("romance", "romantic", "love", "heart", "ballad", "r&b", "rnb", "soul", "kiss", "valentine", "together"),
        BuiltInMoods.SLEEP to setOf("sleep", "peaceful", "soft", "slow", "meditation", "ambient", "piano", "gentle", "lullaby", "relax", "rain", "dream"),
    )

    fun recommendationMap(
        media: List<OmniMedia>,
        state: MoodLibraryState,
        acousticProfiles: Map<String, AcousticMoodProfile> = emptyMap(),
    ): Map<String, List<MoodRecommendation>> {
        val moods = state.moods
        val output = moods.associate { it.id to mutableListOf<MoodRecommendation>() }
        media.asSequence().filter { it.kind == MediaKind.AUDIO }.forEach { song ->
            val normalized = NormalizedSong(song)
            val profile = acousticProfiles[song.uri.toString()]?.takeIf { it.isCurrent(song) }
            moods.forEach { mood -> match(normalized, mood, state, profile)?.let(output.getValue(mood.id)::add) }
        }
        return output.mapValues { (_, matches) ->
            matches.sortedWith(
                compareByDescending<MoodRecommendation> { it.manuallyAssigned }
                    .thenByDescending { it.score }
                    .thenBy { it.media.title.lowercase(Locale.ROOT) },
            )
        }
    }

    fun recommendations(
        mood: MoodDefinition,
        media: List<OmniMedia>,
        state: MoodLibraryState,
        acousticProfiles: Map<String, AcousticMoodProfile> = emptyMap(),
    ): List<MoodRecommendation> = recommendationMap(media, state, acousticProfiles)[mood.id].orEmpty()

    fun moodsFor(
        song: OmniMedia,
        state: MoodLibraryState,
        acousticProfiles: Map<String, AcousticMoodProfile> = emptyMap(),
    ): List<MoodRecommendation> {
        val normalized = NormalizedSong(song)
        val profile = acousticProfiles[song.uri.toString()]?.takeIf { it.isCurrent(song) }
        return state.moods.mapNotNull { match(normalized, it, state, profile) }
            .sortedWith(compareByDescending<MoodRecommendation> { it.manuallyAssigned }.thenByDescending { it.score })
            .take(4)
    }

    private fun match(
        normalizedSong: NormalizedSong,
        mood: MoodDefinition,
        state: MoodLibraryState,
        acousticProfile: AcousticMoodProfile?,
    ): MoodRecommendation? {
        val song = normalizedSong.media
        val uri = song.uri.toString()
        val manual = mood.id in state.manualAssignments[uri].orEmpty()
        if (manual) return MoodRecommendation(song, mood, 1f, true, "Added by you")

        val genre = normalizedSong.genre
        val fullText = normalizedSong.fullText
        val keywords = buildSet {
            addAll(extraKeywords[mood.id].orEmpty())
            addAll(tokens(mood.name))
            addAll(tokens(mood.description))
        }
        val matched = keywords.filter { keyword -> normalize(keyword) in fullText }.distinct()
        var metadataScore = (matched.size * 0.18f).coerceAtMost(0.82f)
        if (normalize(mood.name) in fullText) metadataScore += 0.22f
        if (matched.any { normalize(it) in genre }) metadataScore += 0.12f

        when (mood.id) {
            BuiltInMoods.CHILL -> if (song.durationMs >= 240_000L) metadataScore += 0.04f
            BuiltInMoods.FOCUS -> if (song.durationMs >= 240_000L) metadataScore += 0.05f
            BuiltInMoods.SLEEP -> if (song.durationMs >= 300_000L) metadataScore += 0.05f
            BuiltInMoods.WORKOUT, BuiltInMoods.PARTY -> if (song.durationMs in 120_000L..270_000L) metadataScore += 0.03f
        }

        val acousticScore = acousticScoreFor(mood, acousticProfile)
        val score = when {
            acousticScore != null && metadataScore > 0f -> acousticScore * 0.62f + metadataScore.coerceAtMost(1f) * 0.38f
            acousticScore != null -> acousticScore * 0.82f
            else -> metadataScore
        }
        if (score < 0.18f) return null
        val reason = when {
            acousticScore != null -> "Acoustic match ${(acousticScore * 100).toInt()}%"
            matched.isNotEmpty() -> "Matched ${matched.take(3).joinToString(", ")}"
            else -> "Suggested from track details"
        }
        return MoodRecommendation(song, mood, score.coerceAtMost(0.99f), false, reason)
    }

    private fun acousticScoreFor(mood: MoodDefinition, profile: AcousticMoodProfile?): Float? {
        profile ?: return null
        profile.moodScores[mood.id]?.let { return it }
        if (!mood.isCustom) return null
        val description = normalize("${mood.name} ${mood.description}")
        val aliases = mapOf(
            BuiltInMoods.CHILL to setOf("calm", "chill", "mellow", "relax", "rain"),
            BuiltInMoods.WORKOUT to setOf("energy", "energetic", "gym", "run", "power"),
            BuiltInMoods.FOCUS to setOf("focus", "study", "work", "concentration", "steady"),
            BuiltInMoods.PARTY to setOf("party", "dance", "club", "celebrate", "upbeat"),
            BuiltInMoods.ROMANCE to setOf("love", "romance", "warm", "intimate"),
            BuiltInMoods.SLEEP to setOf("sleep", "soft", "quiet", "peaceful", "slow"),
        )
        return aliases.mapNotNull { (id, words) ->
            profile.moodScores[id]?.takeIf { words.any(description::contains) }
        }.maxOrNull()
    }

    private data class NormalizedSong(val media: OmniMedia) {
        val genre = normalize(media.genre)
        val fullText = normalize("${media.title} ${media.artist} ${media.album} ${media.displayName}") + " " + genre
    }

    private fun tokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .filter { it.length >= 3 && it !in stopWords }
        .toSet()

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
