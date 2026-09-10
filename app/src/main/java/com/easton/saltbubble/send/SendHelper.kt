package com.easton.saltbubble.send

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.easton.saltbubble.R
import com.easton.saltbubble.data.AppSettings
import com.easton.saltbubble.data.Destination

class SendHelper(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun send(text: String, destination: Destination, settings: AppSettings) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        copy(trimmed)
        when (destination) {
            Destination.GrokBot -> {
                toast(context.getString(R.string.paste_in_grok))
                share(trimmed, "Send to Grok Bot")
            }
            Destination.BrandStudio -> {
                toast("Transcript copied — drop it into Capture")
                openStudio(settings)
                share(trimmed, "Send to Brand Studio Capture")
            }
            Destination.Both -> {
                toast("Copied. Paste in Grok Bot, or drop into Capture.")
                openStudio(settings)
                share(trimmed, "Send transcript")
            }
        }
    }

    fun copyOnly(text: String) {
        copy(text)
        toast("Copied to clipboard")
    }

    fun grokPasteHelper(text: String) {
        copy(text)
        toast(context.getString(R.string.paste_in_grok))
        share(text, "Send to Grok Bot")
    }

    fun openStudio(settings: AppSettings) {
        val deep = settings.captureDeepLink.trim()
        val web = settings.captureUrl.trim().ifBlank { AppSettings.DEFAULT_CAPTURE_URL }
        val target = if (deep.isNotEmpty()) deep else web
        openUri(target)
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Salt Bubble transcript", text))
    }

    private fun share(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Salt Bubble")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    private fun openUri(raw: String) {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(view) }
    }

    private fun toast(message: String) {
        main.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
