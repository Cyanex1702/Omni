package com.omniplayer.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.omniplayer.app.model.MediaKind
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class MediaProbe(
    val kind: MediaKind,
    val mimeType: String,
    val extension: String,
    val durationMs: Long,
    val sizeBytes: Long,
)

internal data class ValidatedDownload(
    val file: File,
    val probe: MediaProbe,
)

internal class DownloadedMediaStore(private val context: Context) {

    fun validate(
        candidates: List<File>,
        request: DownloadRequest,
        fallbackDurationMs: Long,
    ): ValidatedDownload {
        val playable = candidates.mapNotNull { file ->
            runCatching { ValidatedDownload(file, probeMedia(file, request.type)) }.getOrNull()
        }
        if (playable.isEmpty()) {
            throw IOException("The website finished downloading, but the result is not playable media.")
        }
        val selected = playable.sortedWith(
            compareByDescending<ValidatedDownload> {
                when (request.type) {
                    DownloadRequest.TYPE_AUDIO -> it.file.extension.equals("mp3", true)
                    else -> it.file.extension.equals("mp4", true)
                }
            }.thenByDescending { it.file.length() }
        ).first()
        return selected.copy(
            probe = selected.probe.copy(
                durationMs = selected.probe.durationMs.takeIf { it > 0L } ?: fallbackDurationMs,
            ),
        )
    }

    suspend fun publish(download: ValidatedDownload, source: YtDlpSourceInfo): Pair<Uri, String> {
        val filename = safeFilename(source.title, download.probe.extension)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(download.file, filename, download.probe, source)
        } else {
            publishToAppStorage(download.file, filename, download.probe.kind)
        }
        return uri to filename
    }

    private fun probeMedia(file: File, requestedType: String): MediaProbe {
        if (!file.isFile || file.length() <= 0L) {
            throw IOException("The downloaded file is empty.")
        }
        val retriever = MediaMetadataRetriever()
        try {
            val retrieverReady = runCatching { retriever.setDataSource(file.absolutePath) }.isSuccess
            var hasVideo = retrieverReady && (
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes" ||
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()?.let { it > 0 } == true
                )
            var hasAudio = retrieverReady &&
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            var extractorMime: String? = null
            var extractorDuration = 0L

            if (!hasAudio && !hasVideo) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    for (index in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(index)
                        val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (trackMime.startsWith("audio/")) hasAudio = true
                        if (trackMime.startsWith("video/")) hasVideo = true
                        if (extractorMime == null &&
                            (trackMime.startsWith("audio/") || trackMime.startsWith("video/"))
                        ) {
                            extractorMime = trackMime
                        }
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            extractorDuration = maxOf(
                                extractorDuration,
                                format.getLong(MediaFormat.KEY_DURATION) / 1_000L,
                            )
                        }
                    }
                } finally {
                    extractor.release()
                }
            }

            if (!hasAudio && !hasVideo) {
                throw IOException("The downloaded data is not playable audio or video.")
            }
            val kind = if (hasVideo) MediaKind.VIDEO else MediaKind.AUDIO
            if (requestedType == DownloadRequest.TYPE_AUDIO && kind != MediaKind.AUDIO) {
                throw IOException("Audio conversion did not produce an audio-only file.")
            }
            if (requestedType == DownloadRequest.TYPE_VIDEO && kind != MediaKind.VIDEO) {
                throw IOException("The selected link did not produce a video file.")
            }

            val metadataMime = if (retrieverReady) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            } else {
                null
            }
            val mime = normalizeMime(metadataMime ?: extractorMime, kind, file.extension)
            val duration = (if (retrieverReady) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            } else {
                null
            })?.toLongOrNull()?.coerceAtLeast(0L) ?: extractorDuration

            return MediaProbe(
                kind = kind,
                mimeType = mime,
                extension = extensionFor(mime, kind, file.extension),
                durationMs = duration,
                sizeBytes = file.length(),
            )
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("The downloaded file could not be recognized as playable media.", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publishToMediaStore(
        sourceFile: File,
        filename: String,
        probe: MediaProbe,
        source: YtDlpSourceInfo,
    ): Uri {
        val collection = if (probe.kind == MediaKind.AUDIO) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val directory = if (probe.kind == MediaKind.AUDIO) {
            Environment.DIRECTORY_MUSIC
        } else {
            Environment.DIRECTORY_MOVIES
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, probe.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Omni")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            if (probe.kind == MediaKind.AUDIO) {
                put(MediaStore.Audio.Media.TITLE, source.title)
                put(MediaStore.Audio.Media.ARTIST, source.artist)
                // MediaStore resolves artwork at album level. Giving every download the
                // same album makes Android reuse the first embedded thumbnail forever.
                // A stable per-track album identity keeps each downloaded cover distinct.
                put(MediaStore.Audio.Media.ALBUM, "Omni Download • ${source.title.take(96)}")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Android could not create the output file.")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                copyCancellable(sourceFile, output)
            } ?: throw IOException("Android could not write the output file.")
            val updated = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            if (updated <= 0) {
                throw IOException("Android could not finish publishing the downloaded file.")
            }
            return uri
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private suspend fun publishToAppStorage(
        sourceFile: File,
        filename: String,
        kind: MediaKind,
    ): Uri {
        val folderType = if (kind == MediaKind.AUDIO) {
            Environment.DIRECTORY_MUSIC
        } else {
            Environment.DIRECTORY_MOVIES
        }
        val root = context.getExternalFilesDir(folderType)
            ?: throw IOException("External app storage is unavailable.")
        val folder = File(root, "Omni")
        if (!folder.exists() && !folder.mkdirs()) {
            throw IOException("Omni could not create its download folder.")
        }
        val output = uniqueFile(folder, filename)
        output.outputStream().use { copyCancellable(sourceFile, it) }
        return Uri.fromFile(output)
    }

    private suspend fun copyCancellable(source: File, output: java.io.OutputStream) =
        withContext(Dispatchers.IO) {
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

    private fun normalizeMime(candidate: String?, kind: MediaKind, extension: String): String {
        val normalized = candidate?.substringBefore(';')?.trim()?.lowercase(Locale.US)
        if (normalized?.startsWith("audio/") == true || normalized?.startsWith("video/") == true) {
            return normalized
        }
        return when (extension.lowercase(Locale.US)) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> if (kind == MediaKind.AUDIO) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "ts", "m2ts" -> "video/mp2t"
            else -> if (kind == MediaKind.AUDIO) "audio/mpeg" else "video/mp4"
        }
    }

    private fun extensionFor(mimeType: String, kind: MediaKind, original: String): String =
        when (mimeType) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/mp4a-latm" -> "m4a"
            "audio/aac" -> "aac"
            "audio/flac" -> "flac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/webm" -> "webm"
            "audio/x-matroska" -> "mka"
            "audio/wav", "audio/x-wav" -> "wav"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "video/3gpp" -> "3gp"
            "video/quicktime" -> "mov"
            "video/x-msvideo" -> "avi"
            "video/mp2t" -> "ts"
            else -> original.lowercase(Locale.US).takeIf(String::isNotBlank)
                ?: if (kind == MediaKind.AUDIO) "mp3" else "mp4"
        }

    private fun safeFilename(stem: String, extension: String): String {
        val safeStem = stem
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
            .trim(' ', '.')
            .take(170)
            .ifBlank { "Omni_${System.currentTimeMillis()}" }
        return "$safeStem.$extension"
    }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        val stem = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 1
        while (candidate.exists()) {
            candidate = File(folder, "$stem ($index)$extension")
            index++
        }
        return candidate
    }
}
