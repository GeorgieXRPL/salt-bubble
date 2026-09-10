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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.ContentCopy
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easton.saltbubble.data.ChatMessage
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.MessageRole
import com.easton.saltbubble.data.MessageStatus
import com.easton.saltbubble.overlay.ChatController
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

@Composable
fun OverlayRoot(
    chat: ChatController,
    expanded: StateFlow<Boolean>,
    onToggle: () -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onIme: (Boolean) -> Unit,
) {
    val isExpanded by expanded.collectAsState()
    val recording by chat.recording.collectAsState()
    val busy by chat.busy.collectAsState()
    val messages by chat.messages.collectAsState()
    val destination by chat.destination.collectAsState()

    if (isExpanded) {
        MessengerPanel(
            messages = messages,
            recording = recording,
            busy = busy,
            destination = destination,
            onDestination = chat::setDestination,
            onCollapse = onCollapse,
            onDrag = onDrag,
            onIme = onIme,
            onHoldStart = chat::startTalk,
            onHoldEnd = chat::stopTalk,
            onSendTyped = chat::sendTyped,
            onSend = chat::send,
            onCopy = chat::copy,
            onGrok = chat::grokHelper,
        )
    } else {
        FloatingBubble(
            recording = recording,
            busy = busy,
            onDrag = onDrag,
            onTap = onToggle,
        )
    }
}

@Composable
private fun FloatingBubble(
    recording: Boolean,
    busy: Boolean,
    onDrag: (Float, Float) -> Unit,
    onTap: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "bubble-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val ring = if (recording) Cyan.copy(alpha = glow) else Cyan.copy(alpha = 0.7f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .bubbleGestures(onDrag = onDrag, onTap = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .background(
                    brush = Brush.radialGradient(listOf(CyanDim, Night)),
                    shape = CircleShape,
                )
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = CyanSoft,
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Salt Bubble",
                    tint = if (recording) CyanSoft else Cyan,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessengerPanel(
    messages: List<ChatMessage>,
    recording: Boolean,
    busy: Boolean,
    destination: Destination,
    onDestination: (Destination) -> Unit,
    onCollapse: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onIme: (Boolean) -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    onSendTyped: (String) -> Unit,
    onSend: (String, Destination) -> Unit,
    onCopy: (String) -> Unit,
    onGrok: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
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
                    .bubbleGestures(onDrag = onDrag, onTap = {})
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SALT BUBBLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cyan,
                    )
                    Text(
                        text = if (recording) "Listening" else if (busy) "Transcribing" else "Over your apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Panel)
                        .clickable(onClick = onCollapse),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Collapse", tint = Ink, modifier = Modifier.size(18.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Destination.entries.forEach { item ->
                    val selected = item == destination
                    Text(
                        text = item.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Night else CyanSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Cyan else Panel)
                            .clickable { onDestination(item) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                            text = "Hold the mic. Transcripts land here, then one tap toward Grok Bot or Brand Studio Capture.",
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
                        onGrok = onGrok,
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
                placeholder = { Text("Type if you cannot talk") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
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
                recording = recording,
                enabled = !busy,
                onHoldStart = onHoldStart,
                onHoldEnd = onHoldEnd,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageCard(
    message: ChatMessage,
    defaultDestination: Destination,
    onSend: (String, Destination) -> Unit,
    onCopy: (String) -> Unit,
    onGrok: (String) -> Unit,
) {
    val alignEnd = message.role == MessageRole.User
    val bg = when (message.role) {
        MessageRole.User -> UserBubble
        MessageRole.Status -> StatusBubble
        MessageRole.System -> StatusBubble
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniAction("Send ${defaultDestination.label}") { onSend(message.id, defaultDestination) }
                    MiniAction("Grok") { onGrok(message.id) }
                    MiniAction("Studio") { onSend(message.id, Destination.BrandStudio) }
                    MiniAction("Both") { onSend(message.id, Destination.Both) }
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
                        text = "Sent · ${message.sentTo.joinToString { it.label }}",
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
        color = CyanSoft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Night.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            .height(56.dp)
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
                    recording -> "RELEASE TO SEND"
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
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        var dragged = false
        drag(down.id) { change ->
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            val fromStart = change.position - down.position
            if (fromStart.getDistance() > viewConfiguration.touchSlop) {
                dragged = true
                change.consume()
                onDrag(dx, dy)
            }
        }
        if (!dragged) onTap()
    }
}
