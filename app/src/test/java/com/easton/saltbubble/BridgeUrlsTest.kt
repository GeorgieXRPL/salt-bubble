package com.easton.saltbubble

import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.BridgeUrls
import com.easton.saltbubble.data.CaptureUrls
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.GrokTargets
import com.easton.saltbubble.net.HubUrls
import com.easton.saltbubble.net.TranscriptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

class CaptureUrlsTest {
    @Test
    fun appendsQuestionTextWhenNoQuery() {
        assertEquals(
            "https://app.mybrandstudio.club/capture?text=hello",
            CaptureUrls.withTranscript("https://app.mybrandstudio.club/capture", "hello"),
        )
    }

    @Test
    fun appendsAmpersandTextWhenQueryExists() {
        assertEquals(
            "https://app.mybrandstudio.club/capture?foo=1&text=hello",
            CaptureUrls.withTranscript("https://app.mybrandstudio.club/capture?foo=1", "hello"),
        )
    }

    @Test
    fun doesNotSmashExistingParams() {
        val url = CaptureUrls.withTranscript(
            "https://app.mybrandstudio.club/capture?foo=1&bar=2",
            "idea",
        )
        assertTrue(url.startsWith("https://app.mybrandstudio.club/capture?foo=1&bar=2&text="))
        assertTrue(url.endsWith("text=idea"))
    }

    @Test
    fun encodesSpaces() {
        assertEquals(
            "https://app.mybrandstudio.club/capture?text=hello%20world",
            CaptureUrls.withTranscript("https://app.mybrandstudio.club/capture", "hello world"),
        )
    }

    @Test
    fun usesCaptureUrlWithTextPrefill() {
        assertEquals(
            "https://app.mybrandstudio.club/capture?text=clip",
            CaptureUrls.target("https://app.mybrandstudio.club/capture", "", "clip"),
        )
    }

    @Test
    fun httpDeepLinkGetsTextPrefill() {
        assertEquals(
            "https://app.mybrandstudio.club/capture?src=hud&text=clip",
            CaptureUrls.target(
                "https://app.mybrandstudio.club/capture",
                "https://app.mybrandstudio.club/capture?src=hud",
                "clip",
            ),
        )
    }

    @Test
    fun customSchemeDeepLinkIsNotRewritten() {
        assertEquals(
            "mybrandstudio://capture",
            CaptureUrls.target("https://app.mybrandstudio.club/capture", "mybrandstudio://capture", "clip"),
        )
    }
}

class GrokTargetsTest {
    @Test
    fun defaultPreferredKeepsBotThenChat() {
        assertEquals(
            listOf(GrokTargets.BOT, GrokTargets.CHAT),
            GrokTargets.packages(GrokTargets.BOT),
        )
    }

    @Test
    fun preferredFirstThenUniqueFallbacks() {
        assertEquals(
            listOf("custom.pkg", GrokTargets.BOT, GrokTargets.CHAT),
            GrokTargets.packages("custom.pkg"),
        )
    }

    @Test
    fun chatPreferredDoesNotDuplicate() {
        assertEquals(
            listOf(GrokTargets.CHAT, GrokTargets.BOT),
            GrokTargets.packages(GrokTargets.CHAT),
        )
    }

    @Test
    fun blankPreferredSkipsEmpty() {
        assertEquals(
            listOf(GrokTargets.BOT, GrokTargets.CHAT),
            GrokTargets.packages("  "),
        )
    }
}

class HubUrlsTest {
    @Test
    fun joinsIdeasPath() {
        assertEquals(
            "https://brand-studio-hub.fly.dev/api/ideas",
            HubUrls.ideasUrl("https://brand-studio-hub.fly.dev"),
        )
    }

    @Test
    fun trimsTrailingSlash() {
        assertEquals(
            "https://brand-studio-hub.fly.dev/api/ideas",
            HubUrls.ideasUrl("https://brand-studio-hub.fly.dev/"),
        )
    }

    @Test
    fun blankFallsBackToDefaultHub() {
        assertEquals(
            "${AppSettings.DEFAULT_HUB_URL}/api/ideas",
            HubUrls.ideasUrl("   "),
        )
    }

    @Test
    fun rejectsCleartextHub() {
        assertFalse(HubUrls.looksSafeHub("http://evil.example/hub"))
    }

    @Test
    fun allowsHttpsAndLocalhost() {
        assertTrue(HubUrls.looksSafeHub("https://brand-studio-hub.fly.dev"))
        assertTrue(HubUrls.looksSafeHub("http://localhost:8787"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ideasUrlRejectsHttp() {
        HubUrls.ideasUrl("http://evil.example/hub")
    }

    @Test
    fun ideaJsonEscapesQuotesAndNewlines() {
        assertEquals(
            """{"text":"say \"hi\"\nnext"}""",
            HubUrls.ideaJson("say \"hi\"\nnext"),
        )
    }
}

class DestinationTest {
    @Test
    fun brandStudioAndBothIncludeStudio() {
        assertTrue(Destination.BrandStudio.includesBrandStudio())
        assertTrue(Destination.Both.includesBrandStudio())
        assertFalse(Destination.GrokBot.includesBrandStudio())
    }
}
