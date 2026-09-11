package com.easton.saltbubble.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TranscriptionClient(
    private val http: OkHttpClient = defaultClient(),
) {
    suspend fun health(baseUrl: String): BridgeHealth = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/health"
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(httpErrorMessage(response.code, raw))
            }
            BridgeHealth(
                ok = raw.contains("\"ok\"") && !raw.contains("\"ok\":false"),
                authRequired = raw.contains("\"auth\":true") || raw.contains("\"auth\": true"),
            )
        }
    }

    suspend fun transcribe(baseUrl: String, wavFile: File, token: String = ""): String = withContext(Dispatchers.IO) {
        val root = baseUrl.trimEnd('/')
        val jsonError = postMultipart(url = "$root/transcribe", file = wavFile, acceptJson = true, token = token)
        if (jsonError is Result.Ok) return@withContext jsonError.text
        if (jsonError is Result.Http && jsonError.code in 200..299) {
            val parsed = TranscriptParser.parse(jsonError.body, jsonError.contentType)
            if (!parsed.isNullOrBlank()) return@withContext parsed
        }
        if (jsonError is Result.Ok || (jsonError is Result.Http && jsonError.code !in listOf(404, 405, 415))) {
            if (jsonError is Result.Http) {
                val parsed = TranscriptParser.parse(jsonError.body, jsonError.contentType)
                if (!parsed.isNullOrBlank()) return@withContext parsed
            }
        }

        val txt = postMultipart(url = "$root/transcribe.txt", file = wavFile, acceptJson = false, token = token)
        when (txt) {
            is Result.Ok -> txt.text
            is Result.Http -> {
                val parsed = TranscriptParser.parse(txt.body, txt.contentType)
                if (!parsed.isNullOrBlank()) parsed
                else throw IOException(httpErrorMessage(txt.code, txt.body))
            }
            is Result.Fail -> {
                when (jsonError) {
                    is Result.Fail -> throw jsonError.error
                    is Result.Http -> throw IOException(httpErrorMessage(jsonError.code, jsonError.body))
                    is Result.Ok -> throw txt.error
                }
            }
        }
    }

    private fun postMultipart(url: String, file: File, acceptJson: Boolean, token: String): Result {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                file.name,
                file.asRequestBody(AUDIO_WAV),
            )
            .addFormDataPart("file", file.name, file.asRequestBody(AUDIO_WAV))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", if (acceptJson) "application/json, text/plain" else "text/plain, application/json")
            .apply { if (token.isNotBlank()) header(TOKEN_HEADER, token.trim()) }
            .post(body)
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val type = response.header("Content-Type")
                if (!response.isSuccessful) {
                    Result.Http(response.code, raw, type)
                } else {
                    val text = TranscriptParser.parse(raw, type)
                    if (text.isNullOrBlank()) Result.Http(response.code, raw, type)
                    else Result.Ok(text)
                }
            }
        } catch (error: Exception) {
            Result.Fail(error)
        }
    }

    private sealed interface Result {
        data class Ok(val text: String) : Result
        data class Http(val code: Int, val body: String, val contentType: String?) : Result
        data class Fail(val error: Exception) : Result
    }

    companion object {
        private val AUDIO_WAV = "audio/wav".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()

        const val TOKEN_HEADER = "X-Salt-Token"

        fun httpErrorMessage(code: Int, body: String): String {
            val snippet = body.trim().replace("\n", " ").take(160)
            return when (code) {
                401 -> "Bridge rejected the token. Set the same Bridge token as saltvoice-local."
                429 -> "Bridge rate-limited the phone. Wait a few seconds and try again."
                else -> if (snippet.isBlank()) {
                    "Bridge returned HTTP $code"
                } else {
                    "Bridge returned HTTP $code: $snippet"
                }
            }
        }
    }
}

data class BridgeHealth(
    val ok: Boolean,
    val authRequired: Boolean,
)

object TranscriptParser {
    fun parse(body: String, contentType: String? = null): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val looksJson = contentType?.contains("json", ignoreCase = true) == true ||
            trimmed.startsWith("{") ||
            trimmed.startsWith("[")
        if (looksJson) {
            extractJsonString(trimmed, "text")?.let { return it }
            extractJsonString(trimmed, "transcription")?.let { return it }
            extractJsonString(trimmed, "transcript")?.let { return it }
        }
        return trimmed
    }

    internal fun extractJsonString(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = regex.find(json) ?: return null
        return unescapeJson(match.groupValues[1]).trim().takeIf { it.isNotEmpty() }
    }

    private fun unescapeJson(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    '/' -> out.append('/')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
        return out.toString()
    }
}
