package com.easton.saltbubble.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.BridgeUrls
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.GrokLand
import com.easton.saltbubble.data.SettingsStore
import com.easton.saltbubble.ui.theme.Cyan
import com.easton.saltbubble.ui.theme.Ink
import com.easton.saltbubble.ui.theme.InkMuted
import com.easton.saltbubble.ui.theme.Night
import com.easton.saltbubble.ui.theme.NightElevated
import com.easton.saltbubble.ui.theme.Stroke
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
    var bridgeToken by remember(settings.bridgeToken) { mutableStateOf(settings.bridgeToken) }
    var grokPackage by remember(settings.grokPackage) { mutableStateOf(settings.grokPackage) }
    var hubUrl by remember(settings.hubUrl) { mutableStateOf(settings.hubUrl) }
    var hubToken by remember(settings.hubToken) { mutableStateOf(settings.hubToken) }
    var postIdeaOnSend by remember(settings.postIdeaOnSend) { mutableStateOf(settings.postIdeaOnSend) }
    var grokLand by remember(settings.grokLand) { mutableStateOf(settings.grokLand) }
    var advanced by remember { mutableStateOf(false) }

    fun current(): AppSettings = AppSettings(
        bridgeHost = host,
        bridgePort = port.toIntOrNull() ?: settings.bridgePort,
        bridgeUseHttps = https,
        captureUrl = capture,
        captureDeepLink = deepLink,
        defaultDestination = destination,
        bridgeToken = bridgeToken,
        grokPackage = grokPackage,
        hubUrl = hubUrl,
        hubToken = hubToken,
        postIdeaOnSend = postIdeaOnSend,
        grokLand = grokLand,
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
        Text(
            "Open PAIR PHONE from the PC console on this phone. Host and token fill in. Stay on the same tailnet.",
            color = InkMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp),
        )

        Spacer(Modifier.height(20.dp))
        SettingsCard("WHERE SHOULD GROK LAND", "Phone is safest. PC paste is optional and privileged.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GrokLand.entries.forEach { item ->
                    FilterChip(
                        selected = grokLand == item,
                        onClick = {
                            grokLand = item
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
        }

        Spacer(Modifier.height(14.dp))
        SettingsCard("WHISPER BRIDGE", "Private Tailscale hop. Pair from the PC instead of typing a token.") {
            Text(
                if (bridgeToken.isNotBlank()) "Token set · private tailnet" else "Not paired — open PAIR PHONE on this device",
                color = InkMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = bridgeToken,
                onValueChange = {
                    bridgeToken = it
                    commit()
                },
                label = { Text("Bridge token") },
                placeholder = { Text("or pair from the PC") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp),
            )
        }

        Spacer(Modifier.height(14.dp))
        SettingsCard("BRAND STUDIO", "Pair from Agents → Open Salt Bubble, or paste an ak_ key. The key can save ideas only.") {
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
                    Text("POST idea when sending to Studio", color = Ink)
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
        }

        Spacer(Modifier.height(14.dp))
        SettingsCard("DEFAULT SEND", "Voice tasks and the Send button use this until you say Salt send to Grok or Brand Studio.") {
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
        }

        Spacer(Modifier.height(14.dp))
        Text(
            if (advanced) "HIDE ADVANCED" else "ADVANCED",
            style = MaterialTheme.typography.labelSmall,
            color = Cyan,
            modifier = Modifier
                .clickable { advanced = !advanced }
                .padding(vertical = 8.dp),
        )
        if (advanced) {
            SettingsCard("HOST AND URLS", "Leave HTTPS off. HTTP stays inside Tailscale.") {
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
                        Text("Off for the default Tailscale HTTP bridge.", color = InkMuted, style = MaterialTheme.typography.bodySmall)
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
                    "Resolved  ${BridgeUrls.baseUrl(host, port.toIntOrNull() ?: AppSettings.DEFAULT_BRIDGE_PORT, https)}",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(10.dp))
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
                Spacer(Modifier.height(10.dp))
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
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    hint: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightElevated)
            .border(1.dp, Stroke, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Cyan)
        Text(
            hint,
            color = InkMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
        )
        content()
    }
}
