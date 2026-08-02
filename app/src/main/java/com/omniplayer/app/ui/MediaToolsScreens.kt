package com.omniplayer.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniplayer.app.MediaToolState
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniSuccess
import com.omniplayer.app.ui.theme.OmniTextMuted

@Composable
fun RingtoneCutterScreen(
    audio: List<OmniMedia>,
    status: MediaToolState,
    onBack: () -> Unit,
    onCreate: (OmniMedia, Long, Long) -> Unit,
) {
    var selectedUri by remember(audio) { mutableStateOf(audio.firstOrNull()?.uri?.toString()) }
    val selected = audio.firstOrNull { it.uri.toString() == selectedUri }
    val duration = selected?.durationMs?.coerceAtLeast(1_000L) ?: 1_000L
    var range by remember(selectedUri) { mutableStateOf(0f..duration.coerceAtMost(30_000L).toFloat()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ToolHeader("Ringtone Cutter", onBack)
            Text("Choose an MP3 or AAC/M4A track, then select the part to keep.", color = OmniTextMuted)
        }
        if (selected != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(selected.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${range.start.toLong().asDuration()} — ${range.endInclusive.toLong().asDuration()}",
                            color = OmniOrange,
                        )
                        RangeSlider(
                            value = range,
                            onValueChange = { range = it },
                            valueRange = 0f..duration.toFloat(),
                            steps = 0,
                            colors = SliderDefaults.colors(thumbColor = OmniOrange, activeTrackColor = OmniOrange),
                        )
                        Button(
                            onClick = { onCreate(selected, range.start.toLong(), range.endInclusive.toLong()) },
                            enabled = !status.running && range.endInclusive - range.start >= 1_000f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = OmniOrange),
                        ) {
                            if (status.running) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ContentCut, null)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(if (status.running) "Creating clip…" else "Create audio clip")
                        }
                    }
                }
            }
        }
        item { ToolStatus(status) }
        item { Text("Select source audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (audio.isEmpty()) {
            item { EmptyState("No audio found", "Grant media access or add audio to your device.") }
        } else {
            items(audio, key = { it.uri.toString() }) { item ->
                ToolSourceCard(item, selectedUri == item.uri.toString()) { selectedUri = item.uri.toString() }
            }
        }
    }
}

@Composable
fun VideoToAudioScreen(
    videos: List<OmniMedia>,
    status: MediaToolState,
    onBack: () -> Unit,
    onExtract: (OmniMedia) -> Unit,
) {
    var selectedUri by remember(videos) { mutableStateOf(videos.firstOrNull()?.uri?.toString()) }
    val selected = videos.firstOrNull { it.uri.toString() == selectedUri }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ToolHeader("Video to Audio", onBack)
            Text(
                "Extract the original AAC audio track from a local video without lowering its quality.",
                color = OmniTextMuted,
            )
        }
        if (selected != null) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MediaArtwork(selected, Modifier.size(64.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(selected.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(selected.durationMs.asDuration(), color = OmniTextMuted)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onExtract(selected) },
                            enabled = !status.running,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = OmniOrange),
                        ) {
                            if (status.running) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AudioFile, null)
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(if (status.running) "Extracting audio…" else "Save as M4A")
                        }
                    }
                }
            }
        }
        item { ToolStatus(status) }
        item { Text("Select source video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (videos.isEmpty()) {
            item { EmptyState("No videos found", "Grant media access or add a video to your device.") }
        } else {
            items(videos, key = { it.uri.toString() }) { item ->
                ToolSourceCard(item, selectedUri == item.uri.toString()) { selectedUri = item.uri.toString() }
            }
        }
    }
}

@Composable
private fun ToolHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ToolSourceCard(media: OmniMedia, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, OmniOrange) else null,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArtwork(media, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${media.artist} • ${media.durationMs.asDuration()}", color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (media.kind == MediaKind.VIDEO) Icons.Rounded.Movie else Icons.Rounded.AudioFile, null, tint = if (selected) OmniOrange else OmniTextMuted)
        }
    }
}

@Composable
private fun ToolStatus(status: MediaToolState) {
    val text = status.error ?: status.message ?: return
    Surface(
        color = if (status.error == null) Color(0xFF123120) else Color(0xFF3A1515),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            color = if (status.error == null) OmniSuccess else Color(0xFFFF9A9A),
            modifier = Modifier.padding(12.dp),
        )
    }
}
