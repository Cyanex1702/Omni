package com.omniplayer.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.omniplayer.app.data.AcousticMoodRepository
import com.omniplayer.app.download.YtDlpRuntime
import com.omniplayer.app.playback.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OmniPlayerApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var playbackController: PlaybackController
        private set
    val acousticMoodRepository by lazy { AcousticMoodRepository(this) }

    override fun onCreate() {
        super.onCreate()
        playbackController = PlaybackController(this)
        applicationScope.launch {
            // Warm the native runtime without blocking app startup. A download worker also
            // awaits this guarded initializer, so opening a shared link immediately is safe.
            runCatching { YtDlpRuntime.ensureInitialized(this@OmniPlayerApplication) }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .components { add(VideoFrameDecoder.Factory()) }
        .build()
}
