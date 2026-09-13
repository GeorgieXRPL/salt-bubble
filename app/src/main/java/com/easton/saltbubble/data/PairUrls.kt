package com.easton.saltbubble.data

import android.net.Uri
import com.easton.saltbubble.net.HubUrls

data class StudioPair(
    val hubUrl: String,
    val hubToken: String,
    val captureUrl: String,
)

object PairUrls {
    const val SCHEME = "saltbubble"
    const val HOST = "pair"
    const val WEB_HOST = "app.mybrandstudio.club"
    const val WEB_PATH = "/salt-bubble/pair"

    fun build(hubUrl: String, key: String, captureUrl: String = AppSettings.DEFAULT_CAPTURE_URL): String {
        val pair = requireValid(hubUrl, key, captureUrl)
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("hub", pair.hubUrl)
            .appendQueryParameter("key", pair.hubToken)
            .appendQueryParameter("capture", pair.captureUrl)
            .build()
            .toString()
    }

    fun parse(uri: Uri?): StudioPair? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val looksPair = (scheme == SCHEME && uri.host.equals(HOST, ignoreCase = true)) ||
            (scheme == "https" && uri.host.equals(WEB_HOST, ignoreCase = true) &&
                uri.path?.trimEnd('/') == WEB_PATH)
        if (!looksPair && scheme != SCHEME && !(scheme == "http" || scheme == "https")) {
            return null
        }
        if (!looksPair && scheme != SCHEME) {
            val path = uri.path?.trimEnd('/').orEmpty()
            if (!path.endsWith("/salt-bubble/pair") && !path.endsWith("/pair")) return null
        }
        val hub = uri.getQueryParameter("hub").orEmpty()
        val key = uri.getQueryParameter("key")
            ?: uri.getQueryParameter("token")
            ?: ""
        val capture = uri.getQueryParameter("capture").orEmpty()
            .ifBlank { AppSettings.DEFAULT_CAPTURE_URL }
        return runCatching { requireValid(hub, key, capture) }.getOrNull()
    }

    fun parse(raw: String): StudioPair? = runCatching { parse(Uri.parse(raw.trim())) }.getOrNull()

    fun requireValid(hubUrl: String, key: String, captureUrl: String): StudioPair {
        val hub = hubUrl.trim().trimEnd('/')
        val token = key.trim()
        val capture = captureUrl.trim().ifBlank { AppSettings.DEFAULT_CAPTURE_URL }
        require(hub.isNotEmpty() && token.isNotEmpty()) { "hub and key are required" }
        require(HubUrls.looksSafeHub(hub)) { "Hub URL must be https" }
        return StudioPair(hubUrl = hub, hubToken = token, captureUrl = capture)
    }
}

data class BridgePair(
    val host: String,
    val port: Int,
    val https: Boolean,
    val token: String,
)

object BridgePairUrls {
    const val HOST = "bridge"

    fun build(
        host: String,
        token: String,
        port: Int = AppSettings.DEFAULT_BRIDGE_PORT,
        https: Boolean = false,
        exp: Long = 0L,
    ): String {
        val pair = requireValid(host, token, port, https)
        return Uri.Builder()
            .scheme(PairUrls.SCHEME)
            .authority(HOST)
            .appendQueryParameter("host", pair.host)
            .appendQueryParameter("port", pair.port.toString())
            .appendQueryParameter("https", if (pair.https) "1" else "0")
            .appendQueryParameter("token", pair.token)
            .apply { if (exp > 0L) appendQueryParameter("exp", exp.toString()) }
            .build()
            .toString()
    }

    fun parse(uri: Uri?): BridgePair? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme != PairUrls.SCHEME || !uri.host.equals(HOST, ignoreCase = true)) return null
        val exp = uri.getQueryParameter("exp")?.toLongOrNull() ?: 0L
        if (exp > 0L && exp < System.currentTimeMillis() / 1000L) return null
        val https = uri.getQueryParameter("https").orEmpty() in setOf("1", "true", "yes")
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: AppSettings.DEFAULT_BRIDGE_PORT
        return runCatching {
            requireValid(
                host = uri.getQueryParameter("host").orEmpty(),
                token = uri.getQueryParameter("token").orEmpty(),
                port = port,
                https = https,
            )
        }.getOrNull()
    }

    fun parse(raw: String): BridgePair? = runCatching { parse(Uri.parse(raw.trim())) }.getOrNull()

    fun requireValid(host: String, token: String, port: Int, https: Boolean): BridgePair {
        val cleaned = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
        require(cleaned.isNotEmpty() && token.trim().isNotEmpty()) { "host and token are required" }
        return BridgePair(
            host = cleaned,
            port = port.coerceIn(1, 65535),
            https = https,
            token = token.trim(),
        )
    }
}
