package com.omniplayer.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.omniplayer.app.MainActivity

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class OmniPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var effects = EqualizerState()
    private var gapless = false
    private val handler = Handler(Looper.getMainLooper())
    private var sleepAction: Runnable? = null
    private var transitionAction: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachEffects(audioSessionId)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    cancelTransitionResume()
                    if (!gapless && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && player.playWhenReady) {
                        val transitionedIndex = player.currentMediaItemIndex
                        val transitionedMediaId = mediaItem?.mediaId
                        player.pause()
                        transitionAction = Runnable {
                            transitionAction = null
                            if (
                                player.mediaItemCount > 0 &&
                                player.currentMediaItemIndex == transitionedIndex &&
                                player.currentMediaItem?.mediaId == transitionedMediaId
                            ) {
                                player.play()
                            }
                        }.also { handler.postDelayed(it, 250L) }
                    }
                }
            },
        )

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(activityIntent)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        sleepAction?.let(handler::removeCallbacks)
        cancelTransitionResume()
        releaseEffects()
        mediaSession?.release()
        player.release()
        mediaSession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(SessionCommand(PlaybackCommands.SET_EQUALIZER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.SET_GAPLESS, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.SET_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(PlaybackCommands.CANCEL_SLEEP_TIMER, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                PlaybackCommands.SET_EQUALIZER -> {
                    effects = EqualizerState(
                        enabled = args.getBoolean(PlaybackCommands.ENABLED),
                        gainsDb = args.getFloatArray(PlaybackCommands.GAINS)?.toList() ?: List(5) { 0f },
                        bassBoost = args.getFloat(PlaybackCommands.BASS),
                        virtualizer = args.getFloat(PlaybackCommands.VIRTUALIZER),
                        reverb = args.getFloat(PlaybackCommands.REVERB),
                    )
                    applyEffects()
                }
                PlaybackCommands.SET_GAPLESS -> gapless = args.getBoolean(PlaybackCommands.ENABLED, false)
                PlaybackCommands.SET_SLEEP_TIMER -> scheduleSleep(args.getLong(PlaybackCommands.DURATION))
                PlaybackCommands.CANCEL_SLEEP_TIMER -> scheduleSleep(0L)
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlayerInteractionFinished(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            playerCommands: Player.Commands,
        ) {
            // Any controller command during the deliberate inter-track gap wins over the pending
            // automatic resume. This prevents a pause/seek/skip request from being undone 250 ms
            // later by the service callback.
            cancelTransitionResume()
        }
    }

    private fun cancelTransitionResume() {
        transitionAction?.let(handler::removeCallbacks)
        transitionAction = null
    }

    private fun scheduleSleep(durationMs: Long) {
        sleepAction?.let(handler::removeCallbacks)
        sleepAction = null
        if (durationMs > 0) {
            sleepAction = Runnable { player.pause() }.also { handler.postDelayed(it, durationMs) }
        }
    }

    private fun attachEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        releaseEffects()
        runCatching { equalizer = Equalizer(0, audioSessionId) }
        runCatching { bassBoost = BassBoost(0, audioSessionId) }
        runCatching { virtualizer = Virtualizer(0, audioSessionId) }
        runCatching {
            reverb = PresetReverb(0, 0).also {
                player.setAuxEffectInfo(AuxEffectInfo(it.id, 0.5f))
            }
        }
        applyEffects()
    }

    private fun applyEffects() {
        equalizer?.runCatching {
            enabled = effects.enabled
            val minimum = bandLevelRange[0].toInt()
            val maximum = bandLevelRange[1].toInt()
            val bands = numberOfBands.toInt()
            for (band in 0 until bands) {
                val sourceIndex = if (bands <= 1) {
                    0
                } else {
                    ((band.toFloat() / (bands - 1)) * (effects.gainsDb.size - 1)).toInt()
                }
                val level = (effects.gainsDb.getOrElse(sourceIndex) { 0f } * 100f)
                    .toInt()
                    .coerceIn(minimum, maximum)
                    .toShort()
                setBandLevel(band.toShort(), level)
            }
        }
        bassBoost?.runCatching {
            enabled = effects.enabled && effects.bassBoost > 0f
            setStrength((effects.bassBoost.coerceIn(0f, 1f) * 1_000f).toInt().toShort())
        }
        virtualizer?.runCatching {
            enabled = effects.enabled && effects.virtualizer > 0f
            setStrength((effects.virtualizer.coerceIn(0f, 1f) * 1_000f).toInt().toShort())
        }
        reverb?.runCatching {
            enabled = effects.enabled && effects.reverb > 0f
            preset = when {
                effects.reverb > 0.75f -> PresetReverb.PRESET_LARGEHALL
                effects.reverb > 0.50f -> PresetReverb.PRESET_MEDIUMHALL
                effects.reverb > 0.25f -> PresetReverb.PRESET_LARGEROOM
                else -> PresetReverb.PRESET_SMALLROOM
            }
        }
    }

    private fun releaseEffects() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
        virtualizer?.release()
        virtualizer = null
        reverb?.release()
        reverb = null
    }
}
