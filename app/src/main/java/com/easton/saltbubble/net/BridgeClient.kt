package com.easton.saltbubble.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BridgeClient(
    private val http: OkHttpClient = defaultClient(),
) {
    /**
     * Ask Salt Remote Bridge on MyPC to paste into the desktop Grok Bot window
     * (same handoff Salt Local uses).
     */
    suspend fun pair(baseUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$root/pair")
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("X-Salt-Token", token.trim()) }
            .post("{}".toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(TranscriptionClient.httpErrorMessage(response.code, raw))
            }
            raw.ifBlank { """{"ok":true}""" }
        }
    }

    suspend fun sendToGrok(baseUrl: String, text: String, token: String = ""): String = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        // Never POST blank/whitespace-only payloads to the bridge.
        if (trimmed.isEmpty()) throw IOException("empty text")
        val root = baseUrl.trimEnd('/')
        val payload = JSONObject().put("text", trimmed).toString()
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$root/send-to-grok")
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("X-Salt-Token", token.trim()) }
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val status = runCatching { JSONObject(raw).optString("status") }.getOrNull()
            if (status == "skipped") {
                return@withContext raw.ifBlank { """{"status":"skipped","reason":"empty"}""" }
            }
            if (!response.isSuccessful) {
                throw IOException(TranscriptionClient.httpErrorMessage(response.code, raw))
            }
            if (status == "error") {
                val err = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                throw IOException(err.ifBlank { raw })
            }
            raw.ifBlank { """{"status":"ok"}""" }
        }
    }


    /**
     * Ask Salt Remote Bridge on MyPC to write a transcript clip mark
     * (last ~30s buffer on PC if text is blank).
     */
    suspend fun postClip(
        baseUrl: String,
        text: String = "",
        source: String = "bubble",
        token: String = "",
    ): String = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        val payload = JSONObject()
            .put("text", text)
            .put("source", source)
            .toString()
        val body = payload.toRequestBody(JSON)
        val request = Request.Builder()
            .url("$root/clip")
            .header("Accept", "application/json")
            .apply { if (token.isNotBlank()) header("X-Salt-Token", token.trim()) }
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val status = runCatching { JSONObject(raw).optString("status") }.getOrNull()
            if (status == "skipped") {
                return@withContext raw.ifBlank { """{"status":"skipped","reason":"empty"}""" }
            }
            if (!response.isSuccessful) {
                throw IOException(TranscriptionClient.httpErrorMessage(response.code, raw))
            }
            if (status == "error") {
                val err = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                throw IOException(err.ifBlank { raw })
            }
            val errField = runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
            if (errField.isNotBlank() && status != "ok" && status != "skipped") {
                throw IOException(errField)
            }
            raw.ifBlank { """{"status":"ok"}""" }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()
    }
}
