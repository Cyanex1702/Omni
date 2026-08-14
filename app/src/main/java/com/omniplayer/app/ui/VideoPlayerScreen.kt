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
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.playback.PlaybackController
import com.omniplayer.app.playback.PlaybackState
import com.omniplayer.app.playback.PlaybackTrackOption
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class VideoDialog {
    MORE, SPEED, SUBTITLES, SUBTITLE_STYLE, AUDIO_TRACK, QUALITY, DISPLAY, LEVELS, INFO
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
    var dialog by remember { mutableStateOf<VideoDialog?>(null) }
    var displayMode by remember { mutableStateOf(VideoDisplayMode.FIT) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var draggingSlider by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(activity?.windowBrightness() ?: 0.55f) }
    var subtitleSize by remember { mutableFloatStateOf(18f) }
    var subtitleBackground by remember { mutableStateOf(true) }
    var gestureMessage by remember { mutableStateOf<String?>(null) }
    val immersive = isLandscape

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

    VideoOptionsSheet(
        dialog = dialog,
        controller = controller,
        state = state,
        current = current,
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
        onOpenDialog = { dialog = it },
        onQueue = onQueue,
        onDownload = onDownload,
        onPlayAsAudio = onPlayAsAudio,
        onPiP = {
            controlsVisible = false
            activity?.enterVideoPictureInPicture()
        },
        onLandscape = { activity?.toggleVideoOrientation(isLandscape) },
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
            castSupported = remember(context) { context.hasRemoteMediaRoute() },
            onControlsVisible = { controlsVisible = it },
            onLocked = {
                locked = it
                controlsVisible = true
            },
            onBack = onBack,
            onPiP = {
                controlsVisible = false
                activity?.enterVideoPictureInPicture()
            },
            onFullscreen = { activity?.toggleVideoOrientation(isLandscape) },
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
        val videoQueue = state.queue.withIndex().filter { it.value.kind == MediaKind.VIDEO }
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                canvas(
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "NOW WATCHING",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                state.title.ifBlank { current?.title ?: "Video" },
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(
                                    state.durationMs.takeIf { it > 0L }?.asDuration(),
                                    current?.sizeBytes?.takeIf { it > 0L }?.asFileSize(),
                                    current?.mimeType?.substringAfter('/')?.uppercase(),
                                ).joinToString("  •  "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        VideoActionChip(Icons.Rounded.Landscape, "Landscape") {
                            activity?.toggleVideoOrientation(false)
                        }
                        VideoActionChip(Icons.AutoMirrored.Rounded.PlaylistPlay, "Video queue", onQueue)
                        VideoActionChip(Icons.Rounded.AspectRatio, displayMode.label) {
                            dialog = VideoDialog.DISPLAY
                        }
                        VideoActionChip(Icons.Rounded.Download, "Download", onDownload)
                        VideoActionChip(Icons.Rounded.PictureInPicture, "PiP") {
                            activity?.enterVideoPictureInPicture()
                        }
                        VideoActionChip(Icons.Rounded.Audiotrack, "Play as audio", onPlayAsAudio)
                    }
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.ScreenRotation,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "Swipe left/right to seek  •  Left edge: brightness  •  Right edge: volume",
                                modifier = Modifier.padding(start = 9.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "A cinema queue built only for videos",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Surface(
                            onClick = onQueue,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                "View queue",
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            if (videoQueue.count { it.index != state.currentIndex } == 0) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            "No other videos are queued. Start another video from the Videos library to build this cinema queue.",
                            modifier = Modifier.padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    videoQueue.filter { it.index != state.currentIndex },
                    key = { _, indexed -> indexed.value.uri.toString() },
                ) { position, indexed ->
                    VideoQueueCard(
                        number = position + 1,
                        media = indexed.value,
                        onPlay = { controller.playIndex(indexed.index) },
                    )
                }
            }
        }
    }
}

