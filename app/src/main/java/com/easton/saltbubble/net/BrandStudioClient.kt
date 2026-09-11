package com.easton.saltbubble.net

import com.easton.saltbubble.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class BrandStudioClient(
    private val http: OkHttpClient = TranscriptionClient.defaultClient(),
) {
    suspend fun addIdea(hubUrl: String, token: String, text: String) = withContext(Dispatchers.IO) {
        val key = token.trim()
        if (key.isEmpty()) return@withContext
        val body = HubUrls.ideaJson(text).toRequestBody(JSON)
        val request = Request.Builder()
            .url(HubUrls.ideasUrl(hubUrl))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code != 200 && response.code != 201) {
                throw IOException(httpError(response.code, raw))
            }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun httpError(code: Int, body: String): String {
            val snippet = body.trim().replace("\n", " ").take(160)
            return if (snippet.isBlank()) "HTTP $code" else "HTTP $code: $snippet"
        }
    }
}

object HubUrls {
    fun looksSafeHub(hubUrl: String): Boolean {
        val root = hubUrl.trim().ifBlank { AppSettings.DEFAULT_HUB_URL }.lowercase()
        return root.startsWith("https://") ||
            root.startsWith("http://localhost") ||
            root.startsWith("http://127.0.0.1")
    }

    fun ideasUrl(hubUrl: String): String {
        val root = hubUrl.trim().ifBlank { AppSettings.DEFAULT_HUB_URL }.trimEnd('/')
        if (!looksSafeHub(root)) {
            throw IllegalArgumentException("Hub URL must be https")
        }
        return "$root/api/ideas"
    }

    fun ideaJson(text: String): String = """{"text":${jsonString(text)}}"""

    internal fun jsonString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (c in value) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }
}
