package com.easton.saltbubble.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class Destination(val label: String) {
    GrokBot("Grok Bot"),
    BrandStudio("Brand Studio"),
    Both("Both"),
    ;

    fun includesBrandStudio(): Boolean = this == BrandStudio || this == Both
}

enum class GrokLand(val label: String) {
    Phone("Phone"),
    Pc("PC"),
    Both("Both"),
    ;

    fun usesPhone(): Boolean = this == Phone || this == Both
    fun usesPc(): Boolean = this == Pc || this == Both
}

enum class SessionPhase(val label: String) {
    Standby("Standby"),
    Listening("Listening"),
}

enum class MessageRole {
    User,
    Status,
    System,
}

enum class MessageStatus {
    Recording,
    Transcribing,
    Ready,
    Failed,
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.Ready,
    val createdAt: Long = System.currentTimeMillis(),
    val sentTo: Set<Destination> = emptySet(),
)

data class AppSettings(
    val bridgeHost: String = DEFAULT_BRIDGE_HOST,
    val bridgePort: Int = DEFAULT_BRIDGE_PORT,
    val bridgeUseHttps: Boolean = false,
    val captureUrl: String = DEFAULT_CAPTURE_URL,
    val captureDeepLink: String = "",
    val defaultDestination: Destination = Destination.Both,
    val bridgeToken: String = "",
    val grokPackage: String = DEFAULT_GROK_PACKAGE,
    val hubUrl: String = DEFAULT_HUB_URL,
    val hubToken: String = "",
    val postIdeaOnSend: Boolean = true,
    val grokLand: GrokLand = GrokLand.Phone,
) {
    val bridgeBaseUrl: String = BridgeUrls.baseUrl(bridgeHost, bridgePort, bridgeUseHttps)

    companion object {
        const val DEFAULT_BRIDGE_HOST = "mypc.tailc2bc11.ts.net"
        const val DEFAULT_BRIDGE_PORT = 8791
        const val DEFAULT_CAPTURE_URL = "https://app.mybrandstudio.club/capture"
        const val DEFAULT_GROK_PACKAGE = "ai.x.grok.bot"
        const val DEFAULT_HUB_URL = "https://brand-studio-hub.fly.dev"
    }
}

object BridgeUrls {
    fun baseUrl(host: String, port: Int, https: Boolean): String {
        val scheme = if (https) "https" else "http"
        val cleaned = host.trim().trimEnd('/')
        val withoutScheme = cleaned
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
        val safePort = port.coerceIn(1, 65535)
        return "$scheme://$withoutScheme:$safePort"
    }
}

object CaptureUrls {
    fun withTranscript(captureUrl: String, text: String): String {
        val base = captureUrl.trim()
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val separator = if (base.contains('?')) '&' else '?'
        return "$base${separator}text=$encoded"
    }

    fun target(captureUrl: String, captureDeepLink: String, text: String): String {
        val deep = captureDeepLink.trim()
        val web = captureUrl.trim().ifBlank { AppSettings.DEFAULT_CAPTURE_URL }
        val trimmed = text.trim()
        if (deep.isNotEmpty()) {
            return if (looksHttp(deep) && trimmed.isNotEmpty()) withTranscript(deep, trimmed) else deep
        }
        return if (trimmed.isEmpty()) web else withTranscript(web, trimmed)
    }

    private fun looksHttp(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }
}

object GrokTargets {
    const val BOT = "ai.x.grok.bot"
    const val CHAT = "ai.x.grok"

    fun packages(preferred: String): List<String> {
        val first = preferred.trim()
        return listOf(first, BOT, CHAT).filter { it.isNotEmpty() }.distinct()
    }
}