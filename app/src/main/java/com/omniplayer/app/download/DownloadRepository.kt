package com.omniplayer.app.download

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import java.util.concurrent.TimeUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class DownloadJob(
    val id: UUID,
    val name: String,
    val mediaTitle: String,
    val artist: String,
    val thumbnail: String?,
    val sourceUrl: String?,
    val requestedType: String?,
    val quality: String?,
    val stage: String,
    val progress: Int,
    val bytes: Long,
    val total: Long,
    val speedBytesPerSecond: Long,
    val etaSeconds: Long,
    val previewPath: String?,
    val previewProtocol: String?,
    val state: WorkInfo.State,
    val outputUri: String?,
    val error: String?,
    val errorDetail: String?,
    val mimeType: String?,
    val kind: MediaKind?,
    val durationMs: Long,
    val createdAt: Long,
)

fun DownloadJob.asMedia(): OmniMedia? {
    if (state != WorkInfo.State.SUCCEEDED) return null
    val uri = outputUri?.let(Uri::parse) ?: return null
    val mediaKind = kind ?: return null
    return OmniMedia(
        id = uri.toString().hashCode().toLong(),
        title = mediaTitle.ifBlank { name.substringBeforeLast('.') },
        artist = artist.ifBlank {
            if (mediaKind == MediaKind.AUDIO) "Omni download" else "Local video"
        },
        album = if (mediaKind == MediaKind.AUDIO) {
            "Omni Download • ${mediaTitle.ifBlank { name.substringBeforeLast('.') }.take(96)}"
        } else {
            "Omni Downloads"
        },
        durationMs = durationMs,
        uri = uri,
        sizeBytes = if (total > 0) total else bytes,
        kind = mediaKind,
        dateAddedSeconds = createdAt / 1_000L,
        mimeType = mimeType,
        artworkUri = thumbnail?.let(Uri::parse).takeIf { mediaKind == MediaKind.AUDIO }
            ?: if (mediaKind == MediaKind.VIDEO) uri else null,
        displayName = name,
    )
}

class DownloadRepository(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val historyPreferences =
        context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
    private val hiddenIds = MutableStateFlow(
        historyPreferences.getString(HIDDEN_IDS, null)
            .orEmpty()
            .split('|')
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            .toSet()
    )

    val jobs: Flow<List<DownloadJob>> =
        combine(workManager.getWorkInfosByTagFlow(DownloadWorker.TAG), hiddenIds) { infos, hidden ->
            infos.filterNot { it.id in hidden }.sortedByDescending(::createdAt).map { info ->
                val output = info.outputData
                val progress = info.progress
                fun text(key: String): String? =
                    output.getString(key) ?: progress.getString(key)
                fun number(key: String): Long =
                    output.getLong(key, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }
                        ?: progress.getLong(key, 0L)

                DownloadJob(
                    id = info.id,
                    name = text(DownloadWorker.KEY_FILENAME) ?: "Media download",
                    mediaTitle = text(DownloadWorker.KEY_MEDIA_TITLE).orEmpty(),
                    artist = text(DownloadWorker.KEY_ARTIST).orEmpty(),
                    thumbnail = text(DownloadWorker.KEY_THUMBNAIL),
                    sourceUrl = text(DownloadWorker.KEY_SOURCE_URL),
                    requestedType = text(DownloadWorker.KEY_TYPE),
                    quality = text(DownloadWorker.KEY_QUALITY),
                    stage = text(DownloadWorker.KEY_STAGE) ?: when (info.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Queued"
                        WorkInfo.State.RUNNING -> "Preparing"
                        WorkInfo.State.SUCCEEDED -> "Completed"
                        WorkInfo.State.FAILED -> "Failed"
                        WorkInfo.State.CANCELLED -> "Canceled"
                    },
                    progress = if (info.state == WorkInfo.State.SUCCEEDED) {
                        100
                    } else {
                        progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                    },
                    bytes = number(DownloadWorker.KEY_BYTES),
                    total = number(DownloadWorker.KEY_TOTAL),
                    speedBytesPerSecond = number(DownloadWorker.KEY_SPEED),
                    etaSeconds = number(DownloadWorker.KEY_ETA),
                    previewPath = text(DownloadWorker.KEY_PREVIEW_PATH),
                    previewProtocol = text(DownloadWorker.KEY_PREVIEW_PROTOCOL),
                    state = info.state,
                    outputUri = output.getString(DownloadWorker.KEY_OUTPUT_URI),
                    error = output.getString(DownloadWorker.KEY_ERROR),
                    errorDetail = output.getString(DownloadWorker.KEY_ERROR_DETAIL),
                    mimeType = output.getString(DownloadWorker.KEY_MIME),
                    kind = output.getString(DownloadWorker.KEY_KIND)
                        ?.let { runCatching { MediaKind.valueOf(it) }.getOrNull() },
                    durationMs = output.getLong(DownloadWorker.KEY_DURATION, 0L),
                    createdAt = createdAt(info),
                )
            }
        }

    fun enqueue(
        url: String,
        type: String,
        quality: String,
        wifiOnly: Boolean,
        simultaneousDownloads: Int,
    ): UUID {
        val download = DownloadRequest.create(url, type, quality)
        val concurrency = simultaneousDownloads.coerceIn(1, 3)

        val created = System.currentTimeMillis()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_URL, download.url)
                    .putString(DownloadWorker.KEY_TYPE, download.type)
                    .putString(DownloadWorker.KEY_QUALITY, download.quality)
                    .putInt(DownloadWorker.KEY_CONCURRENCY, concurrency)
                    .build()
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20L, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .addTag("$CREATED_TAG$created")
            .build()

        // Jobs are independent so a failed/cancelled request cannot poison later downloads.
        // DownloadConcurrencyGate enforces the user's 1-3 task limit inside the app process.
        workManager.enqueue(request)
        return request.id
    }

    fun cancel(id: UUID) = workManager.cancelWorkById(id)

    fun clearFinished(jobs: Collection<DownloadJob>) {
        val additions = jobs.asSequence()
            .filter { it.state.isFinished }
            .map(DownloadJob::id)
            .toSet()
        if (additions.isEmpty()) return
        val updated = (hiddenIds.value + additions).toList().takeLast(MAX_HIDDEN_IDS).toSet()
        historyPreferences.edit()
            .putString(HIDDEN_IDS, updated.joinToString("|"))
            .apply()
        hiddenIds.value = updated
    }

    private fun createdAt(info: WorkInfo): Long =
        info.tags.firstOrNull { it.startsWith(CREATED_TAG) }
            ?.substringAfter(CREATED_TAG)
            ?.toLongOrNull()
            ?: 0L

    companion object {
        private const val CREATED_TAG = "omni.created."
        private const val HISTORY_PREFS = "omni_download_history"
        private const val HIDDEN_IDS = "hidden_ids"
        private const val MAX_HIDDEN_IDS = 500
    }
}