@Composable
fun VideoQueueScreen(
    state: PlaybackState,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit,
) {
    val videos = state.queue.withIndex().filter { it.value.kind == MediaKind.VIDEO }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Video queue", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${videos.size} videos • cinema playback order",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "This list is separate from the music queue and uses video previews.",
                        Modifier.padding(start = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (videos.isEmpty()) {
            item {
                Text(
                    "No videos are queued.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(videos, key = { _, item -> item.value.uri.toString() }) { position, item ->
                VideoQueueCard(
                    number = position + 1,
                    media = item.value,
                    isCurrent = item.index == state.currentIndex,
                    onPlay = { onPlayIndex(item.index) },
                )
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
    castSupported: Boolean,
    onControlsVisible: (Boolean) -> Unit,
    onLocked: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPiP: () -> Unit,
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
                        IconButton(onClick = { onDialog(VideoDialog.MORE) }) {
                            Icon(Icons.Rounded.MoreVert, "More video options")
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 22.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.queue.size > 1) {
                            IconButton(onClick = controller::skipPrevious, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, "Previous video", modifier = Modifier.size(27.dp))
                            }
                        }
                        VideoTransportButton(Icons.Rounded.Replay10, "Back 10 seconds") {
                            controller.seekBy(-10_000)
                            onGestureMessage("−10 seconds")
                        }
                        Surface(
                            onClick = controller::togglePlayPause,
                            modifier = Modifier.size(if (isLandscape) 74.dp else 62.dp),
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
                        VideoTransportButton(Icons.Rounded.Forward10, "Forward 10 seconds") {
                            controller.seekBy(10_000)
                            onGestureMessage("+10 seconds")
                        }
                        if (state.queue.size > 1) {
                            IconButton(onClick = controller::skipNext, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.SkipNext, "Next video", modifier = Modifier.size(27.dp))
                            }
                        }
                    }

                    VideoSideLevelButton(
                        icon = Icons.Rounded.Brightness6,
                        label = "Brightness",
                        value = brightness,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
                    ) { onDialog(VideoDialog.LEVELS) }
                    VideoSideLevelButton(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        label = "Volume",
                        value = state.volume,
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    ) { onDialog(VideoDialog.LEVELS) }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .background(Color(0xA6000000), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
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
                                VideoOverlayButton(Icons.Rounded.Subtitles, "Subtitles") { onDialog(VideoDialog.SUBTITLES) }
                                VideoOverlayButton(Icons.Rounded.Speed, "Playback speed") { onDialog(VideoDialog.SPEED) }
                                VideoOverlayButton(Icons.Rounded.AspectRatio, "Aspect ratio and zoom") { onDialog(VideoDialog.DISPLAY) }
                                VideoOverlayButton(
                                    Icons.Rounded.Landscape,
                                    if (isLandscape) "Return to portrait" else "Landscape mode",
                                ) { onFullscreen() }
                                VideoOverlayButton(Icons.Rounded.MoreVert, "More options") { onDialog(VideoDialog.MORE) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoTransportButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(46.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun VideoSideLevelButton(
    icon: ImageVector,
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, label, modifier = Modifier.size(19.dp))
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun VideoQueueCard(
    number: Int,
    media: OmniMedia,
    isCurrent: Boolean = false,
    onPlay: () -> Unit,
) {
    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                MediaArtwork(
                    media,
                    Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        if (isCurrent) "PLAYING" else number.toString().padStart(2, '0'),
                        Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    media.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "${media.durationMs.asDuration()}  •  ${media.sizeBytes.asFileSize()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (isCurrent) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    if (isCurrent) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isCurrent) "Currently playing" else "Play ${media.title}",
                    Modifier.padding(9.dp).size(20.dp),
                )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun VideoOptionsSheet(
    dialog: VideoDialog?,
    controller: PlaybackController,
    state: PlaybackState,
    current: OmniMedia?,
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
    onOpenDialog: (VideoDialog) -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onPlayAsAudio: () -> Unit,
    onPiP: () -> Unit,
    onLandscape: () -> Unit,
) {
    if (dialog == null) return
    val title = when (dialog) {
        VideoDialog.MORE -> "Video tools"
        VideoDialog.SPEED -> "Playback speed"
        VideoDialog.SUBTITLES -> "Subtitles"
        VideoDialog.SUBTITLE_STYLE -> "Subtitle appearance"
        VideoDialog.AUDIO_TRACK -> "Audio track"
        VideoDialog.QUALITY -> "Video quality"
        VideoDialog.DISPLAY -> "Aspect ratio and zoom"
        VideoDialog.LEVELS -> "Brightness and volume"
        VideoDialog.INFO -> "Media information"
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (dialog == VideoDialog.MORE) {
                        Text(
                            "Everything stays available without crowding the video",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))
            when (dialog) {
                VideoDialog.MORE -> {
                    val actions = listOf(
                        VideoSheetAction(Icons.Rounded.Brightness6, "Levels") { onOpenDialog(VideoDialog.LEVELS) },
                        VideoSheetAction(Icons.Rounded.Subtitles, "Subtitles") { onOpenDialog(VideoDialog.SUBTITLES) },
                        VideoSheetAction(Icons.Rounded.Audiotrack, "Audio track") { onOpenDialog(VideoDialog.AUDIO_TRACK) },
                        VideoSheetAction(Icons.Rounded.Speed, "Speed") { onOpenDialog(VideoDialog.SPEED) },
                        VideoSheetAction(Icons.Rounded.HighQuality, "Quality") { onOpenDialog(VideoDialog.QUALITY) },
                        VideoSheetAction(Icons.Rounded.AspectRatio, "Display") { onOpenDialog(VideoDialog.DISPLAY) },
                        VideoSheetAction(Icons.Rounded.Landscape, "Landscape") { onDismiss(); onLandscape() },
                        VideoSheetAction(Icons.Rounded.PictureInPicture, "Picture-in-Picture") { onDismiss(); onPiP() },
                        VideoSheetAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "Video queue") { onDismiss(); onQueue() },
                        VideoSheetAction(Icons.Rounded.Download, "Download") { onDismiss(); onDownload() },
                        VideoSheetAction(Icons.Rounded.Audiotrack, "Play as audio") { onDismiss(); onPlayAsAudio() },
                        VideoSheetAction(Icons.Rounded.Info, "Media info") { onOpenDialog(VideoDialog.INFO) },
                    )
                    actions.chunked(3).forEach { rowActions ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowActions.forEach { action ->
                                VideoSheetActionButton(action, Modifier.weight(1f))
                            }
                            repeat(3 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
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
                        Text("Brightness  ${(brightness * 100).toInt()}%", Modifier.padding(start = 8.dp))
                    }
                    Slider(value = brightness, onValueChange = onBrightness, valueRange = 0.05f..1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null)
                        Text("Volume  ${(state.volume * 100).toInt()}%", Modifier.padding(start = 8.dp))
                    }
                    Slider(value = state.volume, onValueChange = controller::setVolume, valueRange = 0f..1f)
                    Text(
                        "You can also swipe vertically on the left or right edge of the video.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                VideoDialog.INFO -> {
                    VideoInfoRow("Title", state.title.ifBlank { current?.title ?: "Video" })
                    VideoInfoRow("Duration", state.durationMs.asDuration())
                    VideoInfoRow("File size", current?.sizeBytes?.asFileSize() ?: "Unknown")
                    VideoInfoRow("Format", current?.mimeType?.substringAfter('/')?.uppercase() ?: "Unknown")
                    VideoInfoRow("File", current?.displayName?.ifBlank { "Unknown" } ?: "Unknown")
                }
            }
        }
    }
}

private data class VideoSheetAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun VideoSheetActionButton(action: VideoSheetAction, modifier: Modifier = Modifier) {
    Surface(
        onClick = action.onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(action.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            Spacer(Modifier.height(7.dp))
            Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun VideoInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.width(92.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
