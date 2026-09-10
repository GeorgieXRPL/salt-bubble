package com.easton.saltbubble.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.easton.saltbubble.MainActivity
import com.easton.saltbubble.R
import com.easton.saltbubble.saltBubble
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BubbleService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    private lateinit var chat: ChatController
    private lateinit var overlay: OverlayController
    private var overlayReady = false

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        createChannel()
        enterForeground(recording = false)
        chat = ChatController(application, lifecycleScope, application.saltBubble.settingsStore)
        overlay = OverlayController(this, chat)
        overlay.attach()
        overlayReady = true
        lifecycleScope.launch {
            chat.recording.collectLatest { recording ->
                enterForeground(recording)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopOverlay()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> if (overlayReady) overlay.toggle()
            ACTION_EXPAND -> if (overlayReady) overlay.expand()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (overlayReady) overlay.detach()
        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun stopOverlay() {
        overlay.detach()
        overlayReady = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground(recording: Boolean) {
        val notification = buildNotification(recording)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                if (recording) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        } else {
            0
        }
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(recording: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (recording) {
            "Listening — release the mic to transcribe"
        } else {
            getString(R.string.notification_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bubble)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.easton.saltbubble.START"
        const val ACTION_STOP = "com.easton.saltbubble.STOP"
        const val ACTION_TOGGLE = "com.easton.saltbubble.TOGGLE"
        const val ACTION_EXPAND = "com.easton.saltbubble.EXPAND"
        private const val CHANNEL_ID = "salt_bubble_overlay"
        private const val NOTIFICATION_ID = 8791

        fun start(context: Context) {
            val intent = Intent(context, BubbleService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BubbleService::class.java).setAction(ACTION_STOP)
            context.startForegroundService(intent)
        }

        fun isLikelyRunning(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager.activeNotifications.any { it.id == NOTIFICATION_ID }
        }
    }
}
