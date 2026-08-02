package com.omniplayer.app.download

import java.net.URI
import java.util.Locale

internal data class DownloadFailureDetails(
    val message: String,
    val diagnostic: String,
    val retryable: Boolean,
)

internal object DownloadFailureClassifier {
    fun classify(error: Throwable, sourceUrl: String): DownloadFailureDetails =
        classify(
            diagnostic = diagnosticFrom(error),
            sourceUrl = sourceUrl,
            errorType = error.javaClass.simpleName,
        )

    fun classify(
        diagnostic: String,
        sourceUrl: String,
        errorType: String,
    ): DownloadFailureDetails {
        val useful = diagnostic.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .lastOrNull { it.contains("ERROR:", ignoreCase = true) }
            ?.substringAfter("ERROR:", missingDelimiterValue = diagnostic)
            ?.trim()
            ?: diagnostic.lineSequence().map(String::trim).lastOrNull(String::isNotBlank).orEmpty()
        val lower = diagnostic.lowercase(Locale.US)
        val isYouTube = runCatching {
            URI(sourceUrl).host.orEmpty().lowercase(Locale.US).let { host ->
                host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
            }
        }.getOrDefault(false)

        val (message, retryable) = when {
            lower.contains("sign in to confirm") || lower.contains("not a bot") ->
                "YouTube blocked anonymous access from this phone/network even after Omni tried public playback clients. Try again later, switch Wi-Fi/mobile data, and disable any VPN. Private or account-only videos are not supported." to false
            lower.contains("private video") || lower.contains("members-only") ->
                "This YouTube video is private or members-only and requires authorized access." to false
            lower.contains("age-restricted") || lower.contains("confirm your age") ->
                "This video is age-restricted and requires an authorized signed-in session." to false
            lower.contains("video unavailable") || lower.contains("this video is unavailable") ->
                "The website reports that this video is unavailable for this device, account, or region." to false
            lower.contains("sign in") || lower.contains("cookies") || lower.contains("login required") ->
                "This site requires a logged-in session or browser cookies, which Omni does not import." to false
            lower.contains("drm") ->
                "This media is DRM-protected and cannot be downloaded by Omni." to false
            lower.contains("live stream") || lower.contains("is live") ->
                "Live streams are not supported because they may never finish downloading." to false
            lower.contains("unsupported url") ->
                "This website or link is not supported by the current yt-dlp extractor." to false
            lower.contains("requested format is not available") ->
                "The requested quality is unavailable. Try a lower quality or Best." to false
            isYouTube && (
                lower.contains("no supported javascript runtime") ||
                    lower.contains("challenge solving failed") ||
                    lower.contains("signature solving failed") ||
                    lower.contains("n challenge") ||
                    lower.contains("only images are available") ||
                    lower.contains("no playable audio or video streams")
                ) ->
                "YouTube did not expose a playable stream to Omni's built-in extractor and JavaScript runtime." to false
            lower.contains("http error 429") || lower.contains("too many requests") ->
                "The website temporarily rate-limited this device (HTTP 429). Omni will retry automatically." to true
            lower.contains("http error 403") || lower.contains("forbidden") ->
                "The website temporarily refused the media URL (HTTP 403). Omni will request a fresh link and retry." to true
            Regex("""http error 5\d\d""").containsMatchIn(lower) ->
                "The website has a temporary server problem. Omni will retry automatically." to true
            lower.contains("timed out") || lower.contains("timeout") ->
                "The website took too long to respond. Omni will retry automatically." to true
            lower.contains("unable to download webpage") ||
                lower.contains("temporary failure in name resolution") ||
                lower.contains("network is unreachable") ||
                lower.contains("connection reset") ||
                lower.contains("connection aborted") ||
                lower.contains("remote end closed") ||
                lower.contains("broken pipe") ->
                "Omni temporarily lost contact with the website. It will retry automatically." to true
            lower.contains("fragment") && (
                lower.contains("unavailable") ||
                    lower.contains("failed") ||
                    lower.contains("retry")
                ) ->
                "A media fragment was temporarily unavailable. Omni will retry automatically." to true
            lower.contains("no space left") ->
                "There is not enough free storage to finish this download." to false
            useful.isNotBlank() && !useful.equals("null", true) ->
                useful.take(360) to false
            else ->
                "Download failed ($errorType). The link may be unavailable or unsupported." to false
        }
        return DownloadFailureDetails(
            message = message,
            diagnostic = diagnostic.takeLast(1_800),
            retryable = retryable,
        )
    }

    private fun diagnosticFrom(error: Throwable): String {
        val messages = mutableListOf<String>()
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            current.message?.trim()?.takeIf(String::isNotBlank)?.let(messages::add)
            current = current.cause
        }
        val cleaned = messages.distinct().joinToString("\n")
            .replace(Regex("\\x1B\\[[0-9;]*[A-Za-z]"), "")
            .replace('\r', '\n')
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString("\n")
        return cleaned.ifBlank { "${error.javaClass.name}: no diagnostic message" }
    }
}
