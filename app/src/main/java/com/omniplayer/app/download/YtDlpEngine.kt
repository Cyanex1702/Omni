package com.omniplayer.app.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

data class YtDlpSourceInfo(
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val estimatedBytes: Long,
    val extractor: String,
)

data class YtDlpDownloadResult(
    val source: YtDlpSourceInfo,
    val files: List<File>,
)

/** Omni-owned adapter around youtubedl-android. One extraction performs the download and emits JSON. */
class YtDlpEngine(private val context: Context) {

    suspend fun prepareRuntime(onStage: suspend (String) -> Unit) {
        onStage("Preparing download engine")
        YtDlpRuntime.ensureInitialized(context)
        onStage("Checking site support")
        val update = YtDlpRuntime.updateIfDue(context)
        if (update.isFailure) {
            // Updates are opportunistic. A network or GitHub problem must not suppress the
            // extractor that is already bundled in the APK.
            onStage("Using bundled site support")
        }
        onStage("Starting download")
    }

    fun download(
        request: DownloadRequest,
        outputDirectory: File,
        processId: String,
        progress: (Float, Long, String) -> Unit,
    ): YtDlpDownloadResult {
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw IOException("Omni could not create its temporary download folder.")
        }

        val command = buildRequest(request, outputDirectory, usePublicYoutubeClients = false)
        val response = try {
            execute(command, processId, progress)
        } catch (firstError: Exception) {
            if (!request.isYoutubeUrl() || !firstError.isYoutubeVerificationFailure()) throw firstError

            progress(-1f, -1L, "OMNI_STAGE|YouTube verification requested - trying public playback clients")
            outputDirectory.listFiles()?.forEach { it.deleteRecursively() }
            val fallback = buildRequest(request, outputDirectory, usePublicYoutubeClients = true)
            try {
                execute(fallback, processId, progress)
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(firstError)
                throw fallbackError
            }
        }
        val json = response.out.lineSequence()
            .map(String::trim)
            .lastOrNull { it.startsWith('{') && it.endsWith('}') }
            ?.let(::JSONObject)
            ?: throw IOException("yt-dlp completed without returning media information.")

        validateSource(json)
        val files = outputDirectory.walkTopDown()
            .filter(File::isFile)
            .filter { it.length() > 0L }
            .filterNot { it.extension.lowercase() in NON_MEDIA_EXTENSIONS }
            .sortedByDescending(File::length)
            .toList()
            .ifEmpty { throw IOException("yt-dlp finished but did not create a media file.") }

