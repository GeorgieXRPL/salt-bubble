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
import com.easton.saltbubble.data.CaptureUrls
import com.easton.saltbubble.data.Destination
import com.easton.saltbubble.data.GrokTargets

class SendHelper(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun send(text: String, destination: Destination, settings: AppSettings) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        copy(trimmed)
        when (destination) {
            Destination.GrokBot -> sendToGrok(trimmed, settings.grokPackage)
            Destination.BrandStudio -> openCapture(settings, trimmed)
            Destination.Both -> {
                openCapture(settings, trimmed)
                sendToGrok(trimmed, settings.grokPackage)
            }
        }
    }

    fun copyOnly(text: String) {
        copy(text)
        toast("Copied to clipboard")
    }

    fun grokPasteHelper(text: String, settings: AppSettings = AppSettings()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        copy(trimmed)
        sendToGrok(trimmed, settings.grokPackage)
    }

    fun openStudio(settings: AppSettings, text: String = "") {
        openCapture(settings, text)
    }

    private fun sendToGrok(text: String, preferred: String) {
        toast(context.getString(R.string.paste_in_grok))
        for (pkg in GrokTargets.packages(preferred.ifBlank { GrokTargets.BOT })) {
            if (tryActionSend(text, pkg)) return
            if (tryLaunch(pkg)) return
        }
        share(text, "Send to Grok Bot")
    }

    private fun tryActionSend(text: String, pkg: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Salt Bubble")
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (send.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(send)
            true
        }.getOrDefault(false)
    }

    private fun tryLaunch(pkg: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }

    private fun openCapture(settings: AppSettings, text: String) {
        val target = CaptureUrls.target(settings.captureUrl, settings.captureDeepLink, text)
        if (target.isBlank()) return
        openUri(target)
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

    private fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Salt Bubble transcript", text))
    }

    private fun toast(message: String) {
        main.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
