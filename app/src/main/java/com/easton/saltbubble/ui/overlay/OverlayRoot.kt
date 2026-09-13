package com.easton.saltbubble.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.ChatMessage
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.MessageRole
import com.easton.saltbubble.data.MessageStatus
import com.easton.saltbubble.data.SessionPhase
import com.easton.saltbubble.overlay.ChatController
import com.easton.saltbubble.ui.theme.Amber
import com.easton.saltbubble.ui.theme.AmberDim
import com.easton.saltbubble.ui.theme.Cyan
import com.easton.saltbubble.ui.theme.CyanDim
import com.easton.saltbubble.ui.theme.CyanSoft
import com.easton.saltbubble.ui.theme.Ink
import com.easton.saltbubble.ui.theme.InkMuted
import com.easton.saltbubble.ui.theme.Night
import com.easton.saltbubble.ui.theme.Panel
import com.easton.saltbubble.ui.theme.StatusBubble
import com.easton.saltbubble.ui.theme.Stroke
import com.easton.saltbubble.ui.theme.UserBubble
import kotlinx.coroutines.flow.StateFlow

private enum class HudMode {
    Standby,
    Listening,
    Hold,
    Transcribing,
}

private val Destination.chipLabel: String
    get() = when (this) {
        Destination.GrokBot -> "Grok"
        Destination.BrandStudio -> "Studio"
        Destination.Both -> "Both"
    }

@Composable
fun OverlayRoot(
    chat: ChatController,
    expanded: StateFlow<Boolean>,
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onIme: (Boolean) -> Unit,
) {
    val isExpanded by expanded.collectAsState()
    val recording by chat.recording.collectAsState()
    val busy by chat.busy.collectAsState()
    val messages by chat.messages.collectAsState()
    val destination by chat.destination.collectAsState()
    val phase by chat.phase.collectAsState()
    val sessionStatus by chat.sessionStatus.collectAsState()
    val mode = when {
        recording -> HudMode.Hold
        busy -> HudMode.Transcribing
        phase == SessionPhase.Listening -> HudMode.Listening
        else -> HudMode.Standby
    }

    if (isExpanded) {
        MessengerPanel(
            messages = messages,
            mode = mode,
            destination = destination,
            sessionStatus = sessionStatus,
            onDestination = chat::setDestination,
            onCollapse = onCollapse,
            onClose = onClose,
            onClip = { chat.saveClipMark("bubble") },
            onDrag = onDrag,
            onIme = onIme,
            onHoldStart = chat::startTalk,
            onHoldEnd = chat::stopTalk,
            onSendTyped = chat::sendTyped,
            onSend = chat::send,
            onCopy = chat::copy,
        )
    } else {
        FloatingBubble(
            mode = mode,
            onDrag = onDrag,
            onTap = onToggle,
            onClose = onClose,
        )
    }
}

@Composable
private fun FloatingBubble(
    mode: HudMode,
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 140.dp.toPx() }
    val pulse = rememberInfiniteTransition(label = "bubble-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mode == HudMode.Hold) 520 else 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val accent = when (mode) {
        HudMode.Standby -> Amber
        HudMode.Listening, HudMode.Hold -> Cyan
        HudMode.Transcribing -> CyanSoft
    }
    val ring = when (mode) {
        HudMode.Standby -> Amber.copy(alpha = 0.55f)
        HudMode.Listening, HudMode.Hold -> accent.copy(alpha = glow)
        HudMode.Transcribing -> Cyan.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .bubbleGestures(onDrag = onDrag, onTap = onTap, onDismiss = onClose, dismissThresholdPx = dismissThresholdPx),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        listOf(if (mode == HudMode.Standby) AmberDim else CyanDim, Night),
                    ),
                    shape = CircleShape,
                )
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (mode) {
                HudMode.Transcribing -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = CyanSoft,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = when (mode) {
                        HudMode.Standby -> "Salt Bubble standby"
                        HudMode.Listening -> "Salt Bubble listening"
                        HudMode.Hold -> "Salt Bubble recording"
                        HudMode.Transcribing -> "Salt Bubble transcribing"
                    },
                    tint = if (mode == HudMode.Hold) CyanSoft else accent,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun MessengerPanel(
    messages: List<ChatMessage>,
    mode: HudMode,
    destination: Destination,
    sessionStatus: String,
    onDestination: (Destination) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onClip: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onIme: (Boolean) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onSendTyped: (String) -> Unit,
    onSend: (String, Destination) -> Unit,
    onCopy: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 140.dp.toPx() }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    val headline = when (mode) {
        HudMode.Hold -> "Release to transcribe"
        HudMode.Transcribing -> "Whisper on your PC…"
        else -> sessionStatus
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .shadow(18.dp, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = Night,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Stroke, RoundedCornerShape(22.dp))
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bubbleGestures(onDrag = onDrag, onTap = {}, onDismiss = onClose, dismissThresholdPx = dismissThresholdPx)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SALT BUBBLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan,
                    )
                    Spacer(Modifier.height(6.dp))
                    PhaseChip(mode = mode)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircleAction(Icons.Outlined.ContentCut, "Clip", onClip)
                    CircleAction(Icons.Outlined.Remove, "Collapse", onCollapse)
                    CircleAction(Icons.Outlined.Close, "Close", onClose)
                }
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Destination.entries.forEach { item ->
                    val selected = item == destination
                    Text(
                        text = item.chipLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Night else CyanSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Cyan else Panel)
                            .clickable { onDestination(item) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Panel)
                    .padding(horizontal = 8.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = if (mode == HudMode.Listening) {
                                "Listening. Speak a task — it sends to ${destination.label}. Say Salt go on standby when done."
                            } else {
                                "Say Salt wake up. Ambient talk stays off the wire. Hold the mic for a take, or tap Send."
                            },
                            color = InkMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        defaultDestination = destination,
                        onSend = onSend,
                        onCopy = onCopy,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            TextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onIme(it.isFocused) },
                placeholder = { Text("Type a task") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draft.trim().isEmpty()) return@KeyboardActions
                        onSendTyped(draft)
                        draft = ""
                    },
                ),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Send,
                        contentDescription = "Send text",
                        tint = Cyan,
                        modifier = Modifier
                            .clickable {
                                if (draft.trim().isEmpty()) return@clickable
                                onSendTyped(draft)
                                draft = ""
                            }
                            .padding(6.dp),
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Cyan,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink,
                ),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(8.dp))
            HoldToTalkButton(
                recording = mode == HudMode.Hold,
                enabled = mode != HudMode.Transcribing,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd,
            )
        }
    }
}

