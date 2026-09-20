package no.mwmai.music

import no.mwmai.music.util.formatTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsMinutesHoursAndGarbage() {
        assertEquals("0:07", formatTime(7_000))
        assertEquals("3:42", formatTime(222_999))
        assertEquals("1:02:05", formatTime(3_725_000))
        assertEquals("0:00", formatTime(-5))
    }
}
