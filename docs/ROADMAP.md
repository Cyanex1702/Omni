# Omni release-validation roadmap

The source targets Android 8.0 through Android 15. Before publishing an APK, validate the following on physical devices because native ABIs, codecs, storage behavior, and vendor audio effects vary.

## Required 1.2.0 checks

- API 26/28 app-private fallback storage and notification behavior
- API 29/30 MediaStore pending-file publication
- API 33–35 audio, video, notification, and foreground-service permission flows
- arm64-v8a, armeabi-v7a, x86, and x86_64 native-runtime extraction on a clean install
- Cold-start and simultaneous-task races during Python/FFmpeg initialization
- yt-dlp metadata probe, QuickJS YouTube challenge solving, forced bundled-engine refresh on upgrade, periodic update failure fallback, cancellation, retry, duplicate names, redirect handling, and Wi-Fi-only constraints
- Authorized website audio at 128/192/320 kbps and video at 360/720/1080/best
- Separate video/audio stream merging, missing quality fallback messages, and Unicode/long filenames
- Rejection of DRM, live, login-only, unsupported, malformed, empty, and non-media results
- Immediate playback from Finished plus playback after process recreation and MediaStore rescan
- MP3, AAC/M4A, FLAC, OGG/Opus, WAV, MP4/H.264, MP4/HEVC, and WebM playback where device decoders exist
- Background playback, wired/Bluetooth disconnect, lock-screen controls, sleep timer, and process recreation
- Portrait/landscape video transitions, immersive controls, double-tap/swipe gestures, screen lock, brightness, volume, Picture-in-Picture, subtitle/audio/video track selection, and aspect-ratio modes
- Square Cover, Vinyl Disc, and Wave Circle audio appearances in both Immersive AMOLED and Editorial Luxe themes
- Ringtone clipping, AAC extraction from local MP4, and graceful handling of unsupported codecs
- Equalizer/effect availability and fallback across vendors

## Later product decisions

Potential additions include playlist/batch downloads, downloadable subtitle selection, audio-format selection beyond MP3, cookie import, user-named playlist editing, imported LRC files, full Google Cast integration, and a licensed online catalog. They are not represented as working features in 1.2.0.
