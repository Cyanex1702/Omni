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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.model.OmniMedia
import com.omniplayer.app.model.asDuration
import com.omniplayer.app.model.asFileSize
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniPurple
import com.omniplayer.app.ui.theme.OmniSurfaceHigh
import com.omniplayer.app.ui.theme.OmniTextMuted

enum class MediaSection { ALL, MUSIC, VIDEOS }
private enum class MediaSortOption(val label: String) {
    NEWEST_ADDED("Newest added"),
    RECENTLY_MODIFIED("Recently modified"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    DURATION_LONG("Longest first"),
    DURATION_SHORT("Shortest first"),
    SIZE_LARGE("Largest files"),
    SIZE_SMALL("Smallest files"),
}

private enum class MediaLibraryFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
}

private fun List<OmniMedia>.searchFilterAndSort(
    query: String,
    filter: MediaLibraryFilter,
    favoriteUris: Set<String>,
    sort: MediaSortOption,
): List<OmniMedia> {
    val searched = asSequence()
        .filter { filter == MediaLibraryFilter.ALL || it.uri.toString() in favoriteUris }
        .filter { item ->
            query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                item.artist.contains(query, ignoreCase = true) ||
                item.album.contains(query, ignoreCase = true) ||
                item.displayName.contains(query, ignoreCase = true)
        }
        .toList()
    return when (sort) {
        MediaSortOption.NEWEST_ADDED -> searched.sortedByDescending(OmniMedia::dateAddedSeconds)
        MediaSortOption.RECENTLY_MODIFIED -> searched.sortedByDescending {
            it.dateModifiedSeconds.takeIf { modified -> modified > 0L } ?: it.dateAddedSeconds
        }
        MediaSortOption.NAME_ASC -> searched.sortedBy { it.title.lowercase() }
        MediaSortOption.NAME_DESC -> searched.sortedByDescending { it.title.lowercase() }
        MediaSortOption.DURATION_LONG -> searched.sortedByDescending(OmniMedia::durationMs)
        MediaSortOption.DURATION_SHORT -> searched.sortedBy {
            it.durationMs.takeIf { duration -> duration > 0L } ?: Long.MAX_VALUE
        }
        MediaSortOption.SIZE_LARGE -> searched.sortedByDescending(OmniMedia::sizeBytes)
        MediaSortOption.SIZE_SMALL -> searched.sortedBy {
            it.sizeBytes.takeIf { size -> size > 0L } ?: Long.MAX_VALUE
        }
    }
}

