package com.omniplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.omniplayer.app.model.OmniMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val connected: Boolean = false,
    val connecting: Boolean = true,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val artist: String = "",
    val current: OmniMedia? = null,
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<OmniMedia> = emptyList(),
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val playAsAudio: Boolean = false,
)

data class PlaybackTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

class PlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("omni_playback", Context.MODE_PRIVATE)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var currentQueue: List<OmniMedia> = emptyList()
    private var pendingPlay: PendingPlay? = null
    private var resumeEnabled = true
    private var gaplessEnabled = false
    private var equalizerState = EqualizerState()
    private var playAsAudio = false
    private var lastProgressPersistMs = 0L
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshState(player)

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                buffering = false,
                error = friendlyPlaybackError(error),
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshState(controller ?: return)
            persist()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshState(controller ?: return)
            persist()
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        _state.value = _state.value.copy(connecting = true, error = null)
        val token = SessionToken(appContext, ComponentName(appContext, OmniPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync().also { controllerFuture = it }
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(listener)
                        _state.value = _state.value.copy(connected = true, connecting = false, error = null)
                        sendGapless(mediaController)
                        sendEqualizer(mediaController)
                        pendingPlay?.also {
                            pendingPlay = null
                            performPlay(mediaController, it.media, it.queue, it.positionMs, it.playWhenReady)
                        } ?: refreshState(mediaController)
                    }
                    .onFailure { error ->
                        controllerFuture = null
                        _state.value = _state.value.copy(
                            connected = false,
                            connecting = false,
                            buffering = false,
                            error = "Player could not start: ${error.message ?: "connection failed"}",
                        )
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(
        media: OmniMedia,
        queue: List<OmniMedia>,
        positionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val playableQueue = queue.ifEmpty { listOf(media) }.distinctBy { it.uri.toString() }
        val player = controller
        if (player == null) {
            pendingPlay = PendingPlay(media, playableQueue, positionMs, playWhenReady)
            currentQueue = playableQueue
            _state.value = _state.value.copy(
                connecting = true,
                buffering = true,
                error = null,
                title = media.title,
                artist = media.artist,
                current = media,
                queue = playableQueue,
                currentIndex = playableQueue.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0),
            )
            connect()
            return
        }
        performPlay(player, media, playableQueue, positionMs, playWhenReady)
    }

    private fun performPlay(
        player: MediaController,
        media: OmniMedia,
        queue: List<OmniMedia>,
        positionMs: Long,
        playWhenReady: Boolean,
    ) {
        currentQueue = queue
        playAsAudio = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .build()
        val index = queue.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0)
        _state.value = _state.value.copy(buffering = true, error = null, current = media)
        player.setMediaItems(queue.map { it.toMediaItem() }, index, positionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = playWhenReady
        refreshState(player)
    }

    fun togglePlayPause() {
        val player = controller ?: run {
            _state.value.current?.let { play(it, _state.value.queue, _state.value.positionMs) }
            return
        }
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    fun retry() {
        val player = controller
        if (player == null) {
            _state.value.current?.let { play(it, _state.value.queue, _state.value.positionMs) }
            return
        }
        _state.value = _state.value.copy(error = null, buffering = true)
        player.prepare()
        player.play()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val duration = player.duration.takeUnless { it == C.TIME_UNSET || it <= 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun playIndex(index: Int) {
        val player = controller ?: return
        if (index !in currentQueue.indices) return
        player.seekTo(index, 0)
        player.play()
    }

    fun clearQueue() {
        pendingPlay = null
        currentQueue = emptyList()
        controller?.run {
            stop()
            clearMediaItems()
        }
        _state.value = PlaybackState(connected = controller != null, connecting = controller == null)
        prefs.edit().clear().apply()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
        controller?.let(::refreshState)
    }

    fun setRepeatMode(mode: Int) {
        controller?.repeatMode = mode
        controller?.let(::refreshState)
    }

    fun setSpeed(speed: Float) {
        controller?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2f))
        controller?.let(::refreshState)
    }

    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
        controller?.let(::refreshState)
    }

    fun setPlayAsAudio(enabled: Boolean) {
        val player = controller ?: return
        playAsAudio = enabled
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
            .build()
        refreshState(player)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun trackOptions(trackType: Int): List<PlaybackTrackOption> {
        val player = controller ?: return emptyList()
        return player.currentTracks.groups.flatMapIndexed { groupIndex, group ->
            if (group.type != trackType) return@flatMapIndexed emptyList()
            (0 until group.length).mapNotNull { trackIndex ->
                if (!group.isTrackSupported(trackIndex, true)) return@mapNotNull null
                val format = group.getTrackFormat(trackIndex)
                val fallback = when (trackType) {
                    C.TRACK_TYPE_AUDIO -> listOfNotNull(
                        format.language?.uppercase(),
                        format.channelCount.takeIf { it > 0 }?.let { "$it ch" },
                    ).joinToString(" • ").ifBlank { "Audio ${trackIndex + 1}" }
                    C.TRACK_TYPE_TEXT -> listOfNotNull(format.language?.uppercase(), format.sampleMimeType)
                        .joinToString(" • ").ifBlank { "Subtitle ${trackIndex + 1}" }
                    C.TRACK_TYPE_VIDEO -> listOfNotNull(
                        format.height.takeIf { it > 0 }?.let { "${it}p" },
                        format.bitrate.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1_000_000f) },
                    ).joinToString(" • ").ifBlank { "Video ${trackIndex + 1}" }
                    else -> "Track ${trackIndex + 1}"
                }
                PlaybackTrackOption(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = format.label?.takeIf(String::isNotBlank) ?: fallback,
                    selected = group.isTrackSelected(trackIndex),
                )
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun selectTrack(trackType: Int, option: PlaybackTrackOption?, disabled: Boolean = false) {
        val player = controller ?: return
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, disabled)
        if (option != null && !disabled) {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
        if (trackType == C.TRACK_TYPE_VIDEO) playAsAudio = disabled
        refreshState(player)
    }

    fun player(): Player? = controller

    fun refreshProgress() {
        controller?.let(::refreshState)
        val now = SystemClock.elapsedRealtime()
        if (_state.value.current != null && now - lastProgressPersistMs >= 5_000L) {
            lastProgressPersistMs = now
            persist()
        }
    }

    fun setResumeEnabled(enabled: Boolean) {
        resumeEnabled = enabled
        if (!enabled) prefs.edit().clear().apply()
    }

    fun restoreFrom(media: List<OmniMedia>) {
        if (!resumeEnabled || _state.value.current != null || pendingPlay != null) return
        val uri = prefs.getString(KEY_URI, null) ?: return
        val item = media.firstOrNull { it.uri.toString() == uri } ?: return
        play(
            item,
            media.filter { it.kind == item.kind },
            prefs.getLong(KEY_POSITION, 0L),
            playWhenReady = false,
        )
    }

    fun setGapless(enabled: Boolean) {
        gaplessEnabled = enabled
        controller?.let(::sendGapless)
    }

    fun applyEqualizer(state: EqualizerState) {
        equalizerState = state
        controller?.let(::sendEqualizer)
    }

    private fun sendGapless(player: MediaController) {
        sendCommand(
            player,
            PlaybackCommands.SET_GAPLESS,
            Bundle().apply { putBoolean(PlaybackCommands.ENABLED, gaplessEnabled) },
        )
    }

    private fun sendEqualizer(player: MediaController) {
        sendCommand(
            player,
            PlaybackCommands.SET_EQUALIZER,
            Bundle().apply {
                putBoolean(PlaybackCommands.ENABLED, equalizerState.enabled)
                putFloatArray(PlaybackCommands.GAINS, equalizerState.gainsDb.toFloatArray())
                putFloat(PlaybackCommands.BASS, equalizerState.bassBoost)
                putFloat(PlaybackCommands.VIRTUALIZER, equalizerState.virtualizer)
                putFloat(PlaybackCommands.REVERB, equalizerState.reverb)
            },
        )
    }

    fun setSleepTimer(durationMs: Long) {
        controller?.let { player ->
            sendCommand(
                player,
                if (durationMs > 0) PlaybackCommands.SET_SLEEP_TIMER else PlaybackCommands.CANCEL_SLEEP_TIMER,
                Bundle().apply { putLong(PlaybackCommands.DURATION, durationMs) },
            )
        }
    }

    private fun sendCommand(player: MediaController, action: String, extras: Bundle) {
        player.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), extras)
    }

    private fun refreshState(player: Player) {
        val index = player.currentMediaItemIndex
        val current = currentQueue.getOrNull(index)
        val duration = player.duration.takeUnless { it == C.TIME_UNSET || it < 0 }
            ?: current?.durationMs
            ?: 0L
        _state.value = PlaybackState(
            connected = true,
            connecting = false,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            error = if (player.playerError == null && player.playbackState != Player.STATE_IDLE) null else _state.value.error,
            title = player.mediaMetadata.title?.toString().orEmpty().ifBlank { current?.title.orEmpty() },
            artist = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { current?.artist.orEmpty() },
            current = current,
            currentIndex = index,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = duration.coerceAtLeast(0),
            queue = currentQueue,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            volume = player.volume,
            playAsAudio = playAsAudio,
        )
    }

    private fun persist() {
        if (!resumeEnabled) return
        val current = _state.value.current ?: return
        prefs.edit()
            .putString(KEY_URI, current.uri.toString())
            .putLong(KEY_POSITION, _state.value.positionMs)
            .apply()
    }

    private fun OmniMedia.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .apply { artworkUri?.let(::setArtworkUri) }
                .build(),
        )
        .build()

    private fun friendlyPlaybackError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The media file is missing or was moved."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Omni no longer has permission to read this file."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "This file is not a supported or valid audio/video format."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "The media server could not be reached."
        else -> error.localizedMessage ?: "Playback failed."
    }

    private data class PendingPlay(
        val media: OmniMedia,
        val queue: List<OmniMedia>,
        val positionMs: Long,
        val playWhenReady: Boolean,
    )

    companion object {
        private const val KEY_URI = "last_uri"
        private const val KEY_POSITION = "last_position"
    }
}

data class EqualizerState(
    val enabled: Boolean = false,
    val gainsDb: List<Float> = List(5) { 0f },
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f,
    val reverb: Float = 0f,
)
