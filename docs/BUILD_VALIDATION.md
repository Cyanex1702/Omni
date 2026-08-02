# Build validation

Validation performed on 2026-07-31 with JDK 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Android SDK 35, and Build Tools 35.0.0.

- `:app:assembleDebug`: passed
- `:app:lintDebug`: passed with no lint errors
- `:app:testDebugUnitTest`: passed
- APK package: `com.omniplayer.app`
- Version: `1.2.0` (`versionCode` 7)
- Minimum/target API: 26/35
- APK Signature Scheme v2 verification: passed with the Android debug certificate
- 16 KB page-size ELF and APK alignment checks: passed
- Bundled yt-dlp reports stable version `2026.07.04` and matches the official SHA-256 `495be29ff4d9d4e9be7eabdfef225221e5d5282e77f2f505abc6dca80349f3fd`
- APK contains Python, FFmpeg, aria2c, and QuickJS-NG payloads for arm64-v8a, armeabi-v7a, x86, and x86_64

Downloader request normalization, quality policy, progress parsing, and retry classification have JVM unit coverage. The generated debug APK is for device testing, not store publication. Playback codecs, website extraction, MediaStore behavior, cancellation timing, and the first-launch native extraction still require the physical-device matrix in `ROADMAP.md`.
