package com.easton.saltbubble.overlay

import android.app.Application
import com.easton.saltbubble.audio.WavRecorder
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.ChatMessage
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.MessageRole
import com.easton.saltbubble.data.MessageStatus
import com.easton.saltbubble.data.Phrases
import com.easton.saltbubble.data.SessionPhase
import com.easton.saltbubble.data.SettingsStore
import com.easton.saltbubble.net.BrandStudioClient
import com.easton.saltbubble.net.BridgeClient
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
    private val bridgeClient: BridgeClient = app.saltBubble.bridgeClient,
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

    private val _phase = MutableStateFlow(SessionPhase.Standby)
    val phase: StateFlow<SessionPhase> = _phase.asStateFlow()

    private val _sessionStatus = MutableStateFlow("Standby. Say Salt wake up.")
    val sessionStatus: StateFlow<String> = _sessionStatus.asStateFlow()

    private val _sessionPaused = MutableStateFlow(false)
    val sessionPaused: StateFlow<Boolean> = _sessionPaused.asStateFlow()

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
        _sessionPaused.value = true
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
                _sessionPaused.value = false
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
            _sessionPaused.value = false
            return
        }
        transcribe(file!!)
    }

    fun sendTyped(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _sessionStatus.value = "Empty — ignored"
            return
        }
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
        val payload = message.text.trim()
        if (payload.isEmpty() || message.status != MessageStatus.Ready) {
            if (payload.isEmpty()) _sessionStatus.value = "Empty — ignored"
            return
        }
        // Brand Studio / share sheet still via SendHelper; Grok auto-pastes through MyPC bridge.
        when (destination) {
            Destination.BrandStudio -> sendHelper.send(payload, destination, settings)
            Destination.GrokBot -> Unit
            Destination.Both -> sendHelper.send(payload, Destination.BrandStudio, settings)
        }
        _messages.update { list ->
            list.map {
                if (it.id == messageId) it.copy(sentTo = it.sentTo + destination) else it
            }
        }
        maybePostIdea(payload, destination)
        if (destination == Destination.GrokBot || destination == Destination.Both) {
            scope.launch {
                val base = settings.bridgeBaseUrl
                runCatching { bridgeClient.sendToGrok(base, payload) }
                    .onSuccess { raw ->
                        if (raw.contains("skipped")) {
                            _sessionStatus.value = "Empty — ignored"
                            return@onSuccess
                        }
                        push(
                            ChatMessage(
                                role = MessageRole.Status,
                                text = "Pasted into Grok Bot on MyPC",
                            ),
                        )
                    }
                    .onFailure { error ->
                        val detail = error.message ?: error.javaClass.simpleName
                        if (detail.contains("empty", ignoreCase = true) || detail.contains("skipped", ignoreCase = true)) {
                            _sessionStatus.value = "Empty — ignored"
                            return@onFailure
                        }
                        push(
                            ChatMessage(
                                role = MessageRole.Status,
                                text = "Bridge paste failed ($detail). Falling back to phone share.",
                            ),
                        )
                        sendHelper.send(payload, Destination.GrokBot, settings)
                    }
            }
        }
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
                    remove(RECORDING_ID)
                    if (text.isBlank()) {
                        push(
                            ChatMessage(
                                role = MessageRole.Status,
                                text = "Heard silence. Hold a little longer.",
                                status = MessageStatus.Failed,
                            ),
                        )
                    } else {
                        onHeard(text, fromSession = false)
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
            _sessionPaused.value = false
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


    fun markSessionStandby(seed: Boolean = false) {
        _phase.value = SessionPhase.Standby
        _sessionStatus.value = "Standby. Say Salt wake up."
        if (seed) {
            push(
                ChatMessage(
                    role = MessageRole.Status,
                    text = "Salt is on standby. Say Salt wake up, then your instructions. Say Salt send to Grok or Brand Studio to pick the app.",
                ),
            )
        }
    }

    fun setSessionPaused(paused: Boolean) {
        _sessionPaused.value = paused
    }

    fun onHeard(raw: String, fromSession: Boolean) {
        val text = raw.trim()
        if (text.isEmpty()) return
        when (val kind = Phrases.classify(text)) {
            Phrases.Kind.Noise -> return
            Phrases.Kind.Wake -> {
                _phase.value = SessionPhase.Listening
                _sessionStatus.value = "Listening. Speak a task. Say Salt go on standby when done."
                push(ChatMessage(role = MessageRole.Status, text = "Salt wake up — listening."))
                val rest = Phrases.remainderAfterWake(text)
                if (rest.isNotEmpty()) {
                    acceptTask(rest, autoSend = true)
                }
            }
            Phrases.Kind.Standby -> {
                _phase.value = SessionPhase.Standby
                _sessionStatus.value = "Standby. Say Salt wake up."
                push(ChatMessage(role = MessageRole.Status, text = "Salt on standby."))
            }
            Phrases.Kind.TargetGrok -> {
                _destination.value = Destination.GrokBot
                _sessionStatus.value = "Target: Grok Bot"
                push(ChatMessage(role = MessageRole.Status, text = "Sending to Grok Bot."))
            }
            Phrases.Kind.TargetBrand -> {
                _destination.value = Destination.BrandStudio
                _sessionStatus.value = "Target: Brand Studio"
                push(ChatMessage(role = MessageRole.Status, text = "Sending to Brand Studio Capture."))
            }
            Phrases.Kind.Clip -> {
                saveClipMark(source = "voice")
            }
            Phrases.Kind.Task -> {
                val armed = _phase.value == SessionPhase.Listening
                if (!armed && fromSession) {
                    // ignore ambient chatter while standby
                    return
                }
                if (!armed && !fromSession) {
                    // hold-to-talk while standby: show transcript, do not auto-send
                    push(
                        ChatMessage(
                            role = MessageRole.User,
                            text = text,
                            status = MessageStatus.Ready,
                        ),
                    )
                    push(
                        ChatMessage(
                            role = MessageRole.Status,
                            text = "Standby — say Salt wake up to auto-send, or tap Send.",
                        ),
                    )
                    return
                }
                acceptTask(text, autoSend = true)
            }
        }
    }

    private fun acceptTask(text: String, autoSend: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _sessionStatus.value = "Empty — ignored"
            return
        }
        val message = ChatMessage(
            role = MessageRole.User,
            text = trimmed,
            status = MessageStatus.Ready,
        )
        push(message)
        if (autoSend) {
            send(message.id, _destination.value)
            _sessionStatus.value = "Sent to ${_destination.value.label}. Still listening."
        }
    }



    /** Save a transcript clip mark via bridge POST /clip (or local fallback message). */
    fun saveClipMark(source: String = "bubble") {
        scope.launch {
            val recent = recentUserTranscript()
            val base = settings.bridgeBaseUrl
            runCatching { bridgeClient.postClip(base, recent, source) }
                .onSuccess { raw ->
                    if (raw.contains("skipped")) {
                        _sessionStatus.value = "Clip skipped: no transcript"
                        push(ChatMessage(role = MessageRole.Status, text = "Clip skipped — nothing to save yet."))
                        return@onSuccess
                    }
                    val name = runCatching {
                        val path = org.json.JSONObject(raw).optString("path")
                        path.substringAfterLast('\\').substringAfterLast('/')
                    }.getOrNull().orEmpty()
                    val label = if (name.isNotBlank()) "Clip saved: $name" else "Clip saved"
                    _sessionStatus.value = label
                    push(ChatMessage(role = MessageRole.Status, text = label))
                }
                .onFailure { error ->
                    val detail = error.message ?: error.javaClass.simpleName
                    // Local phone fallback: write under app files dir
                    val local = runCatching { writeLocalClip(recent, source) }.getOrNull()
                    if (local != null) {
                        _sessionStatus.value = "Clip saved (phone): ${local.name}"
                        push(
                            ChatMessage(
                                role = MessageRole.Status,
                                text = "Bridge unreachable ($detail). Saved on phone: ${local.name}",
                            ),
                        )
                    } else {
                        _sessionStatus.value = "Clip failed"
                        push(
                            ChatMessage(
                                role = MessageRole.Status,
                                text = "Clip failed: $detail",
                                status = MessageStatus.Failed,
                            ),
                        )
                    }
                }
        }
    }

    private fun recentUserTranscript(maxLines: Int = 40): String {
        return _messages.value
            .asReversed()
            .asSequence()
            .filter { it.role == MessageRole.User && it.text.isNotBlank() }
            .take(maxLines)
            .toList()
            .asReversed()
            .joinToString("\n") { it.text.trim() }
            .trim()
    }

    private fun writeLocalClip(text: String, source: String): java.io.File? {
        val body = text.trim()
        if (body.isEmpty()) return null
        val dir = java.io.File(app.filesDir, "clips").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val file = java.io.File(dir, "clip-$stamp.txt")
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            .format(java.util.Date())
        file.writeText("time: $iso\nsource: $source\n\n$body\n")
        return file
    }

    companion object {
        private const val RECORDING_ID = "recording-in-flight"
    }
}
