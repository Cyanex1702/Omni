package com.omniplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.omniplayer.app.download.DownloadJob
import com.omniplayer.app.download.DownloadRequest
import com.omniplayer.app.download.asMedia
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniSuccess
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted
import java.util.UUID

@Composable
fun DownloadsScreen(
    jobs: List<DownloadJob>,
    contentPadding: PaddingValues,
    onNewDownload: () -> Unit,
    onPlay: (DownloadJob) -> Unit,
    onPreview: (DownloadJob) -> Unit,
    onCancel: (UUID) -> Unit,
    onRetry: (DownloadJob) -> Boolean,
    onClearFinished: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(true) }
    val active = jobs.filter { !it.state.isFinished }
    val finished = jobs.filter { it.state.isFinished }
    val visible = if (activeTab) active else finished

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 92.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                        Text("Manage active and completed media", color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!activeTab && finished.isNotEmpty()) {
                        IconButton(onClick = onClearFinished) {
                            Icon(Icons.Rounded.DeleteSweep, "Clear download history")
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmniSurfaceHigh, RoundedCornerShape(15.dp))
                        .padding(4.dp),
                ) {
                    DownloadTab("Active  ${active.size}", activeTab, Modifier.weight(1f)) { activeTab = true }
                    DownloadTab("Completed  ${finished.size}", !activeTab, Modifier.weight(1f)) { activeTab = false }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        if (activeTab) "No active downloads" else "No finished downloads",
                        if (activeTab) "Paste a supported webpage or direct media link to begin."
                        else "Completed downloads will be listed here.",
                    )
                }
            } else {
                items(visible, key = { it.id }) { job ->
                    DownloadCard(
                        job = job,
                        onPlay = { onPlay(job) },
                        onPreview = { onPreview(job) },
                        onCancel = { onCancel(job.id) },
                        onRetry = { onRetry(job) },
                    )
                }
            }

            if (active.isNotEmpty()) {
                item {
                    val average = active.map { it.progress }.average().toInt()
                    Surface(
                        color = OmniSurfaceHigh,
                        shape = RoundedCornerShape(17.dp),
                        border = BorderStroke(1.dp, OmniOutline),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row {
                                Text("Overall progress", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("$average%", color = OmniOrange)
                            }
                            Spacer(Modifier.height(9.dp))
                            LinearProgressIndicator(
                                progress = { average / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = OmniOrange,
                            )
                            Spacer(Modifier.height(7.dp))
                            Text("${active.size} active task${if (active.size == 1) "" else "s"}", color = OmniTextMuted)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onNewDownload,
            containerColor = OmniOrange,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        ) { Icon(Icons.Rounded.Link, "New download") }
    }
}

@Composable
private fun DownloadTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else OmniTextMuted, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DownloadCard(
    job: DownloadJob,
    onPlay: () -> Unit,
    onPreview: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val playable = job.asMedia()
    val engineDetail = job.errorDetail?.trim()?.takeIf { detail ->
        detail.isNotBlank() && !detail.equals(job.error?.trim(), ignoreCase = true)
    }
    Surface(
        modifier = Modifier.clickable(enabled = playable != null, onClick = onPlay),
        color = OmniSurfaceHigh,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, OmniOutline.copy(alpha = 0.78f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playable != null) {
                MediaArtwork(playable, Modifier.size(width = 72.dp, height = 56.dp))
            } else {
                Box(
                    modifier = Modifier.size(width = 72.dp, height = 56.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                Icon(
                    when {
                        job.state == WorkInfo.State.SUCCEEDED -> Icons.Rounded.CheckCircle
                        job.state == WorkInfo.State.FAILED -> Icons.Rounded.Cancel
                        else -> Icons.Rounded.Download
                    },
                    null,
                    tint = when (job.state) {
                        WorkInfo.State.SUCCEEDED -> OmniSuccess
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> Color(0xFFFF5B5B)
                        else -> OmniOrange
                    },
                )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    DownloadBadge(if (job.requestedType == "audio") "MP3" else "VIDEO")
                    job.quality?.takeIf(String::isNotBlank)?.let { DownloadBadge(if (it == "best") "BEST" else it) }
                    Text(
                        job.stage,
                        color = if (job.state == WorkInfo.State.FAILED) Color(0xFFFF7070) else OmniTextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (!job.state.isFinished) {
                    LinearProgressIndicator(
                        progress = { job.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = OmniOrange,
                        trackColor = OmniOutline,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        buildString {
                            append(job.bytes.asFileSize())
                            if (job.total > 0) append(" / ${job.total.asFileSize()}")
                            append("  •  ${job.progress}%")
                            if (job.speedBytesPerSecond > 0L) append("  •  ${job.speedBytesPerSecond.asFileSize()}/s")
                            if (job.etaSeconds > 0L) append("  •  ${formatDownloadEta(job.etaSeconds)} left")
                        },
                        color = OmniTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        job.error ?: job.state.name.lowercase().replaceFirstChar(Char::uppercase),
                        color = if (job.state == WorkInfo.State.SUCCEEDED) OmniSuccess else OmniTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (job.state == WorkInfo.State.FAILED && engineDetail != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Engine: $engineDetail",
                            color = OmniTextMuted.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (!job.state.isFinished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (job.requestedType == DownloadRequest.TYPE_VIDEO && job.previewPath != null) {
                        IconButton(onClick = onPreview) {
                            Icon(Icons.Rounded.PlayArrow, "Preview while downloading", tint = OmniOrange)
                        }
                    }
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Cancel, "Cancel") }
                }
            } else if (playable != null) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, "Play downloaded media", tint = OmniOrange)
                }
            } else if (job.state == WorkInfo.State.FAILED && job.sourceUrl != null) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, "Retry download", tint = OmniOrange)
                }
            }
        }
    }
}

