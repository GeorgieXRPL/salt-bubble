package com.easton.saltbubble

import com.easton.saltbubble.data.Phrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {
    @Test
    fun wakePhrases() {
        assertEquals(Phrases.Kind.Wake, Phrases.classify("Salt wake up"))
        assertEquals(Phrases.Kind.Wake, Phrases.classify("hey salt"))
        assertEquals(Phrases.Kind.Wake, Phrases.classify("assault wake up"))
    }

    @Test
    fun standbyPhrases() {
        assertEquals(Phrases.Kind.Standby, Phrases.classify("Salt go on standby"))
        assertEquals(Phrases.Kind.Standby, Phrases.classify("bye"))
        assertEquals(Phrases.Kind.Standby, Phrases.classify("salt cut asleep"))
    }

    @Test
    fun targets() {
        assertEquals(Phrases.Kind.TargetGrok, Phrases.classify("salt send to grok"))
        assertEquals(Phrases.Kind.TargetBrand, Phrases.classify("salt send to brand studio"))
        assertEquals(Phrases.Kind.TargetBrand, Phrases.classify("salt send to capture"))
    }

    @Test
    fun clipPhrases() {
        assertEquals(Phrases.Kind.Clip, Phrases.classify("Salt clip that"))
        assertEquals(Phrases.Kind.Clip, Phrases.classify("salt save clip"))
        assertEquals(Phrases.Kind.Clip, Phrases.classify("clip that"))
        assertEquals(Phrases.Kind.Clip, Phrases.classify("salt grab that"))
    }

    @Test
    fun taskAndRemainder() {
        assertEquals(Phrases.Kind.Task, Phrases.classify("draft a tweet about Card Seat"))
        val rest = Phrases.remainderAfterWake("salt wake up draft a tweet about Card Seat")
        assertTrue(rest.contains("draft a tweet"))
    }
}
