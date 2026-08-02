package com.omniplayer.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun durationFormatsMinutesAndHours() {
        assertEquals("0:00", 0L.asDuration())
        assertEquals("1:05", 65_000L.asDuration())
        assertEquals("1:01:01", 3_661_000L.asDuration())
    }

    @Test
    fun fileSizeUsesReadableUnits() {
        assertEquals("1.0 KB", 1_024L.asFileSize())
        assertEquals("1.0 MB", 1_048_576L.asFileSize())
    }
}

