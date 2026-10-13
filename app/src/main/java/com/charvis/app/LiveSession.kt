package com.charvis.app

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class CharvisState { DISCONNECTED, CONNECTING, LISTENING, SPEAKING }

class LiveSession(
    private val apiKey: String,
    private val onState: (CharvisState) -> Unit,
    private val onError: (String) -> Unit,
    private val onOpenWebsite: (String) -> Unit,
) {
    private val client = OkHttpClient()
    private val audio = AudioStreamer()
    private var socket: WebSocket? = null

    fun connect() {
        if (socket != null) return
        onState(CharvisState.CONNECTING)
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent" +
            "?key=$apiKey"
        socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    fun close() {
        audio.stopAll()
        socket?.close(1000, "bye")
        socket = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(setupMessage())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                handle(JSONObject(text))
            } catch (e: Exception) {
                Log.e(TAG, "bad message", e)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "socket failure", t)
            audio.stopAll()
            socket = null
            onError(t.message ?: "कनेक्शन नहीं हुआ")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            audio.stopAll()
            socket = null
            onState(CharvisState.DISCONNECTED)
        }
    }

    private fun handle(msg: JSONObject) {
        when {
            msg.has("setupComplete") -> {
                onState(CharvisState.LISTENING)
                audio.startMic { chunk -> sendAudio(chunk) }
            }
            msg.has("serverContent") -> handleContent(msg.getJSONObject("serverContent"))
            msg.has("toolCall") -> handleToolCall(msg.getJSONObject("toolCall"))
            msg.has("error") -> onError(msg.getJSONObject("error").optString("message", "API error"))
        }
    }

    private fun handleContent(content: JSONObject) {
        if (content.optBoolean("interrupted")) {
            audio.flushPlayback()
            onState(CharvisState.LISTENING)
            return
        }
        content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
            for (i in 0 until parts.length()) {
                val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                val pcm = Base64.decode(inline.getString("data"), Base64.NO_WRAP)
                onState(CharvisState.SPEAKING)
                audio.play(pcm)
            }
        }
        if (content.optBoolean("turnComplete")) onState(CharvisState.LISTENING)
    }

    private fun handleToolCall(toolCall: JSONObject) {
        val calls = toolCall.getJSONArray("functionCalls")
        val responses = JSONArray()
        for (i in 0 until calls.length()) {
            val call = calls.getJSONObject(i)
            val name = call.getString("name")
            val args = call.optJSONObject("args") ?: JSONObject()
            val result = when (name) {
                "openWebsite" -> {
                    val url = normalizeUrl(args.optString("url"))
                    onOpenWebsite(url)
                    JSONObject().put("result", "opened $url")
                }
                else -> JSONObject().put("error", "unknown tool: $name")
            }
            responses.put(
                JSONObject()
                    .put("id", call.getString("id"))
                    .put("name", name)
                    .put("response", result)
            )
        }
        socket?.send(
            JSONObject()
                .put("toolResponse", JSONObject().put("functionResponses", responses))
                .toString()
        )
    }

    private fun sendAudio(pcm: ByteArray) {
        val msg = JSONObject().put(
            "realtimeInput",
            JSONObject().put(
                "audio",
                JSONObject()
                    .put("mimeType", "audio/pcm;rate=${AudioStreamer.IN_RATE}")
                    .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
            )
        )
        socket?.send(msg.toString())
    }

    private fun setupMessage(): String {
        val openWebsite = JSONObject()
            .put("name", "openWebsite")
            .put("description", "Opens a website in the phone browser. Use only when the user asks to open a site.")
            .put(
                "parameters",
                JSONObject()
                    .put("type", "OBJECT")
                    .put(
                        "properties",
                        JSONObject().put(
                            "url",
                            JSONObject()
                                .put("type", "STRING")
                                .put("description", "Full URL or domain, e.g. youtube.com")
                        )
                    )
                    .put("required", JSONArray().put("url"))
            )

        val voice = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Charon"))
        )

        val setup = JSONObject()
            .put("model", "models/$GEMINI_MODEL")
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", voice)
            )
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", CHARVIS_PROMPT)))
            )
            .put(
                "tools",
                JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(openWebsite)))
            )

        return JSONObject().put("setup", setup).toString()
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    companion object {
        private const val TAG = "CharvisLive"
    }
}
