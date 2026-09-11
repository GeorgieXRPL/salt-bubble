package com.easton.saltbubble.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.BridgeUrls
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.SettingsStore
import com.easton.saltbubble.ui.theme.Cyan
import com.easton.saltbubble.ui.theme.Ink
import com.easton.saltbubble.ui.theme.InkMuted
import com.easton.saltbubble.ui.theme.Night
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    store: SettingsStore,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var host by remember(settings.bridgeHost) { mutableStateOf(settings.bridgeHost) }
    var port by remember(settings.bridgePort) { mutableStateOf(settings.bridgePort.toString()) }
    var https by remember(settings.bridgeUseHttps) { mutableStateOf(settings.bridgeUseHttps) }
    var capture by remember(settings.captureUrl) { mutableStateOf(settings.captureUrl) }
    var deepLink by remember(settings.captureDeepLink) { mutableStateOf(settings.captureDeepLink) }
    var destination by remember(settings.defaultDestination) { mutableStateOf(settings.defaultDestination) }
    var grokPackage by remember(settings.grokPackage) { mutableStateOf(settings.grokPackage) }
    var hubUrl by remember(settings.hubUrl) { mutableStateOf(settings.hubUrl) }
    var hubToken by remember(settings.hubToken) { mutableStateOf(settings.hubToken) }
    var postIdeaOnSend by remember(settings.postIdeaOnSend) { mutableStateOf(settings.postIdeaOnSend) }

    fun current(): AppSettings = AppSettings(
        bridgeHost = host,
        bridgePort = port.toIntOrNull() ?: settings.bridgePort,
        bridgeUseHttps = https,
        captureUrl = capture,
        captureDeepLink = deepLink,
        defaultDestination = destination,
        grokPackage = grokPackage,
        hubUrl = hubUrl,
        hubToken = hubToken,
        postIdeaOnSend = postIdeaOnSend,
    )

    fun commit(next: AppSettings = current()) {
        scope.launch { store.update { next } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Ink)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "saltvoice-local on your Windows PC. Enable remote_bridge (default). Phone reaches it over Tailscale HTTP.",
            color = InkMuted,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(20.dp))
        Text("SALTVOICE-LOCAL BRIDGE", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                commit()
            },
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { value ->
                port = value.filter { it.isDigit() }.take(5)
                val parsed = port.toIntOrNull() ?: return@OutlinedTextField
                commit(current().copy(bridgePort = parsed))
            },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Use HTTPS", color = Ink)
                Text("Leave off for the default Tailscale HTTP bridge.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = https,
                onCheckedChange = {
                    https = it
                    commit()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
            )
        }
        Text(
            "Resolved URL  ${BridgeUrls.baseUrl(host, port.toIntOrNull() ?: AppSettings.DEFAULT_BRIDGE_PORT, https)}",
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text("GROK BOT", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = grokPackage,
            onValueChange = {
                grokPackage = it
                commit()
            },
            label = { Text("Grok package") },
            placeholder = { Text(AppSettings.DEFAULT_GROK_PACKAGE) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Text(
            "Opens Grok Bot (ai.x.grok.bot) with the line on the clipboard. No inject API — paste once if share doesn't land in the composer.",
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text("BRAND STUDIO", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = capture,
            onValueChange = {
                capture = it
                commit()
            },
            label = { Text("Capture URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = deepLink,
            onValueChange = {
                deepLink = it
                commit()
            },
            label = { Text("Optional deep link") },
            placeholder = { Text("mybrandstudio://capture") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = hubUrl,
            onValueChange = {
                hubUrl = it
                commit()
            },
            label = { Text("Hub URL") },
            placeholder = { Text(AppSettings.DEFAULT_HUB_URL) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = hubToken,
            onValueChange = {
                hubToken = it
                commit()
            },
            label = { Text("Hub token") },
            placeholder = { Text("ak_…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("POST idea to hub when sending to Brand Studio", color = Ink)
                Text("Skipped when the token is blank.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = postIdeaOnSend,
                onCheckedChange = {
                    postIdeaOnSend = it
                    commit()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Cyan),
            )
        }
        Text(
            "Opens Capture with ?text= prefilled. If you paste an Agent API key (ak_…), Salt Bubble also POSTs /api/ideas.",
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(24.dp))
        Text("DEFAULT SEND TARGET", style = MaterialTheme.typography.labelSmall, color = Cyan)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Destination.entries.forEach { item ->
                FilterChip(
                    selected = destination == item,
                    onClick = {
                        destination = item
                        commit()
                    },
                    label = { Text(item.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan,
                        selectedLabelColor = Night,
                    ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