@Composable
fun MediaSectionSwitcher(
    selected: MediaSection,
    onAll: () -> Unit,
    onMusic: () -> Unit,
    onVideos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destinations = listOf(
        Triple(MediaSection.ALL, "All", Icons.Rounded.Apps),
        Triple(MediaSection.MUSIC, "Music", Icons.Rounded.MusicNote),
        Triple(MediaSection.VIDEOS, "Videos", Icons.Rounded.VideoLibrary),
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = OmniSurfaceHigh,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, OmniOutline),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.forEach { (section, label, icon) ->
                val active = section == selected
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        when (section) {
                            MediaSection.ALL -> onAll()
                            MediaSection.MUSIC -> onMusic()
                            MediaSection.VIDEOS -> onVideos()
                        }
                    },
                    color = if (active) OmniOrange else Color.Transparent,
                    contentColor = if (active) Color.White else OmniTextMuted,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, null, Modifier.size(18.dp))
                        Text(" $label", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
@Composable
private fun MediaSearchSortControls(
    query: String,
    onQuery: (String) -> Unit,
    filter: MediaLibraryFilter,
    onFilter: (MediaLibraryFilter) -> Unit,
    sort: MediaSortOption,
    onSort: (MediaSortOption) -> Unit,
    resultCount: Int,
    itemLabel: String,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, "Clear search") }
                    }
                },
                placeholder = { Text("Search $itemLabel") },
                shape = RoundedCornerShape(17.dp),
            )
            Box {
                Surface(
                    onClick = { sortExpanded = true },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Sort, "Sort $itemLabel", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                    MediaSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == sort) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                else Icon(Icons.Rounded.Sort, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            onClick = {
                                onSort(option)
                                sortExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaFilterChip(
                selected = filter == MediaLibraryFilter.ALL,
                label = "All",
                icon = Icons.Rounded.Apps,
            ) { onFilter(MediaLibraryFilter.ALL) }
            MediaFilterChip(
                selected = filter == MediaLibraryFilter.FAVORITES,
                label = "Favorites",
                icon = Icons.Rounded.Favorite,
            ) { onFilter(MediaLibraryFilter.FAVORITES) }
            Spacer(Modifier.weight(1f))
            Text(
                "$resultCount results",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FilterList, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Sorted by ${sort.label}",
                modifier = Modifier.padding(start = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun MediaFilterChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MusicLibraryScreen(
    media: List<OmniMedia>,
    favoriteUris: Set<String>,
    contentPadding: PaddingValues,
    onAll: () -> Unit,
    onVideos: () -> Unit,
    onPlay: (OmniMedia) -> Unit,
    onFavorite: (OmniMedia) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(MediaLibraryFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(MediaSortOption.NEWEST_ADDED.name) }
    val filter = remember(filterName) { MediaLibraryFilter.valueOf(filterName) }
    val sort = remember(sortName) { MediaSortOption.valueOf(sortName) }
    val allSongs = media.filter { it.kind == MediaKind.AUDIO }
    val songs = remember(allSongs, query, filter, favoriteUris, sort) {
        allSongs.searchFilterAndSort(query.trim(), filter, favoriteUris, sort)
    }
    val albums = allSongs.map(OmniMedia::album).filter { it.isNotBlank() }.distinct().size
    OmniBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OmniHeader("Music", subtitle = "${songs.size} tracks • $albums albums")
                Spacer(Modifier.height(14.dp))
                MediaSectionSwitcher(
                    selected = MediaSection.MUSIC,
                    onAll = onAll,
                    onMusic = {},
                    onVideos = onVideos,
                )
                Spacer(Modifier.height(14.dp))
                MediaSearchSortControls(
                    query = query,
                    onQuery = { query = it },
                    filter = filter,
                    onFilter = { filterName = it.name },
                    sort = sort,
                    onSort = { sortName = it.name },
                    resultCount = songs.size,
                    itemLabel = "songs",
                )
            }
            item {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, OmniPurple.copy(alpha = 0.7f)),
                ) {
                    Box(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xFF28143B), Color(0xFF171520), Color(0xFF11151A))),
                        ).padding(18.dp),
                    ) {
                        Column {
                            Text("Your sound collection", color = OmniPurple, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text("Albums, songs and audio downloads", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Music stays here—video files have their own screen.",
                                color = OmniTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (allSongs.isEmpty()) {
                item { EmptyState("No music found", "Add audio files or download a track, then rescan your library.") }
            } else if (songs.isEmpty()) {
                item {
                    EmptyState(
                        "No matching music",
                        if (filter == MediaLibraryFilter.FAVORITES) {
                            "No favorite songs match this search. Try All or change the search text."
                        } else {
                            "Try a different song title, artist, album or filename."
                        },
                    )
                }
            } else {
                items(songs, key = { it.uri.toString() }) { song ->
                    MediaRow(
                        media = song,
                        isFavorite = song.uri.toString() in favoriteUris,
                        onPlay = { onPlay(song) },
                        onFavorite = { onFavorite(song) },
                    )
                }
            }
        }
    }
}

@Composable
fun VideoLibraryScreen(
    media: List<OmniMedia>,
    favoriteUris: Set<String>,
    contentPadding: PaddingValues,
    onAll: () -> Unit,
    onMusic: () -> Unit,
    onPlay: (OmniMedia) -> Unit,
    onFavorite: (OmniMedia) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(MediaLibraryFilter.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(MediaSortOption.NEWEST_ADDED.name) }
    val filter = remember(filterName) { MediaLibraryFilter.valueOf(filterName) }
    val sort = remember(sortName) { MediaSortOption.valueOf(sortName) }
    val allVideos = media.filter { it.kind == MediaKind.VIDEO }
    val videos = remember(allVideos, query, filter, favoriteUris, sort) {
        allVideos.searchFilterAndSort(query.trim(), filter, favoriteUris, sort)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                OmniHeader("Videos", subtitle = "${videos.size} videos • dedicated cinema library")
                Spacer(Modifier.height(14.dp))
                MediaSectionSwitcher(
                    selected = MediaSection.VIDEOS,
                    onAll = onAll,
                    onMusic = onMusic,
                    onVideos = {},
                )
                Spacer(Modifier.height(8.dp))
                MediaSearchSortControls(
                    query = query,
                    onQuery = { query = it },
                    filter = filter,
                    onFilter = { filterName = it.name },
                    sort = sort,
                    onSort = { sortName = it.name },
                    resultCount = videos.size,
                    itemLabel = "videos",
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (allVideos.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState("No videos found", "Add a video file or finish a video download to see it here.")
            }
        } else if (videos.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState(
                    "No matching videos",
                    if (filter == MediaLibraryFilter.FAVORITES) {
                        "No favorite videos match this search. Try All or change the search text."
                    } else {
                        "Try a different video title or filename."
                    },
                )
            }
        } else {
            items(videos, key = { it.uri.toString() }) { video ->
                VideoLibraryCard(
                    video = video,
                    favorite = video.uri.toString() in favoriteUris,
                    onPlay = { onPlay(video) },
                    onFavorite = { onFavorite(video) },
                )
            }
        }
    }
}

@Composable
private fun VideoLibraryCard(
    video: OmniMedia,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay),
        color = OmniSurfaceHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OmniOutline),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
                MediaArtwork(video, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xB3000000))),
                    ),
                )
                Surface(
                    modifier = Modifier.align(Alignment.Center).size(42.dp),
                    shape = CircleShape,
                    color = Color(0xD9FFFFFF),
                    contentColor = Color.Black,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, "Play ${video.title}")
                    }
                }
                Text(
                    video.durationMs.asDuration(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp)
                        .background(Color(0xC9000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(Modifier.padding(start = 11.dp, top = 10.dp, bottom = 11.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(video.sizeBytes.asFileSize(), color = OmniTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (favorite) "Remove favorite" else "Add favorite",
                        tint = if (favorite) OmniOrange else OmniTextMuted,
                    )
                }
            }
        }
    }
}