        return YtDlpDownloadResult(
            source = sourceInfo(json, request.url),
            files = files,
        )
    }

    private fun buildRequest(
        request: DownloadRequest,
        outputDirectory: File,
        usePublicYoutubeClients: Boolean,
    ): YoutubeDLRequest = YoutubeDLRequest(request.url).apply {
            addCommonOptions()
            addOption("--newline")
            // --print-json enables quiet mode inside yt-dlp. Without explicitly restoring
            // progress output, the Android callback receives only the final JSON line and the
            // UI jumps straight from "Starting download" to "Completed".
            addOption("--progress")
            addOption("--progress-delta", "0.25")
            addOption(
                "--progress-template",
                "download:OMNI_PROGRESS|%(progress.downloaded_bytes|0)s|" +
                    "%(progress.total_bytes,progress.total_bytes_estimate|0)s|" +
                    "%(progress.speed|0)s|%(progress.eta|0)s|%(info.vcodec|none)s|" +
                    "%(info.acodec|none)s|%(info.protocol|unknown)s|%(progress.filename)s",
            )
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-simulate")
            addOption("--print-json")
            addOption("--match-filter", "!is_live & !was_live & !has_drm")
            addOption("--retries", 10)
            addOption("--fragment-retries", 10)
            addOption("--extractor-retries", 5)
            addOption("--file-access-retries", 3)
            addOption("--retry-sleep", "exp=1:20")
            addOption("--socket-timeout", 30)
            addOption("--concurrent-fragments", 2)
            addOption("--abort-on-unavailable-fragments")
            addOption("--windows-filenames")
            addOption("-P", outputDirectory.absolutePath)
            addOption("-o", "%(title).170B [%(id)s].%(ext)s")

            if (usePublicYoutubeClients) {
                addOption("--extractor-args", "youtube:player_client=web_safari,web_embedded")
            }

            if (request.isAudio) {
                addOption("-f", DownloadFormatPolicy.audioSelector())
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", "${request.quality}K")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--convert-thumbnails", "jpg")
            } else {
                // Prefer Android-friendly H.264/AAC MP4, then fall back to any playable
                // streams/container instead of failing a valid WebM-only or MKV-only source.
                addOption("-f", DownloadFormatPolicy.videoSelector(request.quality))
                // MPEG-TS fragments remain readable as they grow, enabling best-effort
                // playback before an HLS download has fully completed.
                addOption("--hls-use-mpegts")
            }
        }

    private fun execute(
        command: YoutubeDLRequest,
        processId: String,
        progress: (Float, Long, String) -> Unit,
    ) = YtDlpRuntime.execute {
        YoutubeDL.getInstance().execute(command, processId, progress)
    }

    private fun DownloadRequest.isYoutubeUrl(): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private fun Throwable.isYoutubeVerificationFailure(): Boolean {
        val diagnostic = generateSequence(this) { it.cause }
            .joinToString("\n") { "${it.message.orEmpty()}\n${it.stackTraceToString()}" }
            .lowercase()
        return diagnostic.contains("sign in to confirm") ||
            diagnostic.contains("not a bot") ||
            diagnostic.contains("account verification")
    }

    private fun YoutubeDLRequest.addCommonOptions() {
        val cacheDirectory = File(context.cacheDir, "yt-dlp-cache").apply {
            if (!exists()) mkdirs()
        }
        addOption("--no-config")
        addOption("--cache-dir", cacheDirectory.absolutePath)
        addOption(
            "--js-runtimes",
            "quickjs:${YtDlpRuntime.quickJsBinary(context).absolutePath}",
        )
    }

    private fun validateSource(json: JSONObject) {
        val liveStatus = json.optString("live_status")
        if (
            json.optBoolean("is_live", false) ||
            json.optBoolean("was_live", false) ||
            liveStatus == "is_live" ||
            liveStatus == "post_live"
        ) {
            throw IOException("Live streams are not supported because they may never finish downloading.")
        }
        if (json.optBoolean("has_drm", false) || json.optBoolean("_has_drm", false)) {
            throw IOException("This media is DRM-protected and cannot be downloaded by Omni.")
        }
        if (!containsPlayableFormat(json)) {
            throw IOException("The website returned metadata, but no playable audio or video stream.")
        }
    }

    private fun sourceInfo(json: JSONObject, sourceUrl: String): YtDlpSourceInfo {
        val title = json.optString("title").ifBlank {
            runCatching { URI(sourceUrl).host }.getOrNull().orEmpty().ifBlank { "Omni media" }
        }
        val artist = sequenceOf("artist", "uploader", "channel", "creator")
            .map { key -> json.optString(key) }
            .firstOrNull(String::isNotBlank)
            ?: "Omni download"
        val thumbnail = json.optString("thumbnail").takeIf(String::isNotBlank)
        val durationMs = (json.optDouble("duration", 0.0) * 1_000.0).toLong().coerceAtLeast(0L)
        val estimatedBytes = sequenceOf("filesize", "filesize_approx")
            .map { json.optLong(it, 0L) }
            .firstOrNull { it > 0L }
            ?: estimateSelectedFormats(json)

        return YtDlpSourceInfo(
            title = title,
            artist = artist,
            thumbnailUrl = thumbnail,
            durationMs = durationMs,
            estimatedBytes = estimatedBytes,
            extractor = json.optString("extractor_key", json.optString("extractor", "Website")),
        )
    }

    private fun estimateSelectedFormats(json: JSONObject): Long {
        val formats = json.optJSONArray("requested_downloads")
            ?: json.optJSONArray("requested_formats")
            ?: return 0L
        var total = 0L
        for (index in 0 until formats.length()) {
            val item = formats.optJSONObject(index) ?: continue
            total += sequenceOf("filesize", "filesize_approx")
                .map { item.optLong(it, 0L) }
                .firstOrNull { it > 0L }
                ?: 0L
        }
        return total
    }

    private fun containsPlayableFormat(json: JSONObject): Boolean {
        val selected = json.optJSONArray("requested_downloads")
            ?: json.optJSONArray("requested_formats")
        if (selected != null && selected.containsPlayableFormat()) return true

        val formats = json.optJSONArray("formats")
        if (formats == null || formats.length() == 0) {
            return json.optString("url").isNotBlank()
        }
        return formats.containsPlayableFormat()
    }

    private fun JSONArray.containsPlayableFormat(): Boolean {
        for (index in 0 until length()) {
            val format = optJSONObject(index) ?: continue
            val extension = format.optString("ext").lowercase()
            val protocol = format.optString("protocol").lowercase()
            val hasAudio = !format.optString("acodec", "none").equals("none", true)
            val hasVideo = !format.optString("vcodec", "none").equals("none", true)
            if ((hasAudio || hasVideo) && extension !in NON_MEDIA_EXTENSIONS && protocol != "mhtml") {
                return true
            }
        }
        return false
    }

    companion object {
        private val NON_MEDIA_EXTENSIONS = setOf(
            "part", "ytdl", "json", "jpg", "jpeg", "png", "webp", "gif",
            "vtt", "srt", "ass", "lrc", "description",
        )
    }
}
