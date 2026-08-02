package com.omniplayer.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omniplayer.app.data.MediaRepository
import com.omniplayer.app.data.MediaToolsRepository
import com.omniplayer.app.data.OmniSettings
import com.omniplayer.app.data.PreferencesRepository
import com.omniplayer.app.download.DownloadJob
import com.omniplayer.app.download.DownloadRepository
import com.omniplayer.app.download.asMedia
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.playback.EqualizerState
import com.omniplayer.app.playback.PlaybackController
import com.omniplayer.app.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OmniPlayerApplication
    private val mediaRepository = MediaRepository(application)
    private val mediaToolsRepository = MediaToolsRepository(application)
    private val preferencesRepository = PreferencesRepository(application)
    private val downloadRepository = DownloadRepository(application)
    val playbackController: PlaybackController = app.playbackController

    private val _media = MutableStateFlow<List<OmniMedia>>(emptyList())
    val media: StateFlow<List<OmniMedia>> = _media.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val playback: StateFlow<PlaybackState> = playbackController.state
    val settings: StateFlow<OmniSettings> = preferencesRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OmniSettings(),
    )
    val favoriteUris: StateFlow<Set<String>> = preferencesRepository.favorites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet(),
    )
    val recentUris: StateFlow<List<String>> = preferencesRepository.recentUris.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val downloads: StateFlow<List<DownloadJob>> = downloadRepository.jobs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _equalizer = MutableStateFlow(EqualizerState())
    val equalizer: StateFlow<EqualizerState> = _equalizer.asStateFlow()
    private val _mediaToolState = MutableStateFlow(MediaToolState())
    val mediaToolState: StateFlow<MediaToolState> = _mediaToolState.asStateFlow()

    init {
        playbackController.connect()
        refreshMedia()
        viewModelScope.launch {
            preferencesRepository.settings.collectLatest { current ->
                playbackController.setResumeEnabled(current.resumePlayback)
                playbackController.setGapless(current.gaplessPlayback)
            }
        }
        viewModelScope.launch {
            downloadRepository.jobs
                .map { jobs -> jobs.count { it.asMedia() != null } }
                .distinctUntilChanged()
                .collectLatest { completed ->
                    if (completed > 0) refreshMedia()
                }
        }
        viewModelScope.launch {
            while (isActive) {
                playbackController.refreshProgress()
                delay(500)
            }
        }
    }

    fun refreshMedia() {
        viewModelScope.launch {
            _loading.value = true
            _media.value = mediaRepository.loadAll()
            playbackController.restoreFrom(_media.value)
            _loading.value = false
        }
    }

    fun setQuery(value: String) { _query.value = value }

    fun play(media: OmniMedia) {
        val sameKind = _media.value.filter { it.kind == media.kind }
        playbackController.play(media, sameKind)
        viewModelScope.launch { preferencesRepository.markRecent(media.uri.toString()) }
    }

    fun playQueue(media: OmniMedia, queue: List<OmniMedia>) {
        playbackController.play(media, queue)
        viewModelScope.launch { preferencesRepository.markRecent(media.uri.toString()) }
    }

    fun playDownloaded(job: DownloadJob): Boolean {
        val selected = job.asMedia() ?: return false
        val queue = downloads.value.mapNotNull(DownloadJob::asMedia).filter { it.kind == selected.kind }
        playQueue(selected, queue.ifEmpty { listOf(selected) })
        return true
    }

    fun toggleFavorite(media: OmniMedia) {
        viewModelScope.launch { preferencesRepository.toggleFavorite(media.uri.toString()) }
    }

    fun enqueueDownload(url: String, type: String, quality: String): UUID =
        downloadRepository.enqueue(
            url,
            type,
            quality,
            settings.value.wifiOnly,
            settings.value.simultaneousDownloads,
        )

    fun cancelDownload(id: UUID) = downloadRepository.cancel(id)
    fun clearFinishedDownloads() = downloadRepository.clearFinished(downloads.value)

    fun retryDownload(job: DownloadJob): Boolean {
        val source = job.sourceUrl ?: return false
        val type = job.requestedType ?: return false
        enqueueDownload(
            source,
            type,
            job.quality ?: if (type == com.omniplayer.app.download.DownloadWorker.TYPE_AUDIO) "320" else "1080",
        )
        return true
    }

    fun setWifiOnly(value: Boolean) { viewModelScope.launch { preferencesRepository.setWifiOnly(value) } }
    fun setResumePlayback(value: Boolean) { viewModelScope.launch { preferencesRepository.setResumePlayback(value) } }
    fun setGaplessPlayback(value: Boolean) { viewModelScope.launch { preferencesRepository.setGaplessPlayback(value) } }
    fun setTheme(value: String) { viewModelScope.launch { preferencesRepository.setTheme(value) } }
    fun setPlayerAppearance(value: String) {
        viewModelScope.launch { preferencesRepository.setPlayerAppearance(value) }
    }
    fun setSimultaneousDownloads(value: Int) {
        viewModelScope.launch { preferencesRepository.setSimultaneousDownloads(value) }
    }

    fun setEqualizer(value: EqualizerState) {
        _equalizer.value = value
        playbackController.applyEqualizer(value)
    }

    fun setSleepTimer(durationMs: Long) = playbackController.setSleepTimer(durationMs)

    fun extractVideoAudio(video: OmniMedia) {
        if (_mediaToolState.value.running) return
        viewModelScope.launch {
            _mediaToolState.value = MediaToolState(running = true)
            try {
                val result = mediaToolsRepository.extractAudio(video)
                _mediaToolState.value = MediaToolState(message = result.message)
                refreshMedia()
            } catch (error: Exception) {
                _mediaToolState.value = MediaToolState(error = error.message ?: "Audio extraction failed.")
            }
        }
    }

    fun trimAudio(audio: OmniMedia, startMs: Long, endMs: Long) {
        if (_mediaToolState.value.running) return
        viewModelScope.launch {
            _mediaToolState.value = MediaToolState(running = true)
            try {
                val result = mediaToolsRepository.trimAudio(audio, startMs, endMs)
                _mediaToolState.value = MediaToolState(message = result.message)
                refreshMedia()
            } catch (error: Exception) {
                _mediaToolState.value = MediaToolState(error = error.message ?: "Ringtone clip creation failed.")
            }
        }
    }

    fun clearMediaToolStatus() { _mediaToolState.value = MediaToolState() }

    fun currentMedia(): OmniMedia? {
        val state = playback.value
        return state.queue.getOrNull(state.currentIndex)
    }

    fun favoriteMedia(): List<OmniMedia> = media.value.filter { it.uri.toString() in favoriteUris.value }
    fun audioMedia(): List<OmniMedia> = media.value.filter { it.kind == MediaKind.AUDIO }
    fun videoMedia(): List<OmniMedia> = media.value.filter { it.kind == MediaKind.VIDEO }

    fun recentMedia(): List<OmniMedia> {
        val byUri = media.value.associateBy { it.uri.toString() }
        return recentUris.value.mapNotNull(byUri::get)
    }
}

data class MediaToolState(
    val running: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
