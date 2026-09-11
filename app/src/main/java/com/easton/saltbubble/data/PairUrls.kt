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
