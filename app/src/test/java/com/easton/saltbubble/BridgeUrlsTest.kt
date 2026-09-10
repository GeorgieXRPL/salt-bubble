package com.easton.saltbubble

import com.easton.saltbubble.data.BridgeUrls
import com.easton.saltbubble.net.TranscriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeUrlsTest {
    @Test
    fun defaultTailscaleUrl() {
        assertEquals(
            "http://mypc.tailc2bc11.ts.net:8791",
            BridgeUrls.baseUrl("mypc.tailc2bc11.ts.net", 8791, false),
        )
    }

    @Test
    fun stripsSchemeAndPath() {
        assertEquals(
            "http://mypc.tailc2bc11.ts.net:8791",
            BridgeUrls.baseUrl("http://mypc.tailc2bc11.ts.net:9999/extra", 8791, false),
        )
    }

    @Test
    fun httpsToggle() {
        assertEquals(
            "https://office-pc:9000",
            BridgeUrls.baseUrl("office-pc", 9000, true),
        )
    }
}

class TranscriptParserTest {
    @Test
    fun readsJsonTextField() {
        assertEquals("hello easton", TranscriptParser.parse("""{"text":"hello easton"}"""))
    }

    @Test
    fun unescapesNewlines() {
        assertEquals("line one\nline two", TranscriptParser.parse("""{"text":"line one\nline two"}"""))
    }

    @Test
    fun prefersTranscriptionKey() {
        assertEquals("from key", TranscriptParser.parse("""{"transcription":"from key"}"""))
    }

    @Test
    fun plainTextPassthrough() {
        assertEquals("just words", TranscriptParser.parse("just words", "text/plain"))
    }

    @Test
    fun emptyIsNull() {
        assertNull(TranscriptParser.parse("   "))
    }
}
