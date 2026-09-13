package com.easton.saltbubble.ui.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.GrokLand
import com.easton.saltbubble.ui.theme.Amber
import com.easton.saltbubble.ui.theme.Cyan
import com.easton.saltbubble.ui.theme.Ink
import com.easton.saltbubble.ui.theme.InkMuted
import com.easton.saltbubble.ui.theme.Night
import com.easton.saltbubble.ui.theme.NightElevated
import com.easton.saltbubble.ui.theme.Panel
import com.easton.saltbubble.ui.theme.Stroke

data class PermissionSnapshot(
    val overlay: Boolean,
    val mic: Boolean,
    val notifications: Boolean,
    val overlayRunning: Boolean,
) {
    val ready: Boolean get() = overlay && mic && notifications
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    settings: AppSettings,
    permissions: PermissionSnapshot,
    grokInstalled: Boolean,
    bridgePing: String,
    pairStatus: String,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onPingBridge: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
    onGrokLand: (GrokLand) -> Unit,
) {
    val paired = pairStatus.isNotBlank() || settings.hubToken.isNotBlank()
    val tokenOk = settings.bridgeToken.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text("EASTON · HUD", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        Text("Salt Bubble", style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text(
            text = "Float over Camera. Wake Salt, speak a task, send it to Grok or Brand Studio.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 8.dp, end = 12.dp),
        )

        if (permissions.overlayRunning) {
            Spacer(Modifier.height(16.dp))
            StatusBanner(
                title = "Bubble is up",
                detail = "Amber = standby. Cyan = listening. Say Salt wake up.",
                accent = Amber,
            )
        }

        Spacer(Modifier.height(20.dp))
        if (permissions.ready) {
            StatusBanner(
                title = "Permissions ready",
                detail = "Overlay · mic" + if (Build.VERSION.SDK_INT >= 33) " · notifications" else "",
                accent = Cyan,
            )
        } else {
            PermissionRow(
                title = "Draw over other apps",
                detail = "So the bubble can sit on Camera and Chrome.",
                granted = permissions.overlay,
                action = "Grant overlay",
                onAction = onRequestOverlay,
            )
            PermissionRow(
                title = "Microphone",
                detail = "Session listen and hold-to-talk.",
                granted = permissions.mic,
                action = "Grant mic",
                onAction = onRequestMic,
            )
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow(
                    title = "Notifications",
                    detail = "Keeps the overlay alive in the background.",
                    granted = permissions.notifications,
                    action = "Allow",
                    onAction = onRequestNotifications,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = if (permissions.overlayRunning) onStop else onLaunch,
            enabled = permissions.overlayRunning || permissions.ready,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Night),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                if (permissions.overlayRunning) "Stop bubble" else "Launch bubble",
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("Bridge, Grok, Capture")
        }

        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NightElevated)
                .border(1.dp, Stroke, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text("READY", style = MaterialTheme.typography.labelSmall, color = Cyan)
            Spacer(Modifier.height(12.dp))
            ReadyRow(
                title = if (tokenOk) "Bridge paired" else "Bridge not paired",
                detail = buildString {
                    append("Private tailnet")
                    if (bridgePing.isNotBlank()) {
                        append(" · ")
                        append(bridgePing)
                    } else {
                        append(" — open PAIR PHONE from the PC, or Ping")
                    }
                },
                ok = tokenOk && (
                    bridgePing.isBlank() ||
                        bridgePing.startsWith("Pinging") ||
                        bridgePing.contains("up", ignoreCase = true)
                    ),
            )
            ReadyRow(
                title = "Grok on ${settings.grokLand.label.lowercase()}",
                detail = when (settings.grokLand) {
                    GrokLand.Phone -> if (grokInstalled) {
                        "Opens Grok Bot on this phone. Paste once — no inject API."
                    } else {
                        "Grok Bot not installed — share still copies the line."
                    }
                    GrokLand.Pc -> "Pastes into Grok on your PC. Phone share stays off."
                    GrokLand.Both -> "PC paste first, phone share if the bridge misses."
                },
                ok = settings.grokLand == GrokLand.Pc || grokInstalled || settings.grokLand == GrokLand.Both,
            )
            ReadyRow(
                title = if (paired) "Studio paired" else "Studio not paired",
                detail = when {
                    pairStatus.isNotBlank() -> pairStatus
                    paired -> "Agent key can save ideas only"
                    else -> "Agents → Open Salt Bubble"
                },
                ok = paired,
            )
            Spacer(Modifier.height(4.dp))
            Text("WHERE SHOULD GROK LAND?", style = MaterialTheme.typography.labelSmall, color = Cyan)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrokLand.entries.forEach { item ->
                    val selected = item == settings.grokLand
                    Text(
                        text = item.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Night else Cyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Cyan else Panel)
                            .border(1.dp, if (selected) Cyan else Stroke, RoundedCornerShape(8.dp))
                            .clickable { onGrokLand(item) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPingBridge, shape = RoundedCornerShape(10.dp)) {
                Text("Ping bridge")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("SAY", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PhraseChip("Salt wake up")
            PhraseChip("Salt send to Grok")
            PhraseChip("Salt send to Brand Studio")
            PhraseChip("Salt clip that")
            PhraseChip("Salt go on standby")
        }
        Text(
            "Tasks auto-send while listening. Hold the mic anytime for a manual take.",
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun StatusBanner(title: String, detail: String, accent: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ReadyRow(title: String, detail: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (ok) Cyan else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 10.dp, top = 2.dp),
        )
        Column {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun PhraseChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Cyan,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Panel)
            .border(1.dp, Stroke, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (granted) Cyan else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (!granted) {
            FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(10.dp)) {
                Text(action)
            }
        }
    }
}
