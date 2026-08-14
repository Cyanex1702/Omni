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
import androidx.compose.runtime.remember
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
    val moodLibrary by viewModel.moodLibrary.collectAsStateWithLifecycle()
    val moodRecommendations by viewModel.moodRecommendations.collectAsStateWithLifecycle()
    val acousticProfiles by viewModel.acousticProfiles.collectAsStateWithLifecycle()
    val acousticState by viewModel.acousticAnalysisState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val equalizer by viewModel.equalizer.collectAsStateWithLifecycle()
    val mediaToolState by viewModel.mediaToolState.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val moods = moodLibrary.moods
    val moodCounts = remember(moodRecommendations) {
        moodRecommendations.mapValues { (_, recommendations) -> recommendations.size }
    }
    val currentAudio = playback.current?.takeIf { it.kind == MediaKind.AUDIO }
    val currentMoodMatches = remember(currentAudio, moodRecommendations) {
        val uri = currentAudio?.uri?.toString()
        if (uri == null) emptyList() else moodRecommendations.values
            .mapNotNull { recommendations -> recommendations.firstOrNull { it.media.uri.toString() == uri } }
            .sortedWith(compareByDescending<com.omniplayer.app.model.MoodRecommendation> { it.manuallyAssigned }.thenByDescending { it.score })
            .take(4)
    }
    val currentManualMoodIds = currentAudio?.let {
        moodLibrary.manualAssignments[it.uri.toString()].orEmpty()
    }.orEmpty()
    val librarySongs = remember(media) { media.filter { it.kind == MediaKind.AUDIO } }
    val analyzedSongs = remember(librarySongs, acousticProfiles) {
        librarySongs.count { song -> acousticProfiles[song.uri.toString()]?.isCurrent(song) == true }
    }
    val moodRoutes = setOf("moods", "mood/{id}", "acoustic_moods")
    val mediaLibraryRoutes = setOf("library", "music_library", "video_library")
    val showNavigation = currentRoute in mainDestinations.map { it.route } || currentRoute in moodRoutes ||
        currentRoute in mediaLibraryRoutes || currentRoute == "settings"
    val currentPlayerRoute = if (playback.current?.kind == MediaKind.VIDEO && !playback.playAsAudio) {
        "video_player"
    } else {
        "now_playing"
    }
    val showForYou: () -> Unit = {
        // "All" represents the For You/home feed. Prefer revealing the existing
        // start destination so switching sections cannot build duplicate home pages.
        if (!navController.popBackStack("home", inclusive = false)) {
            navController.navigate("home") {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }
    val showMediaSection: (String) -> Unit = { route ->
        // Music and Videos behave like sibling sections. Replace the current
        // section instead of stacking Music -> Videos -> Music indefinitely.
        val sectionRoute = currentRoute?.takeIf { it == "music_library" || it == "video_library" }
        navController.navigate(route) {
            sectionRoute?.let { currentSection ->
                popUpTo(currentSection) { inclusive = true }
            }
            launchSingleTop = true
        }
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
                        onQueue = {
                            navController.navigate(
                                if (playback.current?.kind == MediaKind.VIDEO && !playback.playAsAudio) {
                                    "video_queue"
                                } else {
                                    "queue"
                                },
                            )
                        },
                    )
                    Surface(
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        border = BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp) {
                            mainDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route ||
                                        (destination.route == "library" && currentRoute in mediaLibraryRoutes),
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
                    onOpenMusic = { showMediaSection("music_library") },
                    onOpenVideos = { showMediaSection("video_library") },
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
                    moods = moods,
                    recommendationCounts = moodCounts,
                    contentPadding = padding,
                    onNewDownload = { navController.navigate("new_download") },
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate(if (item.kind == MediaKind.VIDEO) "video_player" else "now_playing")
                    },
                    onFavorite = viewModel::toggleFavorite,
                    onOpenLibrary = { navController.navigate("library") },
                    onNotifications = { navController.navigate("downloads") },
                    onOpenMood = { mood -> navController.navigate("mood/${Uri.encode(mood.id)}") },
                    onManageMoods = { navController.navigate("moods") },
                )
            }
            composable("moods") {
                MoodHubScreen(
                    moods = moods,
                    recommendationCounts = moodCounts,
                    contentPadding = padding,
                    onOpenMood = { mood -> navController.navigate("mood/${Uri.encode(mood.id)}") },
                    onCreateMood = viewModel::createCustomMood,
                    onDeleteMood = { mood -> viewModel.deleteCustomMood(mood.id) },
                    onOpenAcoustic = { navController.navigate("acoustic_moods") },
                )
            }
            composable("acoustic_moods") {
                AcousticMoodScreen(
                    state = acousticState,
                    analyzedSongs = analyzedSongs,
                    librarySongs = librarySongs.size,
                    contentPadding = padding,
                    onBack = navController::popBackStack,
                    onStart = { viewModel.startAcousticAnalysis(false) },
                    onRerun = { viewModel.startAcousticAnalysis(true) },
                    onStop = viewModel::stopAcousticAnalysis,
                    onClear = viewModel::clearAcousticAnalysis,
                )
            }
            composable("mood/{id}") { entry ->
                val moodId = entry.arguments?.getString("id").orEmpty()
                val selectedMood = moods.firstOrNull { it.id == moodId } ?: moods.first()
                val recommendations = moodRecommendations[selectedMood.id].orEmpty()
                MoodDetailScreen(
                    mood = selectedMood,
                    recommendations = recommendations,
                    contentPadding = padding,
                    onBack = navController::popBackStack,
                    onPlayMix = { queue ->
                        queue.firstOrNull()?.let { first ->
                            viewModel.playQueue(first, queue)
                            navController.navigate("now_playing")
                        }
                    },
                    onPlay = { item, queue ->
                        viewModel.playQueue(item, queue)
                        navController.navigate("now_playing")
                    },
                    onToggleMood = viewModel::toggleMood,
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
                    onOpenMusic = { showMediaSection("music_library") },
                    onOpenVideos = { showMediaSection("video_library") },
                )
            }
            composable("music_library") {
                MusicLibraryScreen(
                    media = media,
                    favoriteUris = favorites,
                    contentPadding = padding,
                    onAll = showForYou,
                    onVideos = { showMediaSection("video_library") },
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate("now_playing")
                    },
                    onFavorite = viewModel::toggleFavorite,
                )
            }
            composable("video_library") {
                VideoLibraryScreen(
                    media = media,
                    favoriteUris = favorites,
                    contentPadding = padding,
                    onAll = showForYou,
                    onMusic = { showMediaSection("music_library") },
                    onPlay = { item ->
                        viewModel.play(item)
                        navController.navigate("video_player")
                    },
                    onFavorite = viewModel::toggleFavorite,
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
                    moods = moods,
                    currentMoodMatches = currentMoodMatches,
                    manualMoodIds = currentManualMoodIds,
                    onToggleMood = { mood -> currentAudio?.let { viewModel.toggleMood(it, mood.id) } },
                    onManageMoods = { navController.navigate("moods") },
                )
            }
            composable("video_player") {
                VideoPlayerScreen(
                    state = playback,
                    current = viewModel.currentMedia(),
                    controller = viewModel.playbackController,
                    onBack = navController::popBackStack,
                    onQueue = { navController.navigate("video_queue") },
                    onDownload = { navController.navigate("new_download") },
                    onPlayAsAudio = {
                        viewModel.playbackController.setPlayAsAudio(true)
                        navController.navigate("now_playing") {
                            popUpTo("video_player") { inclusive = true }
                        }
                    },
                )
            }
            composable("video_queue") {
                VideoQueueScreen(
                    state = playback,
                    onBack = navController::popBackStack,
                    onPlayIndex = { index ->
                        viewModel.playbackController.playIndex(index)
                        navController.navigate("video_player") {
                            popUpTo("video_queue") { inclusive = true }
                            launchSingleTop = true
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
