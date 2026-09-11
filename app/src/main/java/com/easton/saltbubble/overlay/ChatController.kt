package com.easton.saltbubble.overlay

import android.app.Application
import com.easton.saltbubble.audio.WavRecorder
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.ChatMessage
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.MessageRole
import com.easton.saltbubble.data.MessageStatus
import com.easton.saltbubble.data.SettingsStore
import com.easton.saltbubble.net.BrandStudioClient
import com.easton.saltbubble.net.TranscriptionClient
import com.easton.saltbubble.saltBubble
import com.easton.saltbubble.send.SendHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ChatController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore = app.saltBubble.settingsStore,
    private val client: TranscriptionClient = app.saltBubble.transcriptionClient,
    private val brandStudio: BrandStudioClient = app.saltBubble.brandStudioClient,
    private val sendHelper: SendHelper = app.saltBubble.sendHelper,
    private val recorder: WavRecorder = WavRecorder(app),
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _destination = MutableStateFlow(Destination.Both)
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private var settings: AppSettings = AppSettings()
    private var pendingFile: File? = null
    private var inflight: Job? = null
    private var seeded = false
    private var lastIdeaText = ""
    private var lastIdeaAt = 0L

    init {
        scope.launch {
            settingsStore.settings.collect { next ->
                settings = next
                if (_destination.value == Destination.Both || !seeded) {
                    _destination.value = next.defaultDestination
                    seeded = true
                }
            }
        }
    }

    fun setDestination(destination: Destination) {
        _destination.value = destination
    }

    fun startTalk() {
        if (_recording.value || _busy.value) return
        runCatching { recorder.start() }
            .onSuccess { file ->
                pendingFile = file
                _recording.value = true
                upsert(
                    ChatMessage(
                        id = RECORDING_ID,
                        role = MessageRole.Status,
                        text = "Listening… release to transcribe",
                        status = MessageStatus.Recording,
                    ),
                )
            }
            .onFailure { error ->
                push(
                    ChatMessage(
                        role = MessageRole.System,
                        text = error.message ?: "Could not start the microphone",
                        status = MessageStatus.Failed,
                    ),
                )
            }
    }

    fun stopTalk() {
        if (!_recording.value) return
        _recording.value = false
        val file = recorder.stop()
        pendingFile = file
        val durationOk = file != null && file.length() > 80
        replace(
            RECORDING_ID,
            ChatMessage(
                id = RECORDING_ID,
                role = MessageRole.Status,
                text = if (durationOk) "Transcribing on your PC…" else "That take was too short. Hold a little longer.",
                status = if (durationOk) MessageStatus.Transcribing else MessageStatus.Failed,
            ),
        )
        if (!durationOk) {
            file?.delete()
            return
        }
        transcribe(file!!)
    }

    fun sendTyped(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val message = ChatMessage(
            role = MessageRole.User,
            text = trimmed,
            status = MessageStatus.Ready,
        )
        push(message)
        send(message.id, _destination.value)
    }

    fun send(messageId: String, destination: Destination) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (message.text.isBlank() || message.status != MessageStatus.Ready) return
        sendHelper.send(message.text, destination, settings)
        _messages.update { list ->
            list.map {
                if (it.id == messageId) it.copy(sentTo = it.sentTo + destination) else it
            }
        }
        maybePostIdea(message.text, destination)
    }

    fun copy(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        sendHelper.copyOnly(message.text)
    }

    fun grokHelper(messageId: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        sendHelper.grokPasteHelper(message.text, settings)
        _messages.update { list ->
            list.map {
                if (it.id == messageId) it.copy(sentTo = it.sentTo + Destination.GrokBot) else it
            }
        }
    }

    fun openStudio() {
        sendHelper.openStudio(settings)
    }

    private fun maybePostIdea(text: String, destination: Destination) {
        val snapshot = settings
        if (!snapshot.postIdeaOnSend) return
        if (snapshot.hubToken.isBlank()) return
        if (!destination.includesBrandStudio()) return
        val now = System.currentTimeMillis()
        if (text == lastIdeaText && now - lastIdeaAt < 8_000) return
        lastIdeaText = text
        lastIdeaAt = now
        scope.launch {
            runCatching { brandStudio.addIdea(snapshot.hubUrl, snapshot.hubToken, text) }
                .onSuccess {
                    push(
                        ChatMessage(
                            role = MessageRole.Status,
                            text = "Saved to Brand Studio ideas",
                        ),
                    )
                }
                .onFailure { error ->
                    val detail = error.message ?: error.javaClass.simpleName
                    push(
                        ChatMessage(
                            role = MessageRole.Status,
                            text = "Could not POST idea: $detail",
                        ),
                    )
                }
        }
    }

    private fun transcribe(file: File) {
        inflight?.cancel()
        _busy.value = true
        inflight = scope.launch {
            val snap = settingsStore.settings.first()
            val base = snap.bridgeBaseUrl
            runCatching { client.transcribe(base, file, snap.bridgeToken) }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        replace(
                            RECORDING_ID,
                            ChatMessage(
                                id = RECORDING_ID,
                                role = MessageRole.Status,
                                text = "Heard silence. Hold a little longer.",
                                status = MessageStatus.Failed,
                            ),
                        )
                    } else {
                        remove(RECORDING_ID)
                        push(
                            ChatMessage(
                                role = MessageRole.User,
                                text = text,
                                status = MessageStatus.Ready,
                            ),
                        )
                    }
                }
                .onFailure { error ->
                    replace(
                        RECORDING_ID,
                        ChatMessage(
                            id = RECORDING_ID,
                            role = MessageRole.System,
                            text = friendlyError(error, base),
                            status = MessageStatus.Failed,
                        ),
                    )
                }
            file.delete()
            _busy.value = false
        }
    }

    private fun friendlyError(error: Throwable, base: String): String {
        val raw = error.message ?: error.javaClass.simpleName
        return when {
            raw.contains("Failed to connect", ignoreCase = true) ||
                raw.contains("Unable to resolve", ignoreCase = true) ||
                raw.contains("timeout", ignoreCase = true) ->
                "Could not reach $base. Is Tailscale up and is saltvoice-local running?"
            raw.contains("rejected the token", ignoreCase = true) ||
                raw.contains("HTTP 401") ->
                "Bridge rejected the token. Paste the BRIDGE TOKEN from the PC console into Settings."
            else -> raw
        }
    }

    private fun push(message: ChatMessage) {
        _messages.update { it + message }
    }

    private fun upsert(message: ChatMessage) {
        _messages.update { list ->
            val without = list.filterNot { it.id == message.id }
            without + message
        }
    }

    private fun replace(id: String, message: ChatMessage) {
        _messages.update { list ->
            if (list.any { it.id == id }) list.map { if (it.id == id) message else it } else list + message
        }
    }

    private fun remove(id: String) {
        _messages.update { list -> list.filterNot { it.id == id } }
    }

    companion object {
        private const val RECORDING_ID = "recording-in-flight"
    }
}