@Composable
private fun PhaseChip(mode: HudMode) {
    val (label, fg, bg) = when (mode) {
        HudMode.Standby -> Triple("STANDBY", Amber, AmberDim)
        HudMode.Listening -> Triple("LISTENING", Night, Cyan)
        HudMode.Hold -> Triple("HOLD", Night, CyanSoft)
        HudMode.Transcribing -> Triple("WHISPER", CyanSoft, Panel)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun CircleAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Panel)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (label == "Clip") Cyan else Ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    defaultDestination: Destination,
    onSend: (String, Destination) -> Unit,
    onCopy: (String) -> Unit,
) {
    val alignEnd = message.role == MessageRole.User
    val bg = when (message.role) {
        MessageRole.User -> UserBubble
        MessageRole.Status, MessageRole.System -> StatusBubble
    }
    val color = if (message.status == MessageStatus.Failed) MaterialTheme.colorScheme.error else Ink
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            text = when (message.role) {
                MessageRole.User -> "YOU"
                MessageRole.Status -> "SALT"
                MessageRole.System -> "STATUS"
            },
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp),
        )
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(10.dp),
        ) {
            Text(
                text = message.text,
                color = color,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (message.role == MessageRole.User && message.status == MessageStatus.Ready) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniAction("Send ${defaultDestination.chipLabel}") { onSend(message.id, defaultDestination) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Night.copy(alpha = 0.4f))
                            .clickable { onCopy(message.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = CyanSoft, modifier = Modifier.size(12.dp))
                    }
                }
                if (message.sentTo.isNotEmpty()) {
                    Text(
                        text = "Sent · ${message.sentTo.joinToString { it.chipLabel }}",
                        color = CyanSoft,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Night,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Cyan)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun HoldToTalkButton(
    recording: Boolean,
    enabled: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val pulse = rememberInfiniteTransition(label = "ptt")
    val glow by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ptt-glow",
    )
    val bg = when {
        !enabled -> Panel
        recording -> Cyan.copy(alpha = glow)
        else -> CyanDim
    }
    val fg = if (recording) Night else Cyan
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, if (recording) CyanSoft else Stroke, RoundedCornerShape(16.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHoldStart()
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.any { it.pressed }
                        } while (pressed)
                    } finally {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onHoldEnd()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = fg)
            Text(
                text = when {
                    !enabled -> "TRANSCRIBING"
                    recording -> "RELEASE"
                    else -> "HOLD TO TALK"
                },
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun Modifier.bubbleGestures(
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    dismissThresholdPx: Float = 0f,
): Modifier = pointerInput(dismissThresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown()
        var dragged = false
        var totalDy = 0f
        val completed = drag(down.id) { change ->
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            val fromStart = change.position - down.position
            if (fromStart.getDistance() > viewConfiguration.touchSlop) {
                dragged = true
                totalDy += dy
                change.consume()
                onDrag(dx, dy)
            }
        }
        when {
            completed && dragged && onDismiss != null && totalDy >= dismissThresholdPx -> onDismiss()
            !dragged -> onTap()
        }
    }
}
