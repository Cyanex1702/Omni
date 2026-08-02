# Third-party notices

Omni 1.1.1 is distributed under the GNU General Public License version 3. The full license is in `LICENSE`.

The downloader runtime uses the following open-source projects:

- **youtubedl-android** (`io.github.junkfood02.youtubedl-android`, 0.17.3), GNU GPL v3: https://github.com/yausername/youtubedl-android
- **yt-dlp** (bundled release 2026.07.04), The Unlicense, with bundled EJS components under the licenses documented by its official executable: https://github.com/yt-dlp/yt-dlp
- **QuickJS-NG** (0.15.0), MIT License; bundled license: `licenses/QUICKJS_NG_LICENSE.txt`: https://github.com/quickjs-ng/quickjs
- **FFmpeg**, licensed under LGPL v2.1+ or GPL v2+ depending on build configuration: https://ffmpeg.org/legal.html
- **aria2**, GNU GPL v2 or later: https://github.com/aria2/aria2

Omni also uses AndroidX, Jetpack Compose, Kotlin, Coil, OkHttp, Guava, and their transitive dependencies under their respective open-source licenses. Refer to the dependency coordinates in `gradle/libs.versions.toml` and the upstream distributions for complete copyright notices.

Seal was examined as a public architectural reference for an Android yt-dlp workflow. Omni does not include Seal source code, branding, resources, or user interface. Seal is GNU GPL v3 software: https://github.com/JunkFood02/Seal
