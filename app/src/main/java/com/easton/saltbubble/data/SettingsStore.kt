package com.easton.saltbubble.data

import android.content.Context
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
        AppSettings(
            bridgeHost = prefs[Keys.HOST] ?: AppSettings.DEFAULT_BRIDGE_HOST,
            bridgePort = prefs[Keys.PORT] ?: AppSettings.DEFAULT_BRIDGE_PORT,
            bridgeUseHttps = prefs[Keys.HTTPS] ?: false,
            captureUrl = prefs[Keys.CAPTURE] ?: AppSettings.DEFAULT_CAPTURE_URL,
            captureDeepLink = prefs[Keys.DEEP_LINK] ?: "",
            defaultDestination = runCatching {
                Destination.valueOf(prefs[Keys.DESTINATION] ?: Destination.Both.name)
            }.getOrDefault(Destination.Both),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                bridgeHost = prefs[Keys.HOST] ?: AppSettings.DEFAULT_BRIDGE_HOST,
                bridgePort = prefs[Keys.PORT] ?: AppSettings.DEFAULT_BRIDGE_PORT,
                bridgeUseHttps = prefs[Keys.HTTPS] ?: false,
                captureUrl = prefs[Keys.CAPTURE] ?: AppSettings.DEFAULT_CAPTURE_URL,
                captureDeepLink = prefs[Keys.DEEP_LINK] ?: "",
                defaultDestination = runCatching {
                    Destination.valueOf(prefs[Keys.DESTINATION] ?: Destination.Both.name)
                }.getOrDefault(Destination.Both),
            )
            val next = transform(current)
            prefs[Keys.HOST] = next.bridgeHost.trim()
            prefs[Keys.PORT] = next.bridgePort.coerceIn(1, 65535)
            prefs[Keys.HTTPS] = next.bridgeUseHttps
            prefs[Keys.CAPTURE] = next.captureUrl.trim()
            prefs[Keys.DEEP_LINK] = next.captureDeepLink.trim()
            prefs[Keys.DESTINATION] = next.defaultDestination.name
        }
    }

    private object Keys {
        val HOST = stringPreferencesKey("bridge_host")
        val PORT = intPreferencesKey("bridge_port")
        val HTTPS = booleanPreferencesKey("bridge_https")
        val CAPTURE = stringPreferencesKey("capture_url")
        val DEEP_LINK = stringPreferencesKey("capture_deeplink")
        val DESTINATION = stringPreferencesKey("default_destination")
    }
}
