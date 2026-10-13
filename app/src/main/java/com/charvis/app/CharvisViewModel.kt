package com.charvis.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class CharvisViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore(app)

    var state by mutableStateOf(CharvisState.DISCONNECTED)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var session: LiveSession? = null
    private val main = Handler(Looper.getMainLooper())

    fun toggle() = if (session == null) connect() else disconnect()

    fun connect() {
        val key = settings.apiKey
        if (key.isBlank()) {
            error = "पहले सेटिंग्स (⚙) में Gemini API key डालो"
            return
        }
        error = null
        session = LiveSession(
            apiKey = key,
            onState = { s -> main.post { state = s } },
            onError = { msg ->
                main.post {
                    error = msg
                    session = null
                    state = CharvisState.DISCONNECTED
                }
            },
            onOpenWebsite = { url -> openUrl(url) },
        ).also { it.connect() }
    }

    fun disconnect() {
        session?.close()
        session = null
        state = CharvisState.DISCONNECTED
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    override fun onCleared() {
        disconnect()
    }
}
