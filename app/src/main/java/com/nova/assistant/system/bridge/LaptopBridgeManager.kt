package com.nova.assistant.system.bridge

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to NOVA Desktop's WebSocket server (network/server.py on the laptop
 * side) so the phone can act as a remote microphone/input device for the
 * laptop's NOVA instance. This is a distinct mode from the phone's own local
 * ConversationEngine: while connected and "Laptop mód" is active, recognized
 * speech is sent to the laptop instead of being answered locally, and the
 * laptop's reply is what gets shown/spoken on the phone.
 *
 * Never blocks the UI thread: connection and message sends happen on
 * OkHttp's own WebSocket thread; all state changes are published through
 * [state] and [replies] for the UI layer to collect.
 */
class LaptopBridgeManager {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // WebSockets are long-lived; no read timeout
        .build()

    private var webSocket: WebSocket? = null

    private val _state = MutableStateFlow<LaptopLinkState>(LaptopLinkState.Disconnected)
    val state: StateFlow<LaptopLinkState> = _state

    /** Emits NOVA Desktop's replies as they arrive, for the UI to display/speak. */
    val replies = MutableSharedFlow<LaptopReply>(extraBufferCapacity = 4)

    /** Emits status updates the laptop sends while processing (e.g. "thinking"). */
    val remoteStatus = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun connect(ip: String, port: Int, pin: String, deviceName: String) {
        disconnect()
        _state.value = LaptopLinkState.Connecting

        val request = Request.Builder().url("ws://$ip:$port").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject()
                    .put("type", "hello")
                    .put("pin", pin)
                    .put("device_name", deviceName)
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text, deviceName)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = LaptopLinkState.Error(t.message ?: "Ismeretlen kapcsolódási hiba")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = LaptopLinkState.Disconnected
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "A felhasználó bontotta a kapcsolatot.")
        webSocket = null
        _state.value = LaptopLinkState.Disconnected
    }

    fun isConnected(): Boolean = _state.value is LaptopLinkState.Connected

    /** Default, robust mode: send text the phone's own SpeechRecognizer already produced. */
    fun sendTranscript(text: String) {
        val socket = webSocket ?: return
        val message = JSONObject().put("type", "transcript").put("text", text)
        socket.send(message.toString())
    }

    /** Raw-mic-streaming mode: see AudioStreamer. */
    fun sendAudioStart(sampleRate: Int) {
        webSocket?.send(JSONObject().put("type", "audio_start").put("sample_rate", sampleRate).toString())
    }

    fun sendAudioChunk(base64Pcm: String) {
        webSocket?.send(JSONObject().put("type", "audio_chunk").put("data", base64Pcm).toString())
    }

    fun sendAudioEnd() {
        webSocket?.send(JSONObject().put("type", "audio_end").toString())
    }

    private fun handleMessage(text: String, deviceName: String) {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return // malformed frame - ignore rather than crash the bridge
        }

        when (json.optString("type")) {
            "hello_ack" -> {
                _state.value = if (json.optBoolean("accepted", false)) {
                    LaptopLinkState.Connected(deviceName)
                } else {
                    LaptopLinkState.Error(json.optString("reason", "A laptop elutasította a kapcsolatot."))
                }
            }
            "reply" -> {
                replies.tryEmit(LaptopReply(json.optString("text", "")))
            }
            "status" -> {
                remoteStatus.tryEmit(json.optString("state", "idle"))
            }
            // "pong" needs no handling; it's just a liveness check response.
        }
    }
}
