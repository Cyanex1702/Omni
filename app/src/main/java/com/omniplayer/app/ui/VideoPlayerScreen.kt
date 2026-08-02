package com.omniplayer.app.ui

import android.app.Activity
import android.app.MediaRouteButton
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.media.MediaRouter
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.playback.PlaybackController
import com.omniplayer.app.playback.PlaybackState
import com.omniplayer.app.playback.PlaybackTrackOption
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class VideoDialog {
    SPEED, SUBTITLES, SUBTITLE_STYLE, AUDIO_TRACK, QUALITY, DISPLAY, LEVELS
}

@androidx.annotation.OptIn(UnstableApi::class)
private enum class VideoDisplayMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

private enum class VideoGesture { SEEK, BRIGHTNESS, VOLUME }

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: PlaybackState,
    current: OmniMedia?,
    controller: PlaybackController,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onPlayAsAudio: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<VideoDialog?>(null) }
    var displayMode by remember { mutableStateOf(VideoDisplayMode.FIT) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var draggingSlider by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.windowBrightness() ?: 0.55f) }
    var subtitleSize by remember { mutableFloatStateOf(18f) }
    var subtitleBackground by remember { mutableStateOf(true) }
    var gestureMessage by remember { mutableStateOf<String?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    val immersive = isLandscape || fullscreen

    LaunchedEffect(state.positionMs, draggingSlider) {
        if (!draggingSlider) seekPosition = state.positionMs.toFloat()
    }
    LaunchedEffect(controlsVisible, state.playing, locked) {
        if (controlsVisible && state.playing && !locked) {
            delay(3_200)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureMessage) {
        if (gestureMessage != null) {
            delay(900)
            gestureMessage = null
        }
    }

    DisposableEffect(immersive, activity) {
        val window = activity?.window
        val view = window?.decorView
        if (window != null && view != null) {
            val insets = WindowCompat.getInsetsController(window, view)
            if (immersive) {
                insets.hide(WindowInsetsCompat.Type.systemBars())
                insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insets.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (window != null && view != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    VideoOptionsDialog(
        dialog = dialog,
        controller = controller,
        state = state,
        displayMode = displayMode,
        brightness = brightness,
        subtitleSize = subtitleSize,
        subtitleBackground = subtitleBackground,
        onDismiss = { dialog = null },
        onDisplayMode = { displayMode = it },
        onBrightness = {
            brightness = it
            activity?.setWindowBrightness(it)
        },
        onSubtitleSize = { subtitleSize = it },
        onSubtitleBackground = { subtitleBackground = it },
        onOpenSubtitleStyle = { dialog = VideoDialog.SUBTITLE_STYLE },
    )

    val canvas: @Composable (Modifier) -> Unit = { modifier ->
        VideoCanvas(
            modifier = modifier,
            state = state,
            current = current,
            controller = controller,
            controlsVisible = controlsVisible,
            locked = locked,
            isLandscape = immersive,
            displayMode = displayMode,
            seekPosition = seekPosition,
            subtitleSize = subtitleSize,
            subtitleBackground = subtitleBackground,
            gestureMessage = gestureMessage,
            menuExpanded = menuExpanded,
            castSupported = remember(context) { context.hasRemoteMediaRoute() },
            onControlsVisible = { controlsVisible = it },
            onLocked = {
                locked = it
                controlsVisible = true
            },
            onMenuExpanded = { menuExpanded = it },
            onBack = onBack,
            onQueue = onQueue,
            onDownload = onDownload,
            onPlayAsAudio = onPlayAsAudio,
            onPiP = {
                controlsVisible = false
                activity?.enterVideoPictureInPicture()
            },
            onRotate = { activity?.toggleVideoOrientation(isLandscape) },
            onFullscreen = { fullscreen = !fullscreen },
            onDialog = { dialog = it },
            onSeekPosition = { seekPosition = it },
            onSliderDragging = { draggingSlider = it },
            onBrightness = {
                brightness = it
                activity?.setWindowBrightness(it)
            },
            brightness = brightness,
            onGestureMessage = { gestureMessage = it },
        )
    }

    if (immersive) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            canvas(Modifier.fillMaxSize())
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { canvas(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) }
            item {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                    Text(
                        state.title.ifBlank { current?.title ?: "Video" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        listOfNotNull(
                            current?.displayName,
                            current?.sizeBytes?.takeIf { it > 0 }?.asFileSize(),
                            current?.mimeType,
                        ).joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        VideoActionChip(Icons.Rounded.Download, "Download", onDownload)
                        VideoActionChip(Icons.Rounded.PictureInPicture, "Picture-in-Picture") {
                            activity?.enterVideoPictureInPicture()
                        }
                        VideoActionChip(Icons.Rounded.Audiotrack, "Play as audio", onPlayAsAudio)
                    }
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(18.dp))
                    Text("Up next", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.queue.size <= 1) {
                item {
                    Text(
                        "No other videos are queued.",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(state.queue, key = { _, item -> item.uri.toString() }) { index, item ->
                    if (index != state.currentIndex) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MediaArtwork(item, Modifier.width(112.dp).aspectRatio(16f / 9f))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(item.durationMs.asDuration(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { controller.playIndex(index) }) {
                                Icon(Icons.Rounded.PlayArrow, "Play ${item.title}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoCanvas(
    modifier: Modifier,
    state: PlaybackState,
    current: OmniMedia?,
    controller: PlaybackController,
    controlsVisible: Boolean,
    locked: Boolean,
    isLandscape: Boolean,
    displayMode: VideoDisplayMode,
    seekPosition: Float,
    subtitleSize: Float,
    subtitleBackground: Boolean,
    gestureMessage: String?,
    menuExpanded: Boolean,
    castSupported: Boolean,
    onControlsVisible: (Boolean) -> Unit,
    onLocked: (Boolean) -> Unit,
    onMenuExpanded: (Boolean) -> Unit,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onPlayAsAudio: () -> Unit,
    onPiP: () -> Unit,
    onRotate: () -> Unit,
    onFullscreen: () -> Unit,
    onDialog: (VideoDialog) -> Unit,
    onSeekPosition: (Float) -> Unit,
    onSliderDragging: (Boolean) -> Unit,
    brightness: Float,
    onBrightness: (Float) -> Unit,
    onGestureMessage: (String?) -> Unit,
) {
    val overlay = Brush.verticalGradient(
        listOf(Color(0xCC000000), Color.Transparent, Color.Transparent, Color(0xE6000000)),
    )
    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(locked, state.durationMs) {
                detectTapGestures(
                    onTap = { onControlsVisible(!controlsVisible) },
                    onDoubleTap = { point ->
                        if (!locked) {
                            val forward = point.x >= size.width / 2f
                            controller.seekBy(if (forward) 10_000 else -10_000)
                            onGestureMessage(if (forward) "+10 seconds" else "−10 seconds")
                            onControlsVisible(true)
                        }
                    },
                )
            }
            .pointerInput(locked, state.durationMs, state.volume, brightness) {
                if (locked) return@pointerInput
                var mode: VideoGesture? = null
                var totalX = 0f
                var totalY = 0f
                var startPosition = state.positionMs
                var startVolume = state.volume
                var startBrightness = brightness
                var previewPosition = startPosition
                detectDragGestures(
                    onDragStart = { point ->
                        mode = null
                        totalX = 0f
                        totalY = 0f
                        startPosition = state.positionMs
                        startVolume = state.volume
                        startBrightness = brightness
                        previewPosition = startPosition
                        if (point.x < 0f) mode = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        if (mode == null && (abs(totalX) > 18f || abs(totalY) > 18f)) {
                            mode = if (abs(totalX) >= abs(totalY)) VideoGesture.SEEK
                            else if (change.position.x < size.width / 2f) VideoGesture.BRIGHTNESS
                            else VideoGesture.VOLUME
                        }
                        when (mode) {
                            VideoGesture.SEEK -> {
                                val range = state.durationMs.coerceAtLeast(1L)
                                previewPosition = (startPosition + (totalX / size.width) * range * 0.45f)
                                    .toLong().coerceIn(0L, range)
                                onGestureMessage(previewPosition.asDuration())
                            }
                            VideoGesture.BRIGHTNESS -> {
                                val value = (startBrightness - totalY / size.height).coerceIn(0.05f, 1f)
                                onBrightness(value)
                                onGestureMessage("Brightness ${(value * 100).toInt()}%")
                            }
                            VideoGesture.VOLUME -> {
                                val value = (startVolume - totalY / size.height).coerceIn(0f, 1f)
                                controller.setVolume(value)
                                onGestureMessage("Volume ${(value * 100).toInt()}%")
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        if (mode == VideoGesture.SEEK) controller.seekTo(previewPosition)
                        mode = null
                    },
                    onDragCancel = { mode = null },
                )
            },
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = controller.player()
                    useController = false
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { view ->
                view.player = controller.player()
                view.resizeMode = displayMode.resizeMode
                view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSize)
                view.subtitleView?.setStyle(
                    CaptionStyleCompat(
                        AndroidColor.WHITE,
                        if (subtitleBackground) AndroidColor.argb(185, 0, 0, 0) else AndroidColor.TRANSPARENT,
                        AndroidColor.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        AndroidColor.BLACK,
                        null,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.buffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(52.dp), color = MaterialTheme.colorScheme.primary)
        }

        gestureMessage?.let { message ->
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xCC151515),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp), fontWeight = FontWeight.Bold)
            }
        }

        if (locked) {
            IconButton(
                onClick = { onLocked(false) },
                modifier = Modifier.align(Alignment.CenterStart).padding(14.dp).background(Color(0xAA000000), CircleShape),
            ) {
                Icon(Icons.Rounded.Lock, "Unlock screen")
            }
        } else {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize().background(overlay)) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                            .background(Color(0x66000000), RoundedCornerShape(16.dp))
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Text(
                            state.title.ifBlank { current?.title ?: "Video" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (castSupported) {
                            AndroidView(
                                factory = { ctx ->
                                    MediaRouteButton(ctx).apply {
                                        routeTypes = MediaRouter.ROUTE_TYPE_LIVE_AUDIO or MediaRouter.ROUTE_TYPE_LIVE_VIDEO
                                    }
                                },
                                modifier = Modifier.size(44.dp),
                            )
                        }
                        IconButton(onClick = onPiP) { Icon(Icons.Rounded.PictureInPicture, "Picture-in-Picture") }
                        Box {
                            IconButton(onClick = { onMenuExpanded(true) }) { Icon(Icons.Rounded.MoreVert, "More options") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpanded(false) }) {
                                DropdownMenuItem(
                                    text = { Text("Download another copy") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, null) },
                                    onClick = { onMenuExpanded(false); onDownload() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Play as audio") },
                                    leadingIcon = { Icon(Icons.Rounded.Audiotrack, null) },
                                    onClick = { onMenuExpanded(false); onPlayAsAudio() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Open queue") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
                                    onClick = { onMenuExpanded(false); onQueue() },
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 34.dp else 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.queue.size > 1) {
                            IconButton(onClick = controller::skipPrevious, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, "Previous video", modifier = Modifier.size(32.dp))
                            }
                        }
                        Surface(
                            onClick = controller::togglePlayPause,
                            modifier = Modifier.size(if (isLandscape) 72.dp else 64.dp),
                            shape = CircleShape,
                            color = Color(0xD9FFFFFF),
                            contentColor = Color.Black,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (state.playing) "Pause" else "Play",
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }
                        if (state.queue.size > 1) {
                            IconButton(onClick = controller::skipNext, modifier = Modifier.size(52.dp)) {
                                Icon(Icons.Rounded.SkipNext, "Next video", modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .background(Color(0x99000000), RoundedCornerShape(18.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Slider(
                            value = seekPosition.coerceIn(0f, state.durationMs.coerceAtLeast(1L).toFloat()),
                            onValueChange = { onSliderDragging(true); onSeekPosition(it) },
                            onValueChangeFinished = { controller.seekTo(seekPosition.toLong()); onSliderDragging(false) },
                            valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                            enabled = state.durationMs > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${seekPosition.toLong().asDuration()} / ${state.durationMs.asDuration()}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                                VideoOverlayButton(Icons.Rounded.LockOpen, "Lock") { onLocked(true) }
                                VideoOverlayButton(Icons.Rounded.Brightness6, "Brightness and volume") { onDialog(VideoDialog.LEVELS) }
                                VideoOverlayButton(Icons.Rounded.Subtitles, "Subtitles") { onDialog(VideoDialog.SUBTITLES) }
                                VideoOverlayButton(Icons.Rounded.Audiotrack, "Audio track") { onDialog(VideoDialog.AUDIO_TRACK) }
                                VideoOverlayButton(Icons.Rounded.HighQuality, "Quality") { onDialog(VideoDialog.QUALITY) }
                                VideoOverlayButton(Icons.Rounded.Speed, "Playback speed") { onDialog(VideoDialog.SPEED) }
                                VideoOverlayButton(Icons.Rounded.AspectRatio, "Aspect ratio and zoom") { onDialog(VideoDialog.DISPLAY) }
                                VideoOverlayButton(Icons.Rounded.ScreenRotation, "Rotate") { onRotate() }
                                VideoOverlayButton(
                                    if (isLandscape) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    if (isLandscape) "Exit full screen" else "Full screen",
                                ) { onFullscreen() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoOverlayButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
        Icon(icon, label, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun VideoActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VideoOptionsDialog(
    dialog: VideoDialog?,
    controller: PlaybackController,
    state: PlaybackState,
    displayMode: VideoDisplayMode,
    brightness: Float,
    subtitleSize: Float,
    subtitleBackground: Boolean,
    onDismiss: () -> Unit,
    onDisplayMode: (VideoDisplayMode) -> Unit,
    onBrightness: (Float) -> Unit,
    onSubtitleSize: (Float) -> Unit,
    onSubtitleBackground: (Boolean) -> Unit,
    onOpenSubtitleStyle: () -> Unit,
) {
    if (dialog == null) return
    val title = when (dialog) {
        VideoDialog.SPEED -> "Playback speed"
        VideoDialog.SUBTITLES -> "Subtitles"
        VideoDialog.SUBTITLE_STYLE -> "Subtitle appearance"
        VideoDialog.AUDIO_TRACK -> "Audio track"
        VideoDialog.QUALITY -> "Video quality"
        VideoDialog.DISPLAY -> "Aspect ratio and zoom"
        VideoDialog.LEVELS -> "Brightness and volume"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                when (dialog) {
                    VideoDialog.SPEED -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        VideoOptionRow("${speed.cleanNumber()}×", state.speed == speed) {
                            controller.setSpeed(speed)
                            onDismiss()
                        }
                    }
                    VideoDialog.SUBTITLES -> {
                        VideoOptionRow("Off", controller.trackOptions(C.TRACK_TYPE_TEXT).none { it.selected }) {
                            controller.selectTrack(C.TRACK_TYPE_TEXT, null, disabled = true)
                            onDismiss()
                        }
                        VideoOptionRow("Automatic", false) {
                            controller.selectTrack(C.TRACK_TYPE_TEXT, null)
                            onDismiss()
                        }
                        controller.trackOptions(C.TRACK_TYPE_TEXT).forEach { option ->
                            TrackOptionRow(option) {
                                controller.selectTrack(C.TRACK_TYPE_TEXT, option)
                                onDismiss()
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 5.dp))
                        VideoOptionRow("Subtitle appearance", false, onOpenSubtitleStyle)
                    }
                    VideoDialog.SUBTITLE_STYLE -> {
                        Text("Text size: ${subtitleSize.toInt()} sp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(value = subtitleSize, onValueChange = onSubtitleSize, valueRange = 14f..28f)
                        VideoOptionRow("Background box", subtitleBackground) { onSubtitleBackground(!subtitleBackground) }
                    }
                    VideoDialog.AUDIO_TRACK -> TrackOptions(
                        options = controller.trackOptions(C.TRACK_TYPE_AUDIO),
                        emptyMessage = "This video has no alternate audio tracks.",
                    ) { option -> controller.selectTrack(C.TRACK_TYPE_AUDIO, option); onDismiss() }
                    VideoDialog.QUALITY -> {
                        VideoOptionRow("Automatic", controller.trackOptions(C.TRACK_TYPE_VIDEO).none { it.selected }) {
                            controller.selectTrack(C.TRACK_TYPE_VIDEO, null)
                            onDismiss()
                        }
                        TrackOptions(
                            options = controller.trackOptions(C.TRACK_TYPE_VIDEO),
                            emptyMessage = "This local file contains one fixed video stream.",
                        ) { option -> controller.selectTrack(C.TRACK_TYPE_VIDEO, option); onDismiss() }
                    }
                    VideoDialog.DISPLAY -> VideoDisplayMode.entries.forEach { mode ->
                        VideoOptionRow(mode.label, displayMode == mode) {
                            onDisplayMode(mode)
                            onDismiss()
                        }
                    }
                    VideoDialog.LEVELS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brightness6, null)
                            Text("Brightness", Modifier.padding(start = 8.dp))
                        }
                        Slider(value = brightness, onValueChange = onBrightness, valueRange = 0.05f..1f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
                            Text("Volume", Modifier.padding(start = 8.dp))
                        }
                        Slider(value = state.volume, onValueChange = controller::setVolume, valueRange = 0f..1f)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun TrackOptions(
    options: List<PlaybackTrackOption>,
    emptyMessage: String,
    onSelect: (PlaybackTrackOption) -> Unit,
) {
    if (options.isEmpty()) {
        Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        options.forEach { option -> TrackOptionRow(option) { onSelect(option) } }
    }
}

@Composable
private fun TrackOptionRow(option: PlaybackTrackOption, onClick: () -> Unit) {
    VideoOptionRow(option.label, option.selected, onClick)
}

@Composable
private fun VideoOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            if (selected) {
                Box(Modifier.size(9.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}

private fun Activity.windowBrightness(): Float = window.attributes.screenBrightness
    .takeIf { it >= 0f }
    ?: 0.55f

private fun Activity.setWindowBrightness(value: Float) {
    window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.05f, 1f) }
}

private fun Activity.toggleVideoOrientation(isLandscape: Boolean) {
    requestedOrientation = if (isLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

private fun Activity.enterVideoPictureInPicture() {
    if (packageManager.hasSystemFeature("android.software.picture_in_picture")) {
        enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
        )
    }
}

@Suppress("DEPRECATION")
private fun Context.hasRemoteMediaRoute(): Boolean {
    val router = getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter ?: return false
    return router.routeCount > 1
}

private fun Float.cleanNumber(): String = if (this % 1f == 0f) toInt().toString() else toString()
