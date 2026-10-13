package com.charvis.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("charvis_settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) {
            // Pasted text sometimes includes a link or extra words; keep only the AIza... key.
            val clean = KEY_REGEX.find(value)?.value ?: value.trim()
            prefs.edit().putString(KEY_API, clean).apply()
        }

    companion object {
        private const val KEY_API = "gemini_api_key"
        private val KEY_REGEX = Regex("AIza[0-9A-Za-z_\\-]{35}")
    }
}
