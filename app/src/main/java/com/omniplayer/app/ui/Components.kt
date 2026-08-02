package com.omniplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.omniplayer.app.R
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.playback.PlaybackState
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniPink
import com.omniplayer.app.ui.theme.OmniPurple
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted

val OmniCardShape = RoundedCornerShape(18.dp)

@Composable
fun OmniBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ),
                ),
            ),
    ) { content() }
}

@Composable
fun OmniHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        action?.invoke()
    }
}

@Composable
fun OmniSectionHeading(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}

@Composable
fun OmniCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val clickable = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Surface(
        modifier = clickable,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = OmniCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
fun AccentButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
    ) {
        Icon(icon, null, Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    Image(
        painter = painterResource(R.drawable.omni_logo),
        contentDescription = "Omni",
        modifier = modifier
            .clip(if (circular) CircleShape else RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), if (circular) CircleShape else RoundedCornerShape(18.dp)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun MediaArtwork(
    media: OmniMedia?,
    modifier: Modifier = Modifier,
    circular: Boolean = false,
) {
    val shape = if (circular) CircleShape else RoundedCornerShape(14.dp)
    val source = media?.artworkUri ?: media?.uri?.takeIf { media.kind == MediaKind.VIDEO }
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (media?.kind == MediaKind.VIDEO) {
                        listOf(Color(0xFF132533), Color(0xFF713447), Color(0xFF17121D))
                    } else {
                        listOf(Color(0xFF2A143E), Color(0xFF7D2859), Color(0xFF131926))
                    },
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (media?.kind == MediaKind.VIDEO) Icons.Rounded.VideoFile else Icons.Rounded.AudioFile,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
        if (source != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(source).crossfade(true).build(),
                contentDescription = media?.title ?: "Media artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun MediaRow(
    media: OmniMedia,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onPlay)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArtwork(media, Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                media.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${media.artist}  •  ${media.sizeBytes.asFileSize()}  •  ${media.durationMs.asDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onQueue: () -> Unit,
) {
    if (state.title.isBlank()) return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    val isVideo = state.current?.kind == MediaKind.VIDEO && !state.playAsAudio
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isVideo) {
                    Box(
                        modifier = Modifier.width(82.dp).height(46.dp).clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaArtwork(state.current, Modifier.fillMaxSize())
                        Surface(color = Color(0x99000000), shape = CircleShape) {
                            Icon(
                                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.padding(4.dp).size(14.dp),
                            )
                        }
                    }
                } else {
                    MediaArtwork(state.current, Modifier.size(43.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        state.error ?: if (isVideo) "Video • ${state.positionMs.asDuration()}" else state.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (state.error == null) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFFF8A8A),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(42.dp)) {
                    if (state.buffering) {
                        CircularProgressIndicator(Modifier.size(21.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or pause",
                            tint = Color.White,
                        )
                    }
                }
                IconButton(onClick = onQueue, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = "Queue", tint = Color.White)
                }
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(OmniPurple, OmniPink, OmniOrange))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(34.dp), tint = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
