package com.easton.saltbubble.data

import java.util.UUID

enum class Destination(val label: String) {
    GrokBot("Grok Bot"),
    BrandStudio("Brand Studio"),
    Both("Both"),
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
) {
    val bridgeBaseUrl: String = BridgeUrls.baseUrl(bridgeHost, bridgePort, bridgeUseHttps)

    companion object {
        const val DEFAULT_BRIDGE_HOST = "mypc.tailc2bc11.ts.net"
        const val DEFAULT_BRIDGE_PORT = 8791
        const val DEFAULT_CAPTURE_URL = "https://app.mybrandstudio.club/capture"
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