private fun formatDownloadEta(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = safe % 3_600L / 60L
    val remainder = safe % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${remainder}s"
        else -> "${remainder}s"
    }
}

@Composable
private fun DownloadBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)) {
        Text(
            label.uppercase(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = OmniTextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun NewDownloadScreen(
    initialUrl: String,
    wifiOnly: Boolean,
    onBack: () -> Unit,
    onDownload: (String, Boolean, String) -> Unit,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var audio by rememberSaveable { mutableStateOf(true) }
    var audioQuality by rememberSaveable { mutableStateOf("320") }
    var videoQuality by rememberSaveable { mutableStateOf("1080") }
    val normalizedUrl = DownloadRequest.extractUrl(url)
    val valid = normalizedUrl != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("New download", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Text("Paste media or webpage URL", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                placeholder = { Text("https://website.com/watch/...") },
                supportingText = {
                    Text("Omni analyzes supported websites and direct media links with yt-dlp.")
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = OmniSurfaceHigh,
                    unfocusedContainerColor = OmniSurfaceHigh,
                    focusedBorderColor = OmniOrange,
                    unfocusedBorderColor = OmniOutline,
                ),
            )
        }
        item {
            Text("Media type", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FormatCard(
                    label = "Audio file",
                    detail = "MP3 + artwork",
                    icon = Icons.Rounded.AudioFile,
                    selected = audio,
                    modifier = Modifier.weight(1f),
                ) { audio = true }
                FormatCard(
                    label = "Video file",
                    detail = "MP4 / WebM / MKV",
                    icon = Icons.Rounded.VideoFile,
                    selected = !audio,
                    modifier = Modifier.weight(1f),
                ) { audio = false }
            }
        }
        item {
            Text(if (audio) "Audio quality" else "Maximum video quality", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val options = if (audio) {
                    listOf("128" to "128 kbps", "192" to "192 kbps", "320" to "320 kbps")
                } else {
                    listOf("360" to "360p", "720" to "720p", "1080" to "1080p", "best" to "Best")
                }
                val selected = if (audio) audioQuality else videoQuality
                options.forEach { (value, label) ->
                    QualityOption(
                        label = label,
                        selected = selected == value,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (audio) audioQuality = value else videoQuality = value
                    }
                }
            }
        }
        item {
            Surface(
                color = OmniSurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, OmniOutline),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("How Omni downloads", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Omni finds the media streams, downloads them, then uses FFmpeg to merge or convert them into a playable file.",
                        color = OmniTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (wifiOnly) "Wi-Fi-only downloading is enabled in Settings."
                        else "Downloads can use Wi-Fi or mobile data.",
                        color = OmniOrange,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    onDownload(
                        normalizedUrl.orEmpty(),
                        audio,
                        if (audio) audioQuality else videoQuality,
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OmniOrange),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(9.dp))
                Text("Analyze & add to queue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QualityOption(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) OmniOrange else OmniSurfaceHigh,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) OmniOrange else OmniOutline,
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) Color.White else OmniTextMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FormatCard(
    label: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) OmniOrange.copy(alpha = 0.14f) else OmniSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) OmniOrange else OmniOutline),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (selected) OmniOrange else Color.White, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, fontWeight = FontWeight.Bold)
            Text(detail, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
