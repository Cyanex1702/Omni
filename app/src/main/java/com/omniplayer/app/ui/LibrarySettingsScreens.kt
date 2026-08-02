package com.omniplayer.app.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniplayer.app.data.OmniSettings
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniPink
import com.omniplayer.app.ui.theme.OmniPurple
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted

private enum class LibraryFilter(val label: String) { SONGS("Songs"), VIDEOS("Videos"), FAVORITES("Favorites") }

@Composable
fun LibraryScreen(
    media: List<OmniMedia>,
    favoriteUris: Set<String>,
    contentPadding: PaddingValues,
    onPlay: (OmniMedia) -> Unit,
    onFavorite: (OmniMedia) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryFilter.SONGS) }
    val visible = when (filter) {
        LibraryFilter.SONGS -> media.filter { it.kind == MediaKind.AUDIO }
        LibraryFilter.VIDEOS -> media.filter { it.kind == MediaKind.VIDEO }
        LibraryFilter.FAVORITES -> media.filter { it.uri.toString() in favoriteUris }
    }

    val storage = remember {
        runCatching {
            val stats = StatFs(Environment.getDataDirectory().absolutePath)
            val total = stats.totalBytes.coerceAtLeast(1L)
            val used = (total - stats.availableBytes).coerceIn(0L, total)
            used to total
        }.getOrDefault(0L to 1L)
    }
    val libraryBytes = media.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 22.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OmniHeader("Library", subtitle = "${media.size} media files on this device", action = {
                    BrandLogo(
                        modifier = Modifier.size(42.dp).clickable(onClick = onOpenSettings),
                        circular = true,
                    )
                })
                Spacer(Modifier.height(14.dp))
            }

            item {
                OmniCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (storage.first.toFloat() / storage.second).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = OmniOrange,
                                trackColor = OmniOutline,
                                strokeWidth = 7.dp,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(storage.first * 100 / storage.second)}%", fontWeight = FontWeight.Bold)
                                Text("used", color = OmniTextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 18.dp)) {
                            Text("Device storage", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(5.dp))
                            Text("${storage.first.asFileSize()} of ${storage.second.asFileSize()}", fontWeight = FontWeight.SemiBold)
                            Text("Omni library: ${libraryBytes.asFileSize()}", color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                modifier = Modifier.clickable(onClick = onRefresh),
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, OmniOutline),
                            ) {
                                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                    Text("Rescan library", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            item {
                LibraryCollectionRow(Icons.Rounded.Favorite, "Favorites", "${favoriteUris.size} saved", OmniPink) {
                    filter = LibraryFilter.FAVORITES
                }
            }
            item {
                LibraryCollectionRow(
                    Icons.Rounded.MusicNote,
                    "Songs",
                    "${media.count { it.kind == MediaKind.AUDIO }} tracks",
                    OmniPurple,
                ) { filter = LibraryFilter.SONGS }
            }
            item {
                LibraryCollectionRow(
                    Icons.Rounded.VideoLibrary,
                    "Videos",
                    "${media.count { it.kind == MediaKind.VIDEO }} videos",
                    Color(0xFFB793FF),
                ) { filter = LibraryFilter.VIDEOS }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(OmniSurfaceHigh, RoundedCornerShape(15.dp))
                        .padding(4.dp),
                ) {
                    LibraryFilter.entries.forEach { item ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable { filter = item },
                            color = if (filter == item) OmniOrange else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                item.label,
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = if (filter == item) Color.White else OmniTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (visible.isEmpty()) {
                item {
                    EmptyState(
                        if (filter == LibraryFilter.FAVORITES) "No favorites yet" else "No media found",
                        if (filter == LibraryFilter.FAVORITES) "Tap the heart beside a song or video to save it."
                        else "Add files to your device, then rescan the library.",
                    )
                }
            } else {
                items(visible, key = { it.uri.toString() }) { item ->
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
private fun LibraryCollectionRow(
    icon: ImageVector,
    label: String,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    OmniCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp)) }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(description, color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = OmniTextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    settings: OmniSettings,
    contentPadding: PaddingValues,
    onWifiOnly: (Boolean) -> Unit,
    onResume: (Boolean) -> Unit,
    onGapless: (Boolean) -> Unit,
    onTheme: (String) -> Unit,
    onPlayerAppearance: (String) -> Unit,
    onSimultaneousDownloads: (Int) -> Unit,
    onEqualizer: () -> Unit,
    onRingtoneCutter: () -> Unit,
    onVideoToAudio: () -> Unit,
) {
    var information by remember { mutableStateOf<SettingsInformation?>(null) }
    information?.let { info ->
        AlertDialog(
            onDismissRequest = { information = null },
            title = { Text(info.title) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (info.showLogo) {
                        BrandLogo(Modifier.size(112.dp), circular = true)
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(info.message)
                }
            },
            confirmButton = { TextButton(onClick = { information = null }) { Text("Done") } },
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = contentPadding.calculateTopPadding() + 18.dp,
            bottom = contentPadding.calculateBottomPadding() + 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OmniHeader("Settings", subtitle = "Tune Omni to the way you listen")
        }
        item {
            SettingsGroup("APPEARANCE") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Visual Theme", modifier = Modifier.padding(start = 12.dp).weight(1f), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("amoled" to "Immersive AMOLED", "editorial" to "Editorial Luxe").forEach { (theme, label) ->
                        AssistChip(
                            onClick = { onTheme(theme) },
                            label = { Text(label) },
                            leadingIcon = if (settings.theme == theme) {
                                { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))) }
                            } else null,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Player Appearance", fontWeight = FontWeight.SemiBold)
                Text(
                    "Used only for music and audio files",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("square" to "Square Cover", "vinyl" to "Vinyl Disc", "wave" to "Wave Circle").forEach { (style, label) ->
                        AssistChip(
                            onClick = { onPlayerAppearance(style) },
                            label = { Text(label) },
                            leadingIcon = if (settings.playerAppearance == style) {
                                { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))) }
                            } else null,
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup("AUDIO") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionTile(Icons.Rounded.Equalizer, "Equalizer", Modifier.weight(1f), onEqualizer)
                    SettingsActionTile(Icons.Rounded.ContentCut, "Ringtone Cutter", Modifier.weight(1f), onRingtoneCutter)
                    SettingsActionTile(Icons.Rounded.VideoFile, "Video to Audio", Modifier.weight(1f), onVideoToAudio)
                }
            }
        }
        item {
            SettingsGroup("DOWNLOADS") {
                SettingsToggle(
                    Icons.Rounded.Download,
                    "Download only on Wi-Fi",
                    "Reduces mobile data usage",
                    settings.wifiOnly,
                    onWifiOnly,
                )
                Column {
                    Text("Simultaneous tasks limit", fontWeight = FontWeight.SemiBold)
                    Text("Controls parallel download tasks", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { count ->
                            AssistChip(
                                onClick = { onSimultaneousDownloads(count) },
                                label = { Text("$count task${if (count == 1) "" else "s"}") },
                                leadingIcon = if (settings.simultaneousDownloads == count) {
                                    { Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))) }
                                } else null,
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsGroup("PLAYBACK") {
                SettingsToggle(
                    Icons.Rounded.LibraryMusic,
                    "Resume playback position",
                    "Continue where you stopped",
                    settings.resumePlayback,
                    onResume,
                )
                SettingsToggle(
                    Icons.Rounded.MusicNote,
                    "Gapless playback",
                    "Reduce silence between compatible tracks",
                    settings.gaplessPlayback,
                    onGapless,
                )
            }
        }
        item {
            SettingsGroup("PRIVACY & ABOUT") {
                SettingsLink(Icons.Rounded.PrivacyTip, "Privacy", "No account, ads, analytics or tracking") {
                    information = SettingsInformation(
                        "Privacy",
                        "Omni keeps favorites, recents, playback position, and settings on this device. It sends no analytics, advertising identifiers, or account data.",
                    )
                }
                SettingsLink(Icons.Rounded.Folder, "Download folders", "Music/Omni and Movies/Omni") {
                    information = SettingsInformation(
                        "Download folders",
                        "Audio is saved in Music/Omni, video in Movies/Omni, and media-tool output in Music/Omni/Tools.",
                    )
                }
                SettingsLink(Icons.Rounded.Info, "About Omni Player", "Version 1.2.1 • free and ad-free") {
                    information = SettingsInformation(
                        "Omni Player 1.2.1",
                        "A private local media player and yt-dlp-powered downloader. Free, ad-free, account-free, and GPLv3.\n\nMade by Cynex1702.",
                        showLogo = true,
                    )
                }
            }
        }
    }
}

private data class SettingsInformation(
    val title: String,
    val message: String,
    val showLogo: Boolean = false,
)

@Composable
private fun SettingsActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(7.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(17.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
    }
}
