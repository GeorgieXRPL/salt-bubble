package com.easton.saltbubble.overlay

import android.app.Application
import android.util.Log
import com.easton.saltbubble.audio.WavRecorder
import com.easton.saltbubble.saltBubble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Continuous mic chunks → PC Whisper bridge → Salt Local-style wake/session.
 * Pauses while the user is hold-to-talk recording or a manual transcribe is in flight.
 */
class SessionLoop(
    private val app: Application,
    private val scope: CoroutineScope,
    private val chat: ChatController,
    private val chunkMs: Long = 2_600L,
) {
    private val recorder = WavRecorder(app)
    private val client = app.saltBubble.transcriptionClient
    private val settingsStore = app.saltBubble.settingsStore
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        chat.markSessionStandby(seed = true)
        job = scope.launch {
            Log.i(TAG, "session loop started")
            while (isActive) {
                if (chat.recording.value || chat.busy.value || chat.sessionPaused.value) {
                    delay(250)
                    continue
                }
                val file = runCatching {
                    val started = recorder.start()
                    delay(chunkMs)
                    if (chat.recording.value) {
                        recorder.cancel()
                        return@runCatching null
                    }
                    recorder.stop() ?: started
                }.getOrElse { err ->
                    Log.w(TAG, "chunk record failed: ${err.message}")
                    runCatching { recorder.cancel() }
                    null
                }
                if (file == null || file.length() < 800) {
                    file?.delete()
                    delay(120)
                    continue
                }
                val base = runCatching { settingsStore.settings.first().bridgeBaseUrl }.getOrNull()
                if (base.isNullOrBlank()) {
                    file.delete()
                    delay(400)
                    continue
                }
                val text = runCatching { client.transcribe(base, file) }
                    .onFailure { Log.w(TAG, "transcribe failed: ${it.message}") }
                    .getOrNull()
                    .orEmpty()
                    .trim()
                file.delete()
                if (text.isNotEmpty()) {
                    chat.onHeard(text, fromSession = true)
                }
                delay(80)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching { recorder.cancel() }
        Log.i(TAG, "session loop stopped")
    }

    companion object {
        private const val TAG = "SaltSession"
    }
}
