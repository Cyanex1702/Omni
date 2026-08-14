package com.omniplayer.app.download

import java.net.URI
import java.util.Locale

data class DownloadRequest(
    val url: String,
    val type: String,
    val quality: String,
) {
    val isAudio: Boolean
        get() = type == TYPE_AUDIO

    companion object {
        const val TYPE_AUDIO = "audio"
        const val TYPE_VIDEO = "video"

        private val webUrl = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        private val trailingSharePunctuation = setOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
        private val audioQualities = setOf("128", "192", "320")
        private val videoQualities = setOf("360", "720", "1080", "best")

        fun create(rawUrl: String, rawType: String, rawQuality: String): DownloadRequest {
            val url = extractUrl(rawUrl)
                ?: throw IllegalArgumentException("Paste a valid HTTP or HTTPS webpage or media link.")
            val type = rawType.trim().lowercase(Locale.US)
            require(type == TYPE_AUDIO || type == TYPE_VIDEO) {
                "Choose Audio or Video."
            }
            val quality = normalizeQuality(type, rawQuality)
            return DownloadRequest(url, type, quality)
        }

        fun extractUrl(text: String): String? {
            val candidate = webUrl.find(text.trim())?.value
                ?.replace("&amp;", "&")
                ?.trimEnd { it in trailingSharePunctuation }
                ?: return null
            return candidate.takeIf(::isSupportedUrl)
        }

        fun isSupportedUrl(value: String): Boolean {
            val uri = runCatching { URI(value) }.getOrNull() ?: return false
            return (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                !uri.host.isNullOrBlank()
        }

        private fun normalizeQuality(type: String, rawQuality: String): String {
            val fallback = if (type == TYPE_AUDIO) "320" else "1080"
            val quality = rawQuality.trim().lowercase(Locale.US).ifBlank { fallback }
            val supported = if (type == TYPE_AUDIO) audioQualities else videoQualities
            require(quality in supported) {
                if (type == TYPE_AUDIO) {
                    "Choose 128, 192, or 320 kbps audio."
                } else {
                    "Choose 360p, 720p, 1080p, or Best video."
                }
            }
            return quality
        }
    }
}

internal object DownloadFormatPolicy {
    fun audioSelector(): String =
        "bestaudio[ext=m4a]/bestaudio/best"

    fun videoSelector(quality: String): String {
        val heightFilter = quality.toIntOrNull()?.let { "[height<=$it]" }.orEmpty()
        return listOf(
            "bestvideo$heightFilter[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]",
            "best$heightFilter[ext=mp4]",
            "bestvideo$heightFilter+bestaudio",
            "best$heightFilter",
            "best",
        ).joinToString("/")
    }
}

internal data class ParsedDownloadProgress(
    val bytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = 0L,
    val percent: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val protocol: String = "",
    val title: String = "",
    val filename: String? = null,
    val structured: Boolean = false,
)

internal object DownloadProgressParser {
    private const val STRUCTURED_PREFIX = "OMNI_PROGRESS|"
    private val totalPattern = Regex(
        """\bof\s+~?\s*([\d.]+)\s*(B|KiB|MiB|GiB|TiB)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val speedPattern = Regex(
        """\bat\s+([\d.]+)\s*(B|KiB|MiB|GiB|TiB)/s\b""",
        RegexOption.IGNORE_CASE,
    )
    private val etaPattern = Regex("""\bETA\s+(?:(\d+):)?(\d+):(\d+)\b""", RegexOption.IGNORE_CASE)

    fun parse(line: String, percent: Int): ParsedDownloadProgress {
        parseStructured(line, percent)?.let { return it }
        val match = totalPattern.find(line) ?: return ParsedDownloadProgress(0L, 0L)
        val number = match.groupValues[1].toDoubleOrNull() ?: return ParsedDownloadProgress(0L, 0L)
        val multiplier = when (match.groupValues[2].lowercase(Locale.US)) {
            "kib" -> 1_024.0
            "mib" -> 1_048_576.0
            "gib" -> 1_073_741_824.0
            "tib" -> 1_099_511_627_776.0
            else -> 1.0
        }
        val total = (number * multiplier).toLong().coerceAtLeast(0L)
        val speed = speedPattern.find(line)?.let { speedMatch ->
            val amount = speedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            (amount * unitMultiplier(speedMatch.groupValues[2])).toLong()
        } ?: 0L
        val eta = etaPattern.find(line)?.let { etaMatch ->
            val hours = etaMatch.groupValues[1].toLongOrNull() ?: 0L
            val minutes = etaMatch.groupValues[2].toLongOrNull() ?: 0L
            val seconds = etaMatch.groupValues[3].toLongOrNull() ?: 0L
            hours * 3_600L + minutes * 60L + seconds
        } ?: 0L
        return ParsedDownloadProgress(
            bytes = total * percent.coerceIn(0, 100) / 100L,
            totalBytes = total,
            speedBytesPerSecond = speed,
            etaSeconds = eta,
            percent = percent.coerceIn(0, 100),
        )
    }

    private fun parseStructured(line: String, fallbackPercent: Int): ParsedDownloadProgress? {
        val marker = line.indexOf(STRUCTURED_PREFIX)
        if (marker < 0) return null
        val fields = line.substring(marker + STRUCTURED_PREFIX.length).split('|', limit = 8)
        if (fields.size < 8) return null
        val bytes = fields[0].number().coerceAtLeast(0L)
        val total = fields[1].number().coerceAtLeast(0L)
        val calculatedPercent = if (total > 0L) {
            (bytes.coerceAtMost(total) * 100L / total).toInt()
        } else {
            fallbackPercent.coerceIn(0, 100)
        }
        return ParsedDownloadProgress(
            bytes = bytes,
            totalBytes = total,
            speedBytesPerSecond = fields[2].number().coerceAtLeast(0L),
            etaSeconds = fields[3].number().coerceAtLeast(0L),
            percent = calculatedPercent,
            hasVideo = fields[4].isCodec(),
            hasAudio = fields[5].isCodec(),
            protocol = fields[6].takeUnless { it.isMissing() }.orEmpty(),
            filename = fields[7].takeUnless { it.isMissing() },
            structured = true,
        )
    }

    private fun String.number(): Long =
        takeUnless { it.isMissing() }?.toDoubleOrNull()?.toLong() ?: 0L

    private fun String.isCodec(): Boolean = !isMissing() && !equals("none", true)

    private fun String.isMissing(): Boolean =
        isBlank() || equals("NA", true) || equals("N/A", true) || equals("None", true) ||
            equals("null", true)

    private fun unitMultiplier(unit: String): Double = when (unit.lowercase(Locale.US)) {
        "kib" -> 1_024.0
        "mib" -> 1_048_576.0
        "gib" -> 1_073_741_824.0
        "tib" -> 1_099_511_627_776.0
        else -> 1.0
    }
}
