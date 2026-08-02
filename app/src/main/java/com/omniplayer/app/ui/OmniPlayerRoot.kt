package com.omniplayer.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.omniplayer.app.MainViewModel
import com.omniplayer.app.download.DownloadRequest
import com.omniplayer.app.download.DownloadWorker
import com.omniplayer.app.download.asMedia
import com.omniplayer.app.model.MediaKind
import com.omniplayer.app.ui.theme.OmniOrange
import com.omniplayer.app.ui.theme.OmniOutline
import com.omniplayer.app.ui.theme.OmniSurface

private data class MainDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val mainDestinations = listOf(
    MainDestination("home", "For You", Icons.Rounded.Home),
    MainDestination("browse", "Explore", Icons.Rounded.Explore),
    MainDestination("downloads", "Downloads", Icons.Rounded.Download),
    MainDestination("library", "Library", Icons.Rounded.LibraryMusic),
)

@Composable
fun OmniPlayerRoot(
    viewModel: MainViewModel,
    initialSharedUrl: String,
    requestMediaPermission: () -> Unit,
) {
    val navController = rememberNavController()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteUris.collectAsStateWithLifecycle()
    val recentUris by viewModel.recentUris.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val equalizer by viewModel.equalizer.collectAsStateWithLifecycle()
    val mediaToolState by viewModel.mediaToolState.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showNavigation = currentRoute in mainDestinations.map { it.route } || currentRoute == "settings"
    val currentPlayerRoute = if (playback.current?.kind == MediaKind.VIDEO && !playback.playAsAudio) {
        "video_player"
    } else {
        "now_playing"
    }

    LaunchedEffect(Unit) { requestMediaPermission() }
    LaunchedEffect(initialSharedUrl) {
        DownloadRequest.extractUrl(initialSharedUrl)?.let { sharedUrl ->
            navController.navigate("new_download?url=${Uri.encode(sharedUrl)}") {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showNavigation) {
                Column {
                    MiniPlayer(
                        state = playback,
                        onOpen = { navController.navigate(currentPlayerRoute) },
                        onToggle = viewModel.playbackController::togglePlayPause,
                        onQueue = { navController.navigate("queue") },
                    )
                    Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp) {
                            mainDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(destination.icon, destination.label) },
                                    label = { Text(destination.label) },
                                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("home") {
                HomeScreen(
                    media = media,
                    recentMedia = recentUris.mapNotNull { uri -> media.firstOrNull { it.uri.toString() == uri } },
                    favoriteUris = favorites,
                    query = query,
                    loading = loading,
                    contentPadding = padding,
                    onQuery = viewModel::setQuery,
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(if (item.kind == MediaKind.VIDEO) "video_player" else "now_playing")
                    },
                    onFavorite = viewModel::toggleFavorite,
                    onOpenLibrary = { navController.navigate("library") },
                    onNotifications = { navController.navigate("downloads") },
                    onNewDownload = { text ->
                        val shared = DownloadRequest.extractUrl(text).orEmpty()
                        navController.navigate("new_download?url=${Uri.encode(shared)}")
                    },
                )
            }
            composable("browse") {
                BrowseScreen(
                    media = media,
                    favoriteUris = favorites,
                    contentPadding = padding,
                    onNewDownload = { navController.navigate("new_download") },
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(if (item.kind == MediaKind.VIDEO) "video_player" else "now_playing")
                    },
                    onFavorite = viewModel::toggleFavorite,
                    onOpenLibrary = { navController.navigate("library") },
                    onNotifications = { navController.navigate("downloads") },
                )
            }
            composable("downloads") {
                DownloadsScreen(
                    jobs = downloads,
                    contentPadding = padding,
                    onNewDownload = { navController.navigate("new_download") },
                    onPlay = { job ->
                        if (viewModel.playDownloaded(job)) {
                            navController.navigate(if (job.asMedia()?.kind == MediaKind.VIDEO) "video_player" else "now_playing")
                        }
                    },
                    onPreview = { job -> navController.navigate("download_preview/${job.id}") },
                    onCancel = viewModel::cancelDownload,
                    onRetry = viewModel::retryDownload,
                    onClearFinished = viewModel::clearFinishedDownloads,
                )
            }
            composable("download_preview/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                DownloadPreviewScreen(
                    job = downloads.firstOrNull { it.id.toString() == id },
                    onBack = navController::popBackStack,
                )
            }
            composable("library") {
                LibraryScreen(
                    media = media,
                    favoriteUris = favorites,
                    contentPadding = padding,
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(if (item.kind == MediaKind.VIDEO) "video_player" else "now_playing")
                    },
                    onFavorite = viewModel::toggleFavorite,
                    onRefresh = viewModel::refreshMedia,
                    onOpenSettings = { navController.navigate("settings") },
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = settings,
                    contentPadding = padding,
                    onWifiOnly = viewModel::setWifiOnly,
                    onResume = viewModel::setResumePlayback,
                    onGapless = viewModel::setGaplessPlayback,
                    onTheme = viewModel::setTheme,
                    onPlayerAppearance = viewModel::setPlayerAppearance,
                    onSimultaneousDownloads = viewModel::setSimultaneousDownloads,
                    onEqualizer = { navController.navigate("equalizer") },
                    onRingtoneCutter = {
                        viewModel.clearMediaToolStatus()
                        navController.navigate("ringtone_cutter")
                    },
                    onVideoToAudio = {
                        viewModel.clearMediaToolStatus()
                        navController.navigate("video_to_audio")
                    },
                )
            }
            composable("new_download?url={url}") { entry ->
                NewDownloadScreen(
                    initialUrl = entry.arguments?.getString("url").orEmpty(),
                    wifiOnly = settings.wifiOnly,
                    onBack = navController::popBackStack,
                    onDownload = { url, audio, quality ->
                        viewModel.enqueueDownload(
                            url,
                            if (audio) DownloadWorker.TYPE_AUDIO else DownloadWorker.TYPE_VIDEO,
                            quality,
                        )
                        navController.navigate("downloads") {
                            popUpTo("downloads") { inclusive = true }
                        }
                    },
                )
            }
            composable("now_playing") {
                NowPlayingScreen(
                    state = playback,
                    current = viewModel.currentMedia(),
                    controller = viewModel.playbackController,
                    onBack = navController::popBackStack,
                    onQueue = { navController.navigate("queue") },
                    onEqualizer = { navController.navigate("equalizer") },
                    onSleepTimer = viewModel::setSleepTimer,
                    playerAppearance = settings.playerAppearance,
                    isFavorite = viewModel.currentMedia()?.uri.toString() in favorites,
                    onFavorite = { viewModel.currentMedia()?.let(viewModel::toggleFavorite) },
                )
            }
            composable("video_player") {
                VideoPlayerScreen(
                    state = playback,
                    current = viewModel.currentMedia(),
                    controller = viewModel.playbackController,
                    onBack = navController::popBackStack,
                    onQueue = { navController.navigate("queue") },
                    onDownload = { navController.navigate("new_download") },
                    onPlayAsAudio = {
                        viewModel.playbackController.setPlayAsAudio(true)
                        navController.navigate("now_playing") {
                            popUpTo("video_player") { inclusive = true }
                        }
                    },
                )
            }
            composable("queue") {
                QueueScreen(
                    state = playback,
                    onBack = navController::popBackStack,
                    onPlay = { item ->
                        viewModel.playQueue(item, playback.queue)
                        navController.navigate(if (item.kind == MediaKind.VIDEO) "video_player" else "now_playing") {
                            popUpTo("queue") { inclusive = true }
                        }
                    },
                    onClear = viewModel.playbackController::clearQueue,
                )
            }
            composable("equalizer") {
                EqualizerScreen(
                    state = equalizer,
                    onChange = viewModel::setEqualizer,
                    onBack = navController::popBackStack,
                )
            }
            composable("ringtone_cutter") {
                RingtoneCutterScreen(
                    audio = media.filter { it.kind == com.omniplayer.app.model.MediaKind.AUDIO },
                    status = mediaToolState,
                    onBack = navController::popBackStack,
                    onCreate = viewModel::trimAudio,
                )
            }
            composable("video_to_audio") {
                VideoToAudioScreen(
                    videos = media.filter { it.kind == com.omniplayer.app.model.MediaKind.VIDEO },
                    status = mediaToolState,
                    onBack = navController::popBackStack,
                    onExtract = viewModel::extractVideoAudio,
                )
            }
        }
    }
}
