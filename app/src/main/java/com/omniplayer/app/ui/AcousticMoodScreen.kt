package com.omniplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniplayer.app.mood.AcousticAnalysisState

@Composable
fun AcousticMoodScreen(
    state: AcousticAnalysisState,
    analyzedSongs: Int,
    librarySongs: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRerun: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val progress = if (state.total > 0) state.completed.toFloat() / state.total else 0f
    OmniBackground {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column {
                    Text("Acoustic analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Optional · On-device · Beta", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Listen to the sound, not just the filename", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "When you start it, Omni privately samples each song's audio and measures energy, tempo, brightness and dynamics. Unsupported files are skipped safely. Nothing is uploaded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "It never runs automatically. You can stop it at any time, and completed songs are kept so Resume skips them. Keep Omni open while it works.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (state.running) "Analyzing library" else "Analysis status", fontWeight = FontWeight.Bold)
                        Text("$analyzedSongs / $librarySongs songs", color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.running) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            state.currentTitle.ifBlank { "Preparing audio…" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.failed > 0) Text("${state.failed} files could not be decoded", style = MaterialTheme.typography.bodySmall)
                    } else if (analyzedSongs > 0) {
                        Text("Saved analysis is active in your mood recommendations.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Not started. Metadata recommendations continue to work normally.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.running) {
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Rounded.Pause, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pause analysis")
                }
            } else {
                Button(onClick = onStart, enabled = librarySongs > 0, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (analyzedSongs > 0 && analyzedSongs < librarySongs) "Resume remaining songs" else "Start acoustic analysis")
                }
            }
            if (!state.running && analyzedSongs > 0) {
                OutlinedButton(onClick = onRerun, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reanalyze every song")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove acoustic results")
                }
            }
        }
    }
}
