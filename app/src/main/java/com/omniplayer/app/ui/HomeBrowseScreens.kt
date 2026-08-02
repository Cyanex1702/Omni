package com.omniplayer.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniPink
import com.omniplayer.app.ui.theme.OmniPurple
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted
import java.time.LocalTime

@Composable
fun HomeScreen(
    media: List<OmniMedia>,
    recentMedia: List<OmniMedia>,
    favoriteUris: Set<String>,
    query: String,
    loading: Boolean,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onPlay: (OmniMedia) -> Unit,
    onFavorite: (OmniMedia) -> Unit,
    onOpenLibrary: () -> Unit,
    onNotifications: () -> Unit,
    onNewDownload: (String) -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val searched = media.filter { item ->
        query.isBlank() || item.title.contains(query, true) ||
            item.artist.contains(query, true) || item.album.contains(query, true)
    }
    val recents = (recentMedia + media).distinctBy { it.uri.toString() }.take(8)
    val latest = searched.sortedByDescending { it.dateAddedSeconds }.take(8)

    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 22.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                        Text("Omni", style = MaterialTheme.typography.headlineSmall)
                    }
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Rounded.NotificationsNone, "Open downloads", tint = OmniTextMuted)
                    }
                    BrandLogo(Modifier.size(42.dp), circular = true)
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or paste a link", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        IconButton(onClick = { onNewDownload(query) }) {
                            Icon(Icons.Rounded.Link, "Open link downloader", tint = OmniOrange)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OmniSurfaceHigh,
                        unfocusedContainerColor = OmniSurfaceHigh,
                        focusedBorderColor = OmniOrange.copy(alpha = 0.8f),
                        unfocusedBorderColor = OmniOutline,
                    ),
                )
            }

            item {
                Surface(
                    color = Color.Transparent,
                    shape = OmniCardShape,
                    border = BorderStroke(1.dp, OmniOrange.copy(alpha = 0.62f)),
                ) {
                    Box(
                        Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF312018),
                                        Color(0xFF231B24),
                                        Color(0xFF171625),
                                    ),
                                ),
                            )
                            .padding(16.dp),
                    ) {
                        Column {
                            Text("Quick download", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("Paste any supported public media link", color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(14.dp))
                            AccentButton(
                                text = "Paste link",
                                icon = Icons.Rounded.Link,
                                onClick = {
                                    @Suppress("DEPRECATION")
                                    onNewDownload(clipboard.getText()?.text.orEmpty())
                                },
                                modifier = Modifier.fillMaxWidth(0.46f),
                            )
                        }
                    }
                }
            }

            if (recents.isNotEmpty()) {
                item {
                    OmniSectionHeading("Continue listening", "View all", onOpenLibrary)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        items(recents, key = { it.uri.toString() }) { item ->
                            ContinueCard(item, onClick = { onPlay(item) })
                        }
                    }
                }
            }

            item { OmniSectionHeading("Recently added", "View all", onOpenLibrary) }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OmniOrange)
                    }
                }
            } else if (latest.isEmpty()) {
                item {
                    EmptyState(
                        title = if (query.isBlank()) "Your library is ready" else "Nothing found",
                        message = if (query.isBlank()) {
                            "Add music and videos, or use Quick download to start your collection."
                        } else {
                            "Try another title, artist, or album."
                        },
                    )
                }
            } else {
                items(latest, key = { it.uri.toString() }) { item ->
                    MediaRow(
                        media = item,
                        isFavorite = item.uri.toString() in favoriteUris,
                        onPlay = { onPlay(item) },
                        onFavorite = { onFavorite(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: OmniMedia, onClick: () -> Unit) {
    Column(modifier = Modifier.width(142.dp).clickable(onClick = onClick)) {
        Box {
            MediaArtwork(item, Modifier.size(142.dp))
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp),
                shape = CircleShape,
                color = Color(0xC4101419),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

private data class Mood(val name: String, val icon: ImageVector, val color: Color)

@Composable
fun BrowseScreen(
    media: List<OmniMedia>,
    favoriteUris: Set<String>,
    contentPadding: PaddingValues,
    onNewDownload: () -> Unit,
    onPlay: (OmniMedia) -> Unit,
    onFavorite: (OmniMedia) -> Unit,
    onOpenLibrary: () -> Unit,
    onNotifications: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val audio = media.filter {
        it.kind == MediaKind.AUDIO &&
            (search.isBlank() || it.title.contains(search, true) || it.artist.contains(search, true))
    }
    val featured = audio.firstOrNull() ?: media.firstOrNull()
    val collections = audio.groupBy { it.album }.entries.take(6)
    val moods = listOf(
        Mood("Chill", Icons.Rounded.Headphones, OmniPurple),
        Mood("Workout", Icons.Rounded.FitnessCenter, OmniPink),
        Mood("Focus", Icons.Rounded.SportsEsports, Color(0xFF4C82FF)),
        Mood("Party", Icons.Rounded.LocalFireDepartment, OmniOrange),
        Mood("Romance", Icons.Rounded.Favorite, OmniPink),
        Mood("Sleep", Icons.Rounded.Bedtime, Color(0xFF6C65D8)),
    )

    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 22.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                OmniHeader("Explore", action = {
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Rounded.NotificationsNone, "Open downloads", tint = OmniTextMuted)
                    }
                })
            }

            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your collection") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = OmniSurfaceHigh,
                        unfocusedContainerColor = OmniSurfaceHigh,
                        focusedBorderColor = OmniPurple,
                        unfocusedBorderColor = OmniOutline,
                    ),
                )
            }

            item {
                Surface(shape = RoundedCornerShape(21.dp), color = Color.Transparent) {
                    Box(Modifier.fillMaxWidth().height(212.dp)) {
                        MediaArtwork(featured, Modifier.fillMaxSize())
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0x38100A19), Color(0xE813111B)),
                                ),
                            ),
                        )
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(18.dp),
                        ) {
                            Text(
                                featured?.album?.takeIf { it.isNotBlank() && it != "Unknown album" } ?: "Chill nights",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                featured?.artist?.takeIf { it.isNotBlank() } ?: "Music for the moment",
                                color = Color.White.copy(alpha = 0.76f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            AccentButton(
                                text = if (featured == null) "Add music" else "Play mix",
                                icon = if (featured == null) Icons.Rounded.Link else Icons.Rounded.PlayArrow,
                                onClick = { featured?.let(onPlay) ?: onNewDownload() },
                            )
                        }
                    }
                }
            }

            item {
                OmniSectionHeading("Browse by mood")
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    moods.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            row.forEach { mood -> MoodChip(mood, Modifier.weight(1f)) }
                        }
                    }
                }
            }

            item { OmniSectionHeading("Trending mixes", "View all", onOpenLibrary) }

            if (collections.isEmpty()) {
                item {
                    OmniCard(modifier = Modifier.fillMaxWidth(), onClick = onNewDownload) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).background(OmniOrange.copy(alpha = 0.16f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Rounded.Link, null, tint = OmniOrange) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text("Build your first mix", fontWeight = FontWeight.SemiBold)
                                Text("Download or add media to your library", color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Rounded.PlayArrow, null, tint = OmniTextMuted)
                        }
                    }
                }
            } else {
                items(collections, key = { it.key }) { collection ->
                    val first = collection.value.first()
                    MixRow(
                        title = collection.key,
                        subtitle = "${collection.value.size} tracks",
                        media = first,
                        onClick = { onPlay(first) },
                    )
                }
            }

            if (audio.isNotEmpty()) {
                item { OmniSectionHeading("From your library") }
                items(audio.take(6), key = { it.uri.toString() }) { item ->
                    MediaRow(
                        media = item,
                        isFavorite = item.uri.toString() in favoriteUris,
                        onPlay = { onPlay(item) },
                        onFavorite = { onFavorite(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodChip(mood: Mood, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = OmniSurfaceHigh,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, OmniOutline),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(mood.icon, null, tint = mood.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(mood.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun MixRow(
    title: String,
    subtitle: String,
    media: OmniMedia,
    onClick: () -> Unit,
) {
    OmniCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArtwork(media, Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, OmniOutline),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp)) } }
        }
    }
}
