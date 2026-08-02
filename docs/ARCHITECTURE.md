# Omni architecture

## Media and playback path

1. `MediaRepository` reads audio and video entries from Android MediaStore.
2. `MainViewModel` exposes library content, recents, favorites, settings, downloads, media-tool status, and equalizer state as flows.
3. `PlaybackController` owns the application-level Media3 controller and retains a Play request while the playback service connects.
4. `OmniPlaybackService` owns ExoPlayer and MediaSession. It supplies decoder fallback, audio focus, noisy-headset handling, wake mode, notification/lock-screen controls, sleep timer, gapless preference, and audio effects.
5. The Compose player observes READY, BUFFERING, and ERROR state and displays video, artwork, progress, or a recoverable error instead of an indefinite spinner.

## Website download path

1. Android share/open intents or the New Download form provide one HTTP/HTTPS URL, media type, and quality.
2. `DownloadRepository` validates and normalizes the request, then enqueues an independent WorkManager job. A process-local fair gate limits native work to the configured one to three simultaneous downloads without making unrelated jobs prerequisites of each other.
3. `YtDlpRuntime` serializes initialization and extractor replacement for the bundled Python, yt-dlp, QuickJS-NG, FFmpeg, and aria2c packages. Downloads can run concurrently under a read lock, while an update uses the write lock so it cannot replace the extractor during an import. Successful updates are remembered for three days; failed updates can retry after 15 minutes.
4. `YtDlpEngine` supplies the ABI-matched QuickJS executable for YouTube EJS challenges and performs extraction, stream selection, download, and final JSON reporting in one yt-dlp invocation. It rejects live/DRM/non-media results and downloads into a per-task private directory.
5. FFmpeg extracts MP3 audio or merges separate video/audio streams. H.264/AAC MP4 is preferred for Android compatibility, with playable WebM/MKV fallback. Foreground progress reports every stage, cancellation interrupts the native process, and transient network/server failures receive bounded exponential retries.
6. `DownloadWorker` probes every candidate with Android media APIs. Only a real, non-empty audio-only or video-containing result matching the requested type can complete.
7. The validated file is copied through MediaStore to `Music/Omni` or `Movies/Omni` on Android 10+. Temporary files are always removed.
8. Work output carries the final URI, MIME type, kind, title, artist, thumbnail, duration, size, quality, and source URL. The Finished card can play it immediately and preserve enough input to retry failures.

## Media tools

- The ringtone cutter copies MP3 frames or remuxes an AAC/M4A time range without re-encoding.
- Video to Audio remuxes a compatible AAC track into M4A without quality loss.
- Unsupported codecs return a visible explanation instead of creating an unusable output.

## UI map

- `OmniPlayerRoot.kt`: four-tab navigation, bottom bar, link handoff, and mini-player
- `HomeBrowseScreens.kt`: greeting/search/quick-download Home and editorial Explore screen
- `DownloadScreens.kt`: active/finished cards, format/quality form, cancellation, retry, and immediate play
- `PlaybackScreens.kt`: audio-only Square Cover, Vinyl Disc, and Wave Circle player, lyrics/related panel, queue, equalizer, and sleep timer
- `VideoPlayerScreen.kt`: dedicated responsive video surface, auto-hiding overlays, seek/brightness/volume gestures, track selectors, display modes, screen lock, rotation, and Picture-in-Picture
- `LibrarySettingsScreens.kt`: storage-aware library, media filters, and settings
- `MediaToolsScreens.kt`: ringtone cutter and video-to-audio workflow
- `Components.kt`: artwork/thumbnail rendering and shared media rows

## Privacy, licensing, and boundaries

Downloads and playback run locally. Omni has no ads, analytics, tracking, account backend, or hosted media catalog. It does not implement DRM circumvention, cookie theft, or login bypasses. Because Omni links GPL-licensed youtubedl-android components, this complete source is distributed under GPL-3.0; third-party attribution is in `THIRD_PARTY_NOTICES.md`.
