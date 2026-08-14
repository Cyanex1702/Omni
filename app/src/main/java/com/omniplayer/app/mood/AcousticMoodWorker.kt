package com.omniplayer.app.mood

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.omniplayer.app.OmniPlayerApplication
import com.omniplayer.app.data.MediaRepository
import com.omniplayer.app.model.MediaKind

data class AcousticAnalysisState(
    val running: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val failed: Int = 0,
)

class AcousticMoodWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OmniPlayerApplication
        val repository = app.acousticMoodRepository
        val songs = MediaRepository(applicationContext).loadAll().filter { it.kind == MediaKind.AUDIO }
        val force = inputData.getBoolean(KEY_FORCE, false)
        var completed = 0
        var failed = 0
        val analyzer = AcousticMoodAnalyzer(applicationContext)
        songs.forEach { song ->
            if (isStopped) return Result.failure(progressData(completed, songs.size, song.title, failed))
            val existing = repository.profiles.value[song.uri.toString()]
            if (!force && existing?.isCurrent(song) == true) {
                completed++
            } else {
                setProgress(progressData(completed, songs.size, song.title, failed))
                val profile = analyzer.analyze(song) { isStopped }
                if (profile == null) failed++ else repository.put(profile)
                completed++
            }
            setProgress(progressData(completed, songs.size, song.title, failed))
        }
        return Result.success(progressData(completed, songs.size, "", failed))
    }

    private fun progressData(done: Int, total: Int, title: String, failed: Int): Data = workDataOf(
        KEY_COMPLETED to done,
        KEY_TOTAL to total,
        KEY_TITLE to title.take(120),
        KEY_FAILED to failed,
    )

    companion object {
        const val UNIQUE_WORK = "optional_acoustic_mood_analysis"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_TITLE = "title"
        const val KEY_FAILED = "failed"
        private const val KEY_FORCE = "force"

        fun start(context: Context, force: Boolean) {
            runCatching {
                val request = OneTimeWorkRequestBuilder<AcousticMoodWorker>()
                    .setInputData(workDataOf(KEY_FORCE to force))
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
            }
        }
    }
}
