package com.omniplayer.app.model

import android.net.Uri

enum class MediaKind { AUDIO, VIDEO }

data class OmniMedia(
    val id: Long,
    val title: String,
    val artist: String = "Unknown artist",
    val album: String = "Unknown album",
    val genre: String = "Unknown genre",
    val durationMs: Long = 0,
    val uri: Uri,
    val sizeBytes: Long = 0,
    val kind: MediaKind,
    val dateAddedSeconds: Long = 0,
    val mimeType: String? = null,
    val artworkUri: Uri? = null,
    val displayName: String = title,
)

fun Long.asDuration(): String {
    val totalSeconds = (this / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

fun Long.asFileSize(): String = when {
    this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
    this >= 1_024 -> "%.1f KB".format(this / 1_024.0)
    else -> "$this B"
}
