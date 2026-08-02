package com.omniplayer.app.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.WorkInfo
import com.omniplayer.app.download.DownloadJob
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted
import java.io.File
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun DownloadPreviewScreen(
    job: DownloadJob?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    val latestJob by rememberUpdatedState(job)
    var playerMessage by remember { mutableStateOf<String?>(null) }
    var loadedSource by remember { mutableStateOf<String?>(null) }
    var observedLength by remember { mutableLongStateOf(0L) }

    fun load(current: DownloadJob, preservePosition: Boolean) {
        val source = current.previewSource(context) ?: return
        val key = source.uri.toString()
        val position = if (preservePosition) player.currentPosition.coerceAtLeast(0L) else 0L
        val item = MediaItem.Builder().setUri(source.uri).apply {
            source.mimeType?.let(::setMimeType)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        if (position > 0L) player.seekTo(position)
        player.playWhenReady = true
        loadedSource = key
        observedLength = source.file?.length() ?: 0L
        playerMessage = null
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playerMessage = "This part is not playable yet. Omni will retry as more video arrives."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(job?.previewPath, job?.outputUri, job?.state) {
        val current = job ?: return@LaunchedEffect
        val source = current.previewSource(context) ?: return@LaunchedEffect
        if (source.uri.toString() != loadedSource || current.state == WorkInfo.State.SUCCEEDED) {
            load(current, preservePosition = loadedSource != null)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500L)
            val current = latestJob ?: continue
            if (current.state.isFinished) continue
            val source = current.previewSource(context) ?: continue
            val length = source.file?.length() ?: continue
            if (length > observedLength && (player.playerError != null || player.playbackState == Player.STATE_ENDED)) {
                load(current, preservePosition = true)
            }
            observedLength = length
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    job?.name ?: "Download preview",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (job?.state == WorkInfo.State.SUCCEEDED) "Download complete" else "Playing the available part",
                    color = OmniTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(color = OmniOrange.copy(alpha = 0.18f), shape = RoundedCornerShape(20.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Downloading, null, tint = OmniOrange)
                    Text(" ${job?.progress ?: 0}%", color = OmniOrange, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF050505)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { playerContext ->
                    PlayerView(playerContext).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        controllerShowTimeoutMs = 2_500
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            if (job?.previewSource(context) == null) {
                Text("Waiting for playable video data…", color = OmniTextMuted)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Live download preview", color = Color.White, style = MaterialTheme.typography.titleLarge)
            LinearProgressIndicator(
                progress = { (job?.progress ?: 0) / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = OmniOrange,
                trackColor = OmniOutline,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${job?.bytes?.asFileSize() ?: "0 B"}${job?.total?.takeIf { it > 0L }?.let { " / ${it.asFileSize()}" }.orEmpty()}",
                    color = OmniTextMuted,
                    modifier = Modifier.weight(1f),
                )
                job?.speedBytesPerSecond?.takeIf { it > 0L }?.let {
                    Text("${it.asFileSize()}/s", color = OmniOrange)
                }
            }
            playerMessage?.let {
                Surface(color = OmniSurfaceHigh, shape = RoundedCornerShape(14.dp)) {
                    Text(it, color = OmniTextMuted, modifier = Modifier.padding(12.dp))
                }
            }
            Surface(
                color = OmniSurfaceHigh,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, OmniOutline),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = OmniOrange)
                    Spacer(Modifier.padding(5.dp))
                    Text(
                        "Preview is best effort. Some MP4 and WebM files become playable only after their final merge; HLS video usually starts sooner.",
                        color = OmniTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private data class PreviewSource(val uri: Uri, val mimeType: String?, val file: File?)

@OptIn(UnstableApi::class)
private fun DownloadJob.previewSource(context: Context): PreviewSource? {
    if (state == WorkInfo.State.SUCCEEDED) {
        return outputUri?.let { PreviewSource(Uri.parse(it), mimeType, null) }
    }
    val path = previewPath ?: return null
    val root = File(context.cacheDir, "omni-ytdlp/$id")
    val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
    val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
    if (!candidate.path.startsWith(canonicalRoot.path + File.separator) || !candidate.isFile || candidate.length() <= 0L) {
        return null
    }
    val protocol = previewProtocol.orEmpty().lowercase()
    val name = candidate.name.lowercase()
    val mime = when {
        "m3u8" in protocol || "hls" in protocol || name.endsWith(".ts") || name.endsWith(".m2ts") -> MimeTypes.VIDEO_MP2T
        ".webm" in name -> MimeTypes.VIDEO_WEBM
        ".mkv" in name -> MimeTypes.VIDEO_MATROSKA
        else -> MimeTypes.VIDEO_MP4
    }
    return PreviewSource(Uri.fromFile(candidate), mime, candidate)
}
