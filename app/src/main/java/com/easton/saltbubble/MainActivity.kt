package com.easton.saltbubble

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.overlay.BubbleService
import com.easton.saltbubble.send.GrokApps
import com.easton.saltbubble.ui.home.HomeScreen
import com.easton.saltbubble.ui.home.PermissionSnapshot
import com.easton.saltbubble.ui.settings.SettingsScreen
import com.easton.saltbubble.ui.theme.SaltBubbleTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val settingsFlow by lazy {
        application.saltBubble.settingsStore.settings.stateIn(
            lifecycleScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettings(),
        )
    }

    private var showSettings by mutableStateOf(false)
    private var permissionTick by mutableStateOf(0)
    private var bridgePing by mutableStateOf("")
    private var pairStatus by mutableStateOf("")

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    private val notificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPairIntent(intent)
        setContent {
            val settings by settingsFlow.collectAsState()
            val tick = permissionTick
            SaltBubbleTheme {
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        store = application.saltBubble.settingsStore,
                        onBack = { showSettings = false },
                    )
                } else {
                    HomeScreen(
                        settings = settings,
                        permissions = currentPermissions(tick),
                        grokInstalled = GrokApps(packageManager).anyInstalled(settings.grokPackage),
                        bridgePing = bridgePing,
                        pairStatus = pairStatus,
                        onLaunch = ::launchBubble,
                        onStop = { BubbleService.stop(this) },
                        onSettings = { showSettings = true },
                        onPingBridge = { pingBridge(settings) },
                        onRequestMic = {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestOverlay = ::openOverlaySettings,
                        onGrokLand = { land ->
                            lifecycleScope.launch {
                                application.saltBubble.settingsStore.update { it.copy(grokLand = land) }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPairIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        permissionTick++
    }

    private fun applyPairIntent(intent: Intent?) {
        val data = intent?.data
        val studio = com.easton.saltbubble.data.PairUrls.parse(data)
        val bridge = com.easton.saltbubble.data.BridgePairUrls.parse(data)
        if (studio == null && bridge == null) return
        lifecycleScope.launch {
            if (studio != null) {
                application.saltBubble.settingsStore.update { current ->
                    current.copy(
                        hubUrl = studio.hubUrl,
                        hubToken = studio.hubToken,
                        captureUrl = studio.captureUrl.ifBlank { current.captureUrl },
                        postIdeaOnSend = true,
                    )
                }
                pairStatus = "Paired with Brand Studio"
            }
            if (bridge != null) {
                application.saltBubble.settingsStore.update { current ->
                    current.copy(
                        bridgeHost = bridge.host,
                        bridgePort = bridge.port,
                        bridgeUseHttps = bridge.https,
                        bridgeToken = bridge.token,
                    )
                }
                pairStatus = "Paired with this PC over Tailscale"
                runCatching {
                    application.saltBubble.bridgeClient.pair(
                        com.easton.saltbubble.data.BridgeUrls.baseUrl(
                            bridge.host,
                            bridge.port,
                            bridge.https,
                        ),
                        bridge.token,
                    )
                }.onFailure { error ->
                    pairStatus = "Bridge saved — pin failed (${error.message ?: error.javaClass.simpleName})"
                }
            }
        }
    }

    private fun currentPermissions(tick: Int): PermissionSnapshot {
        @Suppress("UNUSED_VARIABLE")
        val observed = tick
        val notificationsOk = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return PermissionSnapshot(
            overlay = Settings.canDrawOverlays(this),
            mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            notifications = notificationsOk,
            overlayRunning = BubbleService.isLikelyRunning(this),
        )
    }

    private fun pingBridge(settings: AppSettings) {
        bridgePing = "Pinging…"
        lifecycleScope.launch {
            runCatching { application.saltBubble.transcriptionClient.health(settings.bridgeBaseUrl) }
                .onSuccess { health ->
                    bridgePing = if (health.ok) {
                        if (health.authRequired && settings.bridgeToken.isBlank()) {
                            "Bridge is up — paste the BRIDGE TOKEN in Settings"
                        } else {
                            "Bridge is up"
                        }
                    } else {
                        "Bridge answered but is not healthy"
                    }
                }
                .onFailure { error ->
                    val raw = error.message ?: error.javaClass.simpleName
                    bridgePing = if (raw.contains("Failed to connect", ignoreCase = true) ||
                        raw.contains("Unable to resolve", ignoreCase = true)
                    ) {
                        "Could not reach ${settings.bridgeBaseUrl}"
                    } else {
                        raw.take(120)
                    }
                }
        }
    }

    private fun launchBubble() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        BubbleService.start(this)
        permissionTick++
        moveTaskToBack(true)
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }
}
