package com.easton.saltbubble.ui.home

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInNew
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.ui.theme.Cyan
import com.easton.saltbubble.ui.theme.CyanDim
import com.easton.saltbubble.ui.theme.CyanSoft
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

@Composable
fun HomeScreen(
    settings: AppSettings,
    permissions: PermissionSnapshot,
    onLaunch: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = "EASTON · HUD",
            style = MaterialTheme.typography.labelSmall,
            color = Cyan,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Salt Bubble",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Text(
            text = "Talk to Grok Bot and Brand Studio without leaving Camera or Chrome. Hold the floating mic, get a transcript, send it.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 8.dp, end = 12.dp),
        )

        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(CyanDim, Night)))
                .border(2.dp, Cyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = CyanSoft, modifier = Modifier.size(36.dp))
        }

        Spacer(Modifier.height(24.dp))
        PermissionRow(
            title = "Draw over other apps",
            detail = "Required so the bubble can sit on Camera, Chrome, and everything else.",
            granted = permissions.overlay,
            action = "Grant overlay",
            onAction = onRequestOverlay,
        )
        PermissionRow(
            title = "Microphone",
            detail = "Hold-to-talk records 16 kHz WAV for the PC Whisper bridge.",
            granted = permissions.mic,
            action = "Grant mic",
            onAction = onRequestMic,
        )
        if (Build.VERSION.SDK_INT >= 33) {
            PermissionRow(
                title = "Notifications",
                detail = "Foreground service needs a live notification while the overlay is up.",
                granted = permissions.notifications,
                action = "Allow",
                onAction = onRequestNotifications,
            )
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(NightElevated)
                .border(1.dp, Stroke, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text("BRIDGE", style = MaterialTheme.typography.labelSmall, color = Cyan)
            Spacer(Modifier.height(6.dp))
            Text(settings.bridgeBaseUrl, color = Ink, style = MaterialTheme.typography.bodyMedium)
            Text(
                "POST /transcribe  ·  fallback /transcribe.txt",
                color = InkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Capture → ${settings.captureUrl}",
                color = InkMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
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
            Text("Host, port, Capture URL")
        }

        Spacer(Modifier.height(24.dp))
        Text("WHILE YOU ARE IN ANOTHER APP", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        HowTo("1", "Grant overlay, then Launch bubble. A cyan mic appears over everything.")
        HowTo("2", "Tap the bubble to open the messenger. Drag it out of the way.")
        HowTo("3", "Hold HOLD TO TALK, speak, release. Salt Remote Bridge on your PC returns text.")
        HowTo("4", "Grok Bot copies + share sheet and toasts “Paste in Grok Bot”. There is no public inject API.")
        HowTo("5", "Brand Studio opens Capture and leaves the transcript on the clipboard.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(
                androidx.compose.ui.platform.LocalContext.current,
            )
        ) {
            // Keep the unused import path honest on older previews; the grant row already covers this.
        }
    }
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

@Composable
private fun HowTo(step: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(step, color = Cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
        Text(body, color = InkMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Suppress("unused")
private val overlayPermissionConstant = Manifest.permission.RECORD_AUDIO
