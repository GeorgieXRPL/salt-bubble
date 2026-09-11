package com.easton.saltbubble.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "salt_bubble_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        read(prefs)
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.HOST] = next.bridgeHost.trim()
            prefs[Keys.PORT] = next.bridgePort.coerceIn(1, 65535)
            prefs[Keys.HTTPS] = next.bridgeUseHttps
            prefs[Keys.CAPTURE] = next.captureUrl.trim()
            prefs[Keys.DEEP_LINK] = next.captureDeepLink.trim()
            prefs[Keys.DESTINATION] = next.defaultDestination.name
            prefs[Keys.BRIDGE_TOKEN] = next.bridgeToken.trim()
            prefs[Keys.GROK_PACKAGE] = next.grokPackage.trim().ifBlank { AppSettings.DEFAULT_GROK_PACKAGE }
            prefs[Keys.HUB_URL] = next.hubUrl.trim().ifBlank { AppSettings.DEFAULT_HUB_URL }
            prefs[Keys.HUB_TOKEN] = next.hubToken.trim()
            prefs[Keys.POST_IDEA] = next.postIdeaOnSend
        }
    }

    private fun read(prefs: Preferences): AppSettings = AppSettings(
        bridgeHost = prefs[Keys.HOST] ?: AppSettings.DEFAULT_BRIDGE_HOST,
        bridgePort = prefs[Keys.PORT] ?: AppSettings.DEFAULT_BRIDGE_PORT,
        bridgeUseHttps = prefs[Keys.HTTPS] ?: false,
        captureUrl = prefs[Keys.CAPTURE] ?: AppSettings.DEFAULT_CAPTURE_URL,
        captureDeepLink = prefs[Keys.DEEP_LINK] ?: "",
        defaultDestination = runCatching {
            Destination.valueOf(prefs[Keys.DESTINATION] ?: Destination.Both.name)
        }.getOrDefault(Destination.Both),
        bridgeToken = prefs[Keys.BRIDGE_TOKEN] ?: "",
        grokPackage = prefs[Keys.GROK_PACKAGE]?.ifBlank { null } ?: AppSettings.DEFAULT_GROK_PACKAGE,
        hubUrl = prefs[Keys.HUB_URL]?.ifBlank { null } ?: AppSettings.DEFAULT_HUB_URL,
        hubToken = prefs[Keys.HUB_TOKEN] ?: "",
        postIdeaOnSend = prefs[Keys.POST_IDEA] ?: true,
    )

    private object Keys {
        val HOST = stringPreferencesKey("bridge_host")
        val PORT = intPreferencesKey("bridge_port")
        val HTTPS = booleanPreferencesKey("bridge_https")
        val CAPTURE = stringPreferencesKey("capture_url")
        val DEEP_LINK = stringPreferencesKey("capture_deeplink")
        val DESTINATION = stringPreferencesKey("default_destination")
        val BRIDGE_TOKEN = stringPreferencesKey("bridge_token")
        val GROK_PACKAGE = stringPreferencesKey("grok_package")
        val HUB_URL = stringPreferencesKey("hub_url")
        val HUB_TOKEN = stringPreferencesKey("hub_token")
        val POST_IDEA = booleanPreferencesKey("post_idea_on_send")
    }
}
