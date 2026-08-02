package com.omniplayer.app.download

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Initializes the bundled Python, yt-dlp, FFmpeg and aria2c runtimes exactly once.
 *
 * Native extraction is expensive on the first launch. Both the application warm-up and every
 * worker go through this mutex so a download can never race a half-extracted runtime.
 */
object YtDlpRuntime {
    private const val PREFS = "omni_ytdlp_runtime"
    private const val INSTALLED_BUNDLED_REVISION = "installed_bundled_revision"
    private const val LAST_UPDATE_ATTEMPT = "last_update_attempt_v2"
    private const val LAST_UPDATE_SUCCESS = "last_update_success"
    private const val BUNDLED_ENGINE_REVISION = "2026.07.04"
    private const val QUICKJS_LIBRARY = "libqjs.so"
    private const val UPDATE_INTERVAL_MS = 3L * 24L * 60L * 60L * 1_000L
    private const val UPDATE_RETRY_INTERVAL_MS = 15L * 60L * 1_000L
    private const val LIBRARY_PREFS = "youtubedl-android"
    private const val LIBRARY_VERSION = "dlpVersion"
    private const val LIBRARY_VERSION_NAME = "dlpVersionName"

    private val mutex = Mutex()
    private val engineLock = ReentrantReadWriteLock(true)

    @Volatile
    private var initialized = false

    suspend fun ensureInitialized(context: Context) = withContext(Dispatchers.IO) {
        if (initialized) return@withContext
        mutex.withLock {
            if (initialized) return@withLock
            val appContext = context.applicationContext
            val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val bundledRevisionChanged =
                preferences.getString(INSTALLED_BUNDLED_REVISION, null) != BUNDLED_ENGINE_REVISION
            engineLock.write {
                if (bundledRevisionChanged) {
                    removeExtractedYtDlp(appContext)
                    // The youtubedl-android updater keeps its own release tag. Clear it when the
                    // APK replaces the executable, otherwise it can mistake an older bundled file
                    // for the previously downloaded current release.
                    appContext.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .remove(LIBRARY_VERSION)
                        .remove(LIBRARY_VERSION_NAME)
                        .apply()
                }

                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                Aria2c.getInstance().init(appContext)
            }
            if (bundledRevisionChanged) {
                preferences.edit()
                    .putString(INSTALLED_BUNDLED_REVISION, BUNDLED_ENGINE_REVISION)
                    // Check the stable channel on first use. Failure is non-fatal and is throttled,
                    // while the bundled extractor remains available as an offline fallback.
                    .remove(LAST_UPDATE_ATTEMPT)
                    .remove(LAST_UPDATE_SUCCESS)
                    .apply()
            }
            initialized = true
        }
    }

    /**
     * Refreshes yt-dlp periodically because website extractors change independently of Omni.
     * An update failure is intentionally non-fatal; the bundled extractor is still attempted.
     */
    suspend fun updateIfDue(context: Context): Result<YoutubeDL.UpdateStatus?> =
        withContext(Dispatchers.IO) {
            ensureInitialized(context)
            mutex.withLock {
                val appContext = context.applicationContext
                val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val lastAttempt = preferences.getLong(LAST_UPDATE_ATTEMPT, 0L)
                val lastSuccess = preferences.getLong(LAST_UPDATE_SUCCESS, 0L)
                if (isRecent(now, lastSuccess, UPDATE_INTERVAL_MS) ||
                    isRecent(now, lastAttempt, UPDATE_RETRY_INTERVAL_MS)
                ) {
                    return@withLock Result.success(null)
                }

                preferences.edit().putLong(LAST_UPDATE_ATTEMPT, now).apply()
                val result = runCatching {
                    engineLock.write {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            appContext,
                            YoutubeDL.UpdateChannel.STABLE,
                        )
                    }
                }
                if (result.isSuccess) {
                    preferences.edit().putLong(LAST_UPDATE_SUCCESS, now).apply()
                }
                result
            }
        }

    /** Full path to Omni's ABI-matched QuickJS executable in the installed APK. */
    fun quickJsBinary(context: Context): File {
        val binary = File(context.applicationInfo.nativeLibraryDir, QUICKJS_LIBRARY)
        if (!binary.isFile || !binary.canExecute()) {
            throw IOException("Omni's YouTube JavaScript runtime is missing for this device.")
        }
        return binary
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    /**
     * Prevents the updater from replacing the yt-dlp zip application while one or more Python
     * processes may still be importing modules from it. Multiple downloads retain read permits.
     */
    fun <T> execute(block: () -> T): T = engineLock.read(block)

    private fun removeExtractedYtDlp(context: Context) {
        val directory = File(
            File(context.noBackupFilesDir, YoutubeDL.baseName),
            YoutubeDL.ytdlpDirName,
        )
        if (directory.exists() && !directory.deleteRecursively()) {
            throw IOException("Omni could not replace its outdated download engine.")
        }
    }

    private fun isRecent(now: Long, timestamp: Long, interval: Long): Boolean =
        timestamp > 0L && now >= timestamp && now - timestamp < interval
}
