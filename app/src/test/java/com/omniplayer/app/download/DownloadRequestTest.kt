package com.omniplayer.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRequestTest {
    @Test
    fun extractsUrlFromSharedTextAndRemovesPunctuation() {
        assertEquals(
            "https://example.com/watch?v=123&list=no",
            DownloadRequest.extractUrl(
                "Watch this video:\nhttps://example.com/watch?v=123&amp;list=no).",
            ),
        )
    }

    @Test
    fun rejectsUnsupportedOrIncompleteUrls() {
        assertNull(DownloadRequest.extractUrl("ftp://example.com/video.mp4"))
        assertNull(DownloadRequest.extractUrl("https://"))
        assertFalse(DownloadRequest.isSupportedUrl("file:///tmp/video.mp4"))
    }

    @Test
    fun normalizesTypeAndQuality() {
        val audio = DownloadRequest.create(
            "  https://example.com/audio  ",
            "AUDIO",
            "",
        )
        assertEquals(DownloadRequest.TYPE_AUDIO, audio.type)
        assertEquals("320", audio.quality)

        val video = DownloadRequest.create(
            "https://example.com/video",
            DownloadRequest.TYPE_VIDEO,
            "BEST",
        )
        assertEquals("best", video.quality)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedQuality() {
        DownloadRequest.create(
            "https://example.com/video",
            DownloadRequest.TYPE_VIDEO,
            "144",
        )
    }

    @Test
    fun videoSelectorPrefersCompatibleMp4AndKeepsFallbacks() {
        val selector = DownloadFormatPolicy.videoSelector("720")
        assertTrue(selector.startsWith("bestvideo[height<=720][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]"))
        assertTrue(selector.contains("bestvideo[height<=720]+bestaudio"))
        assertTrue(selector.endsWith("/best"))
    }

    @Test
    fun parsesYtDlpProgressSizes() {
        val parsed = DownloadProgressParser.parse(
            "[download]  25.0% of ~ 8.00MiB at 1.00MiB/s ETA 00:06",
            25,
        )
        assertEquals(8L * 1_048_576L, parsed.totalBytes)
        assertEquals(2L * 1_048_576L, parsed.bytes)
        assertEquals(1_048_576L, parsed.speedBytesPerSecond)
        assertEquals(6L, parsed.etaSeconds)
    }

    @Test
    fun parsesStructuredProgressWithSpeedEtaAndPreviewFile() {
        val parsed = DownloadProgressParser.parse(
            "OMNI_PROGRESS|2621440|10485760|524288|15|avc1.4d401f|none|https|C:\\cache\\clip.mp4.part",
            0,
        )
        assertTrue(parsed.structured)
        assertEquals(25, parsed.percent)
        assertEquals(524_288L, parsed.speedBytesPerSecond)
        assertEquals(15L, parsed.etaSeconds)
        assertTrue(parsed.hasVideo)
        assertFalse(parsed.hasAudio)
        assertEquals("https", parsed.protocol)
        assertEquals("C:\\cache\\clip.mp4.part", parsed.filename)
    }
}
