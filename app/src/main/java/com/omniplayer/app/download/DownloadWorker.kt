package com.omniplayer.app.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val processId = "omni-$id"
    private val temporaryDirectory = java.io.File(applicationContext.cacheDir, "omni-ytdlp/$id")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val request = try {
            DownloadRequest.create(
                inputData.getString(KEY_URL).orEmpty(),
                inputData.getString(KEY_TYPE).orEmpty(),
                inputData.getString(KEY_QUALITY).orEmpty(),
            )
        } catch (error: IllegalArgumentException) {
            return@withContext failure(error.message ?: "The download request is invalid.")
        }
        val concurrency = inputData.getInt(KEY_CONCURRENCY, 2).coerceIn(1, 3)

        temporaryDirectory.deleteRecursively()
        try {
            updateStage(
                stage = "Waiting for a download slot",
                progress = 0,
                title = "Media download",
                request = request,
            )
            DownloadConcurrencyGate.withPermit(concurrency) {
                performDownload(request)
            }
        } catch (cancelled: CancellationException) {
            YtDlpRuntime.cancel(processId)
            throw cancelled
        } finally {
            temporaryDirectory.deleteRecursively()
        }
    }

    private suspend fun performDownload(request: DownloadRequest): Result {
        val engine = YtDlpEngine(applicationContext)
        val mediaStore = DownloadedMediaStore(applicationContext)
        var lastProgress = 0
        var lastTotal = 0L
        var lastBytes = 0L
        var lastSpeed = 0L
        var lastEta = 0L
        var lastPreviewPath: String? = null
        var lastPreviewProtocol: String? = null
        var lastPublishAt = 0L
        var lastForegroundAt = 0L
        var phaseSignature = ""
        var completedPhaseBytes = 0L

        return try {
            engine.prepareRuntime { stage ->
                updateStage(
                    stage = stage,
                    progress = 0,
                    title = "Media download",
                    request = request,
                )
            }

            val downloadResult = runInterruptible {
                engine.download(
                    request = request,
                    outputDirectory = temporaryDirectory,
                    processId = processId,
                ) { rawProgress, etaSeconds, outputLine ->
                    if (outputLine.startsWith("OMNI_STAGE|")) {
                        val stage = outputLine.substringAfter('|')
                        val data = progressData(
                            stage, lastProgress, "Media download", request,
                            lastBytes, lastTotal, speed = lastSpeed, eta = lastEta,
                            previewPath = lastPreviewPath,
                            previewProtocol = lastPreviewProtocol,
                        )
                        runCatching { setProgressAsync(data).get(10, TimeUnit.SECONDS) }
                        return@download
                    }

                    val rawPercent = (rawProgress.coerceIn(0f, 100f) * 0.94f).roundToInt()
                    val parsed = DownloadProgressParser.parse(outputLine, rawPercent)
                    if (!parsed.structured && rawProgress < 0f) return@download

                    val signature = "${parsed.hasVideo}:${parsed.hasAudio}"
                    if (
                        parsed.structured && phaseSignature == "true:false" &&
                        signature == "false:true"
                    ) {
                        completedPhaseBytes = maxOf(lastBytes, lastTotal)
                    }
                    if (parsed.structured) phaseSignature = signature

                    val phasePercent = if (parsed.structured) parsed.percent else rawPercent
                    val reported = when {
                        request.isAudio -> phasePercent * 94 / 100
                        parsed.hasVideo && !parsed.hasAudio -> phasePercent * 80 / 100
                        !parsed.hasVideo && parsed.hasAudio && completedPhaseBytes > 0L ->
                            80 + phasePercent * 14 / 100
                        else -> phasePercent * 94 / 100
                    }.coerceIn(0, 94)
                    val newProgress = maxOf(lastProgress, reported)
                    val parsedEta = parsed.etaSeconds.takeIf { it > 0L } ?: etaSeconds.coerceAtLeast(0L)
                    val newBytes = if (parsed.bytes > 0L) completedPhaseBytes + parsed.bytes else lastBytes
                    val newTotal = if (parsed.totalBytes > 0L) {
                        completedPhaseBytes + parsed.totalBytes
                    } else {
                        lastTotal
                    }
                    val previewPath = if (!request.isAudio && parsed.hasVideo) {
                        safePreviewPath(parsed.filename) ?: lastPreviewPath
                    } else {
                        lastPreviewPath
                    }
                    val now = System.currentTimeMillis()
                    val shouldPublish = newProgress != lastProgress ||
                        now - lastPublishAt >= PROGRESS_UPDATE_INTERVAL_MS ||
                        previewPath != lastPreviewPath
                    lastProgress = newProgress
                    lastBytes = newBytes
                    lastTotal = newTotal
                    lastSpeed = parsed.speedBytesPerSecond.takeIf { it > 0L } ?: lastSpeed
                    lastEta = parsedEta
                    lastPreviewPath = previewPath
                    if (previewPath != null && parsed.protocol.isNotBlank()) {
                        lastPreviewProtocol = parsed.protocol
                    }
                    if (!shouldPublish) return@download

                    val stage = when {
                        parsedEta > 0L -> "Downloading ${formatEta(parsedEta)} left"
                        parsed.hasVideo && !parsed.hasAudio -> "Downloading video"
                        !parsed.hasVideo && parsed.hasAudio -> "Downloading audio"
                        else -> "Downloading media"
                    }
                    val data = progressData(
                        stage, lastProgress, parsed.title.ifBlank { "Media download" }, request,
                        lastBytes, lastTotal, speed = lastSpeed, eta = lastEta,
                        previewPath = lastPreviewPath,
                        previewProtocol = lastPreviewProtocol,
                    )
                    runCatching { setProgressAsync(data).get(10, TimeUnit.SECONDS) }
                    lastPublishAt = now
                    if (now - lastForegroundAt >= FOREGROUND_UPDATE_INTERVAL_MS) {
                        runCatching {
                            setForegroundAsync(createForegroundInfo(lastProgress, stage))
                                .get(10, TimeUnit.SECONDS)
                        }
                        lastForegroundAt = now
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            val source = downloadResult.source
            updateStage(
                stage = "Validating finished media",
                progress = 95,
                title = source.title,
                request = request,
                total = source.estimatedBytes,
                artist = source.artist,
                thumbnail = source.thumbnailUrl,
            )
            val validated = mediaStore.validate(
                candidates = downloadResult.files,
                request = request,
                fallbackDurationMs = source.durationMs,
            )

            updateStage(
                stage = "Saving to Omni",
                progress = 98,
                title = source.title,
                request = request,
                bytes = validated.file.length(),
                total = validated.file.length(),
                artist = source.artist,
                thumbnail = source.thumbnailUrl,
            )
            val (outputUri, filename) = mediaStore.publish(validated, source)

            Result.success(
                Data.Builder()
                    .putString(KEY_OUTPUT_URI, outputUri.toString())
                    .putString(KEY_FILENAME, filename)
                    .putString(KEY_MEDIA_TITLE, source.title)
                    .putString(KEY_ARTIST, source.artist)
                    .putString(KEY_THUMBNAIL, source.thumbnailUrl)
                    .putString(KEY_SOURCE_URL, request.url)
                    .putString(KEY_EXTRACTOR, source.extractor)
                    .putString(KEY_MIME, validated.probe.mimeType)
                    .putString(KEY_KIND, validated.probe.kind.name)
                    .putString(KEY_TYPE, request.type)
                    .putString(KEY_QUALITY, request.quality)
                    .putString(KEY_STAGE, "Completed")
                    .putLong(KEY_DURATION, validated.probe.durationMs)
                    .putLong(KEY_BYTES, validated.probe.sizeBytes)
                    .putLong(KEY_TOTAL, validated.probe.sizeBytes)
                    .putInt(KEY_PROGRESS, 100)
                    .build(),
            )
        } catch (cancelled: CancellationException) {
            YtDlpRuntime.cancel(processId)
            throw cancelled
        } catch (cancelled: YoutubeDL.CanceledException) {
            failure("Download canceled.")
        } catch (error: Exception) {
            val details = DownloadFailureClassifier.classify(error, request.url)
            if (details.retryable && runAttemptCount < MAX_AUTO_RETRIES) {
                updateStage(
                    stage = "Temporary problem • retry ${runAttemptCount + 2} of ${MAX_AUTO_RETRIES + 1}",
                    progress = lastProgress.coerceAtMost(94),
                    title = "Media download",
                    request = request,
                    total = lastTotal,
                    speed = lastSpeed,
                    eta = lastEta,
                    previewPath = lastPreviewPath,
                )
                Result.retry()
            } else {
                failure(details.message, details.diagnostic)
            }
        }
    }

    private suspend fun updateStage(
        stage: String,
        progress: Int,
        title: String,
        request: DownloadRequest,
        bytes: Long = 0L,
        total: Long = 0L,
        artist: String = "",
        thumbnail: String? = null,
        speed: Long = 0L,
        eta: Long = 0L,
        previewPath: String? = null,
        previewProtocol: String? = null,
    ) {
        setProgress(
            progressData(
                stage = stage,
                progress = progress,
                title = title,
                request = request,
                bytes = bytes,
                total = total,
                artist = artist,
                thumbnail = thumbnail,
                speed = speed,
                eta = eta,
                previewPath = previewPath,
                previewProtocol = previewProtocol,
            )
        )
        setForeground(createForegroundInfo(progress, "$stage • $title"))
    }

    private fun progressData(
        stage: String,
        progress: Int,
        title: String,
        request: DownloadRequest,
        bytes: Long = 0L,
        total: Long = 0L,
        artist: String = "",
        thumbnail: String? = null,
        speed: Long = 0L,
        eta: Long = 0L,
        previewPath: String? = null,
        previewProtocol: String? = null,
    ): Data = Data.Builder()
        .putString(KEY_STAGE, stage)
        .putString(KEY_FILENAME, title)
        .putString(KEY_MEDIA_TITLE, title)
        .putString(KEY_ARTIST, artist)
        .putString(KEY_THUMBNAIL, thumbnail)
        .putString(KEY_SOURCE_URL, request.url)
        .putString(KEY_TYPE, request.type)
        .putString(KEY_QUALITY, request.quality)
        .putInt(KEY_PROGRESS, progress.coerceIn(0, 100))
        .putLong(KEY_BYTES, bytes.coerceAtLeast(0L))
        .putLong(KEY_TOTAL, total.coerceAtLeast(0L))
        .putLong(KEY_SPEED, speed.coerceAtLeast(0L))
        .putLong(KEY_ETA, eta.coerceAtLeast(0L))
        .putString(KEY_PREVIEW_PATH, previewPath)
        .putString(KEY_PREVIEW_PROTOCOL, previewProtocol)
        .build()

    private fun safePreviewPath(reportedPath: String?): String? {
        val raw = reportedPath?.takeIf(String::isNotBlank) ?: return null
        val reported = File(raw).let { if (it.isAbsolute) it else File(temporaryDirectory, raw) }
        val candidates = listOf(reported, File("${reported.absolutePath}.part"))
        val root = runCatching { temporaryDirectory.canonicalFile }.getOrNull() ?: return null
        return candidates.firstOrNull { candidate ->
            val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return@firstOrNull false
            canonical.path.startsWith(root.path + File.separator) && canonical.isFile && canonical.length() > 0L
        }?.absolutePath
    }

    private fun failure(message: String, diagnostic: String = message): Result = Result.failure(
        Data.Builder()
            .putString(KEY_ERROR, message)
            .putString(KEY_ERROR_DETAIL, diagnostic.takeLast(1_800))
            .putString(KEY_STAGE, "Failed")
            .putString(KEY_FILENAME, inputData.getString(KEY_URL) ?: "Media download")
            .putString(KEY_SOURCE_URL, inputData.getString(KEY_URL))
            .putString(KEY_TYPE, inputData.getString(KEY_TYPE))
            .putString(KEY_QUALITY, inputData.getString(KEY_QUALITY))
            .build(),
    )

    private fun createForegroundInfo(progress: Int, title: String): ForegroundInfo {
        val notification = DownloadNotifications.build(
            applicationContext,
            id.toString(),
            title,
            progress,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    companion object {
        const val TAG = "omni_download"
        const val KEY_URL = "url"
        const val KEY_TYPE = "type"
        const val KEY_QUALITY = "quality"
        const val KEY_CONCURRENCY = "concurrency"
        const val KEY_STAGE = "stage"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_ETA = "eta"
        const val KEY_PREVIEW_PATH = "preview_path"
        const val KEY_PREVIEW_PROTOCOL = "preview_protocol"
        const val KEY_FILENAME = "filename"
        const val KEY_MEDIA_TITLE = "media_title"
        const val KEY_ARTIST = "artist"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_SOURCE_URL = "source_url"
        const val KEY_EXTRACTOR = "extractor"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_DETAIL = "error_detail"
        const val KEY_MIME = "mime"
        const val KEY_KIND = "kind"
        const val KEY_DURATION = "duration"
        const val TYPE_AUDIO = DownloadRequest.TYPE_AUDIO
        const val TYPE_VIDEO = DownloadRequest.TYPE_VIDEO

        private const val MAX_AUTO_RETRIES = 2
        private const val PROGRESS_UPDATE_INTERVAL_MS = 300L
        private const val FOREGROUND_UPDATE_INTERVAL_MS = 1_000L

        private fun formatEta(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val minutes = safe / 60L
            val remainder = safe % 60L
            return if (minutes > 0L) "${minutes}m ${remainder}s" else "${remainder}s"
        }
    }
}
