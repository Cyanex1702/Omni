package com.omniplayer.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.MoodDefinition
import com.omniplayer.app.model.MoodRecommendation
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.playback.EqualizerState
import com.omniplayer.app.playback.PlaybackController
import com.omniplayer.app.playback.PlaybackState
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniPink
import com.omniplayer.app.ui.theme.OmniTextMuted

private enum class PlayerTab(val label: String) { PLAYER("Player"), LYRICS("Lyrics"), RELATED("Related") }

@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    current: OmniMedia?,
    controller: PlaybackController,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit,
    onSleepTimer: (Long) -> Unit,
    playerAppearance: String,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    moods: List<MoodDefinition>,
    currentMoodMatches: List<MoodRecommendation>,
    manualMoodIds: Set<String>,
    onToggleMood: (MoodDefinition) -> Unit,
    onManageMoods: () -> Unit,
) {
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(PlayerTab.PLAYER) }
    var showTimer by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var showMoods by remember { mutableStateOf(false) }
    LaunchedEffect(state.positionMs, dragging) {
        if (!dragging) seekPosition = state.positionMs.toFloat()
    }

    if (showTimer) {
        SleepTimerDialog(
            onDismiss = { showTimer = false },
            onSelect = {
                onSleepTimer(it)
                showTimer = false
            },
        )
    }

    if (showMoods && current != null && current.kind == MediaKind.AUDIO) {
        MoodAssignmentDialog(
            song = current,
            moods = moods,
            manuallySelected = manualMoodIds,
            suggested = currentMoodMatches.mapTo(mutableSetOf()) { it.mood.id },
            onToggle = onToggleMood,
            onManageMoods = onManageMoods,
            onDismiss = { showMoods = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surface,
                ),
            ),
        ).padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("From your library", color = muted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onQueue) { Icon(Icons.Rounded.MoreVert, "Queue and options") }
        }
        Spacer(Modifier.height(6.dp))
        AudioPlayerVisual(state, current, playerAppearance, Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.title.ifBlank { "Select something to play" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(state.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(
                onClick = { showMoods = true },
                enabled = current?.kind == MediaKind.AUDIO,
            ) {
                Icon(Icons.Rounded.AutoAwesome, "Edit song moods", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onFavorite, enabled = current != null) {
                Icon(
                    if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        if (current?.kind == MediaKind.AUDIO) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(vertical = 3.dp),
            ) {
                if (currentMoodMatches.isEmpty()) {
                    item {
                        Surface(
                            onClick = { showMoods = true },
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Add moods", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    items(currentMoodMatches, key = { it.mood.id }) { match ->
                        MoodPill(mood = match.mood, onClick = { showMoods = true })
                    }
                }
            }
        }
        if (state.error != null) {
            Surface(color = Color(0xFF3A1515), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.error, color = Color(0xFFFFA0A0), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = controller::retry) { Text("Retry", color = accent) }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Slider(
            value = seekPosition.coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
            onValueChange = {
                dragging = true
                seekPosition = it
            },
            onValueChangeFinished = {
                controller.seekTo(seekPosition.toLong())
                dragging = false
            },
            valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
            enabled = current != null && state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(seekPosition.toLong().asDuration(), color = muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(state.durationMs.asDuration(), color = muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { controller.setShuffle(!state.shuffle) }) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) accent else MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = controller::skipPrevious) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(34.dp))
            }
            Surface(
                modifier = Modifier.size(64.dp).clickable(enabled = current != null, onClick = controller::togglePlayPause),
                color = Color.Transparent,
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(listOf(accent, MaterialTheme.colorScheme.secondary)),
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.buffering) {
                        CircularProgressIndicator(Modifier.size(30.dp), color = Color.White, strokeWidth = 3.dp)
                    } else {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Play or pause",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            IconButton(onClick = controller::skipNext) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(34.dp))
            }
            IconButton(
                onClick = {
                    val next = when (state.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    controller.setRepeatMode(next)
                },
            ) {
                Icon(
                    Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PlayerTool(Icons.Rounded.Equalizer, "EQ", onEqualizer)
            PlayerTool(Icons.Rounded.Speed, "${state.speed.formatSpeed()}x") {
                controller.setSpeed(nextSpeed(state.speed))
            }
            PlayerTool(Icons.Rounded.Timer, "Timer") { showTimer = true }
            PlayerTool(Icons.AutoMirrored.Rounded.PlaylistPlay, "Queue", onQueue)
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(136.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    PlayerTab.entries.forEach { item ->
                        Column(
                            modifier = Modifier.weight(1f).clickable { tab = item }.padding(top = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                item.label,
                                color = if (tab == item) MaterialTheme.colorScheme.onSurface else muted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.width(42.dp).height(2.dp).background(
                                    if (tab == item) accent else Color.Transparent,
                                    CircleShape,
                                ),
                            )
                        }
                    }
                }
                when (tab) {
                    PlayerTab.PLAYER -> PlayerSummaryPanel(current, onQueue, onEqualizer, Modifier.weight(1f))
                    PlayerTab.LYRICS -> LyricsPanel(current, Modifier.weight(1f))
                    PlayerTab.RELATED -> RelatedPanel(state, controller, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerVisual(
    state: PlaybackState,
    current: OmniMedia?,
    appearance: String,
    modifier: Modifier,
) {
    val motion = rememberInfiniteTransition(label = "audio artwork motion")
    val rotation by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "vinyl rotation",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1_800), RepeatMode.Reverse),
        label = "wave pulse",
    )
    val primaryAccent = MaterialTheme.colorScheme.primary
    val secondaryAccent = MaterialTheme.colorScheme.secondary
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(0.95f).aspectRatio(1f).background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), Color.Transparent),
                ),
                CircleShape,
            ),
        )
        when (appearance) {
            "vinyl" -> Box(
                modifier = Modifier.fillMaxWidth(0.88f).aspectRatio(1f).rotate(if (state.playing) rotation else 0f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFF08080B))
                    repeat(13) { index ->
                        drawCircle(
                            color = Color.White.copy(alpha = if (index % 2 == 0) 0.12f else 0.06f),
                            radius = size.minDimension * (0.17f + index * 0.025f),
                            style = Stroke(width = 1f),
                        )
                    }
                    drawCircle(primaryAccent.copy(alpha = 0.35f), radius = size.minDimension * 0.19f)
                }
                MediaArtwork(current, Modifier.fillMaxWidth(0.37f).aspectRatio(1f), circular = true)
            }
            "wave" -> Box(
                modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    repeat(9) { index ->
                        val fraction = 0.30f + index * 0.035f
                        drawCircle(
                            color = secondaryAccent.copy(
                                alpha = (0.12f + ((index % 3) * 0.06f)) * if (state.playing) pulse else 0.45f,
                            ),
                            radius = size.minDimension * fraction,
                            style = Stroke(width = 2f + (index % 3)),
                        )
                    }
                }
                MediaArtwork(current, Modifier.fillMaxWidth(0.54f).aspectRatio(1f), circular = true)
            }
            else -> MediaArtwork(
                current,
                Modifier.fillMaxWidth(0.86f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
            )
        }
        if (state.buffering) {
            Surface(color = Color(0x99000000), shape = CircleShape) {
                CircularProgressIndicator(Modifier.padding(16.dp).size(34.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PlayerSummaryPanel(
    current: OmniMedia?,
    onQueue: () -> Unit,
    onAudioTools: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onQueue) { Text("Queue") }
        Text(current?.album?.takeUnless { it == "Unknown album" } ?: "Local audio", color = OmniTextMuted, maxLines = 1)
        TextButton(onClick = onAudioTools) { Text("Audio tools") }
    }
}

@Composable
private fun PlayerTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(5.dp)) {
        Icon(icon, label, modifier = Modifier.size(21.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = OmniTextMuted)
    }
}

@Composable
private fun LyricsPanel(current: OmniMedia?, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            current?.title?.let { "♪  $it" } ?: "Lyrics will appear here",
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "No embedded lyrics found for this file",
            color = OmniTextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RelatedPanel(state: PlaybackState, controller: PlaybackController, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        itemsIndexed(state.queue.take(2), key = { _, item -> item.uri.toString() }) { index, item ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { controller.playIndex(index) },
                color = if (index == state.currentIndex) OmniOrange.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MediaArtwork(item, Modifier.size(34.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 11.dp)) {
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(item.artist, color = OmniTextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(item.durationMs.asDuration(), color = OmniTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = { onSelect(minutes * 60_000L) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop after $minutes minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(onClick = { onSelect(0) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Turn timer off", modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun nextSpeed(current: Float): Float = when {
    current < 1.24f -> 1.25f
    current < 1.49f -> 1.5f
    current < 1.99f -> 2f
    else -> 1f
}

private fun Float.formatSpeed(): String = if (this % 1f == 0f) toInt().toString() else toString()

@Composable
fun QueueScreen(
    state: PlaybackState,
    onBack: () -> Unit,
    onPlay: (OmniMedia) -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Queue (${state.queue.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                IconButton(onClick = onClear, enabled = state.queue.isNotEmpty()) {
                    Icon(Icons.Rounded.DeleteSweep, "Clear queue")
                }
            }
            Text(
                if (state.queue.isEmpty()) "Nothing queued" else "${state.queue.sumOf { it.durationMs }.asDuration()} total",
                color = OmniTextMuted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        if (state.queue.isEmpty()) {
            item { EmptyState("Queue is empty", "Choose a song or video from your library.") }
        } else {
            itemsIndexed(state.queue, key = { _, item -> item.uri.toString() }) { index, item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onPlay(item) },
                    color = if (index == state.currentIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = if (index == state.currentIndex) OmniOrange else OmniTextMuted, modifier = Modifier.width(28.dp))
                        MediaArtwork(item, Modifier.size(46.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(item.durationMs.asDuration(), color = OmniTextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerScreen(
    state: EqualizerState,
    onChange: (EqualizerState) -> Unit,
    onBack: () -> Unit,
) {
    var preset by remember { mutableStateOf("Custom") }
    val frequencies = listOf("60", "230", "910", "3.6k", "14k")
    val presets = linkedMapOf(
        "Custom" to null,
        "Rock" to listOf(4f, 2f, -1f, 2f, 4f),
        "Pop" to listOf(-1f, 2f, 4f, 2f, -1f),
        "Jazz" to listOf(3f, 1f, 1f, 3f, 4f),
        "Classical" to listOf(4f, 3f, 0f, 2f, 3f),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Equalizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { onChange(state.copy(enabled = it)) })
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { (name, gains) ->
                    AssistChip(
                        onClick = {
                            preset = name
                            if (gains != null) onChange(state.copy(enabled = true, gainsDb = gains))
                        },
                        label = { Text(name) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (preset == name) OmniOrange else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = Color.White,
                        ),
                    )
                }
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 8.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    frequencies.forEachIndexed { index, frequency ->
                        val value = state.gainsDb.getOrElse(index) { 0f }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${value.toInt()} dB", color = OmniOrange, style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Slider(
                                    value = value,
                                    onValueChange = { gain ->
                                        val gains = state.gainsDb.toMutableList().also {
                                            while (it.size < frequencies.size) it.add(0f)
                                            it[index] = gain
                                        }
                                        preset = "Custom"
                                        onChange(state.copy(gainsDb = gains))
                                    },
                                    valueRange = -12f..12f,
                                    enabled = state.enabled,
                                    modifier = Modifier.width(190.dp).height(38.dp).rotate(-90f),
                                    colors = SliderDefaults.colors(thumbColor = OmniOrange, activeTrackColor = OmniOrange),
                                )
                            }
                            Text(frequency, color = OmniTextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            EffectSlider("Bass Boost", state.bassBoost, state.enabled) { onChange(state.copy(bassBoost = it)) }
            Spacer(Modifier.height(10.dp))
            EffectSlider("Virtualizer", state.virtualizer, state.enabled) { onChange(state.copy(virtualizer = it)) }
            Spacer(Modifier.height(10.dp))
            EffectSlider("Reverb", state.reverb, state.enabled) { onChange(state.copy(reverb = it)) }
        }
        item {
            Text(
                "Audio effects are applied to Omni's playback session. Some devices may not provide every effect.",
                color = OmniTextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Float, enabled: Boolean, onValue: (Float) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Equalizer, null, tint = OmniOrange)
                Text(label, modifier = Modifier.padding(start = 10.dp).weight(1f), fontWeight = FontWeight.SemiBold)
                Text("${(value * 100).toInt()}%", color = OmniTextMuted)
            }
            Slider(
                value = value,
                onValueChange = onValue,
                valueRange = 0f..1f,
                enabled = enabled,
                colors = SliderDefaults.colors(thumbColor = OmniOrange, activeTrackColor = OmniOrange),
            )
        }
    }
}
