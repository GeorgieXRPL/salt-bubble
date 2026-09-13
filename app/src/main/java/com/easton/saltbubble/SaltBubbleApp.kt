package com.easton.saltbubble

import android.app.Application
import com.easton.saltbubble.data.SettingsStore
import com.easton.saltbubble.net.BrandStudioClient
import com.easton.saltbubble.net.BridgeClient
import com.easton.saltbubble.net.TranscriptionClient
import com.easton.saltbubble.send.SendHelper

class SaltBubbleApp : Application() {
    lateinit var settingsStore: SettingsStore
        private set

    lateinit var transcriptionClient: TranscriptionClient
        private set

    lateinit var brandStudioClient: BrandStudioClient
        private set

    lateinit var bridgeClient: BridgeClient
        private set

    lateinit var sendHelper: SendHelper
        private set

    override fun onCreate() {
        super.onCreate()
        settingsStore = SettingsStore(this)
        val http = TranscriptionClient.defaultClient()
        transcriptionClient = TranscriptionClient(http)
        brandStudioClient = BrandStudioClient(http)
        bridgeClient = BridgeClient(http)
        sendHelper = SendHelper(this)
    }
}

val Application.saltBubble: SaltBubbleApp
    get() = this as SaltBubbleApp
