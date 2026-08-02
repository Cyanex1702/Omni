# Omni Player for Android

Omni Player is a free, ad-free, local-first audio/video player, media library, and website-media downloader. It has no account system, analytics SDK, advertising SDK, paywall, or remote Omni service.

Made by Cynex1702.

This source release is licensed under GNU GPL version 3. Omni uses the GPL-licensed `youtubedl-android` runtime to run yt-dlp and FFmpeg on-device; see `LICENSE` and `THIRD_PARTY_NOTICES.md`.

## Implemented in 1.2.0

- A cohesive graphite-and-coral interface with four-tab navigation, quick link download, editorial Explore content, a storage-aware Library, a compact mini-player, and a redesigned Now Playing screen
- Home, Explore, Downloads, Library, Settings, Queue, Lyrics, Equalizer, Ringtone Cutter, and Video-to-Audio workflows with the existing functionality preserved
- Separate purpose-built audio and video experiences: audio supports Square Cover, Vinyl Disc, and Wave Circle appearances, while video uses a responsive canvas-first controller with portrait, landscape, full-screen, gesture, track, subtitle, display, lock, and Picture-in-Picture controls
- Immersive AMOLED and Editorial Luxe visual themes with a persistent player-appearance preference
- A simplified adaptive launcher icon with a monochrome Android themed-icon layer, inspired by the supplied Omni wing artwork
- The exact supplied black-and-silver Omni artwork as the launcher icon and Android 12+ splash artwork
- Device audio/video discovery through Android MediaStore, including artwork and local-video thumbnails
- AndroidX Media3 audio/video playback with decoder fallback, audio focus, background playback, notification and lock-screen controls
- Queue, seek, shuffle, repeat, playback speed, sleep timer, favorites, recents, resume position, and visible buffering/error/retry states
- Five-band equalizer, bass boost, virtualizer, and reverb connected to the playback audio session where supported
- yt-dlp 2026.07.04 website extraction plus direct-media URL support, with periodic extractor updates
- ABI-matched QuickJS-NG 0.15.0 runtime and embedded EJS solver scripts for current YouTube JavaScript challenges
- MP3 extraction at 128, 192, or 320 kbps with metadata and artwork
- Video download at up to 360p, 720p, 1080p, or best available quality: Android-friendly MP4 is preferred, while playable WebM/MKV is retained as a fallback instead of rejecting a valid source
- Independent WorkManager jobs with Wi-Fi-only mode, a 1–3 download concurrency gate, foreground progress, cancellation, automatic transient retries, history, and Android share/open-link support
- Shared text and form input normalize the first valid HTTP/HTTPS media link, including links surrounded by ordinary message text
- Media validation before completion; HTML, empty output, DRM, live streams, and unplayable files are not falsely reported as finished
- Finished files published to `Music/Omni` or `Movies/Omni` on Android 10+, with immediate playback from the Finished tab
- MP3 and AAC/M4A clipping, plus compatible local-video AAC extraction to M4A

## Download scope

Omni handles a single user-supplied HTTP/HTTPS media page or direct file per task when the current yt-dlp extractor supports it. It does not bypass access controls. The current release intentionally does not handle:

- DRM or encrypted media;
- live streams;
- playlists or bulk channel downloads;
- links that require a signed-in browser session, imported cookies, or payment;
- media the user is not authorized to save.

Website behavior changes frequently. Omni retries a failed update after 15 minutes, records only successful updates for the three-day interval, and falls back to its current bundled extractor if an update is unavailable.

## Open and run

1. Extract the archive to a new folder.
2. Open the folder containing `settings.gradle.kts` in Android Studio.
3. Select JDK 17 and install Android SDK 35 when prompted.
4. Let Gradle sync download the native yt-dlp/FFmpeg dependencies.
5. Run the `app` configuration on Android 8.0 (API 26) or newer.
6. Grant audio, video, and notification access when requested.

The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01, Media3 1.5.1, youtubedl-android 0.17.3, yt-dlp 2026.07.04, and QuickJS-NG 0.15.0. The native runtime makes the final APK substantially larger than a player-only app, and its first initialization can take a few seconds.

## Device test

1. Put an MP3/M4A file and an MP4/WebM file on the device, rescan Library, and play both.
2. Paste an authorized, publicly accessible supported webpage into New Download and select Audio 192 kbps.
3. Confirm Preparing, Downloading, Validating, and Saving stages appear; then play the MP3 from Downloads → Finished.
4. Repeat with Video 720p and confirm the final MP4, WebM, or MKV has picture and, when present in the source, sound.
5. Cancel a running task, retry a failed task, share a web link to Omni, and test Wi-Fi-only mode.
6. Background playback, seek, pause, notification controls, and return to the full player.
7. Try a DRM, live, login-only, or unsupported link and confirm Omni reports a useful failure rather than an endless loading state.

See `docs/ARCHITECTURE.md` for the implementation map and `docs/ROADMAP.md` for release validation.
