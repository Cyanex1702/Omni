package com.omniplayer.app.download

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailureClassifierTest {
    @Test
    fun retriesTransientNetworkAndServerFailures() {
        assertTrue(
            DownloadFailureClassifier.classify(
                IOException("ERROR: HTTP Error 503: Service Unavailable"),
                "https://example.com/video",
            ).retryable
        )
        assertTrue(
            DownloadFailureClassifier.classify(
                IOException("Connection timed out"),
                "https://example.com/video",
            ).retryable
        )
    }

    @Test
    fun doesNotRetryAuthorizationOrDrmFailures() {
        assertFalse(
            DownloadFailureClassifier.classify(
                IOException("ERROR: Sign in to confirm you're not a bot"),
                "https://youtube.com/watch?v=test",
            ).retryable
        )
        assertFalse(
            DownloadFailureClassifier.classify(
                IOException("This media has DRM"),
                "https://example.com/video",
            ).retryable
        )
    }

    @Test
    fun preservesUsefulEngineDiagnostic() {
        val failure = DownloadFailureClassifier.classify(
            IOException("wrapper", IOException("ERROR: extractor returned a precise failure")),
            "https://example.com/video",
        )
        assertTrue(failure.message.contains("extractor returned a precise failure"))
        assertTrue(failure.diagnostic.contains("wrapper"))
    }
}
