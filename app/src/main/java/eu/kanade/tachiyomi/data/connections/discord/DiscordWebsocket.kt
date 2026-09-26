// Adapted from Komikku (originally from KizzyRPC/saikou-app) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.math.min
import kotlin.math.pow

sealed interface DiscordWebSocket : CoroutineScope {
    suspend fun sendActivity(presence: Presence)
    fun close()
}

/**
 * Maintains a WebSocket connection to the Discord Gateway for a single account token and
 * pushes Rich Presence updates over it. One instance is meant to live per active account,
 * created/closed by whatever manages the foreground RPC service.
 *
 * Reconnects automatically on failure with exponential backoff (see [Listener.onFailure]) -
 * intentional closes (see [close]) never trigger a reconnect.
 */
open class DiscordWebSocketImpl(
    private val token: String,
) : DiscordWebSocket {

    private val json = Json {
        encodeDefaults = true
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
    }

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val request = Request.Builder()
        .url("wss://gateway.discord.gg/?encoding=json&v=10")
        .build()

    private var webSocket: WebSocket? = client.newWebSocket(request, Listener())

    private var connected = false

    private val connectionState = MutableStateFlow(false)

    // Reset to 0 once the gateway handshake actually succeeds (READY) - see onMessage below -
    // so a long-lived, healthy connection doesn't inherit backoff from an earlier rough patch.
    private var reconnectAttempt = 0

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.IO

    private fun sendIdentify() {
        val response = Identity.Response(
            op = OpCode.IDENTIFY.value.toLong(),
            d = Identity(
                token = token,
                properties = Identity.Properties(
                    os = "windows",
                    browser = "Chrome",
                    device = "disco",
                ),
                compress = false,
                intents = 0,
            ),
        )
        webSocket?.send(json.encodeToString(response))
    }

    override fun close() {
        Logger.i { "Closing Discord WebSocket, sending offline status" }
        webSocket?.send(
            json.encodeToString(
                Presence.Response(
                    op = OpCode.DISPATCH.value.toLong(),
                    d = Presence(status = "offline"),
                ),
            ),
        )
        webSocket?.close(NORMAL_CLOSURE_CODE, CLOSE_REASON_INTERRUPT)
        connected = false
        connectionState.value = false
    }

    override suspend fun sendActivity(presence: Presence) {
        try {
            // Wait for the gateway handshake to finish so a caller doesn't hang forever if
            // there's no network or the token gets rejected.
            withTimeout(CONNECTION_TIMEOUT_MS) {
                connectionState.filter { it }.first()
            }
            val response = Presence.Response(
                op = OpCode.PRESENCE_UPDATE.value.toLong(),
                d = presence,
            )
            val sent = webSocket?.send(json.encodeToString(response))
            if (sent != true) Logger.e { "Failed to send ${OpCode.PRESENCE_UPDATE}" }
        } catch (e: TimeoutCancellationException) {
            Logger.e(e) { "Timeout waiting for Discord connection, skipping activity update" }
        } catch (e: Exception) {
            Logger.e(e) { "Error sending Discord activity" }
        }
    }

    inner class Listener : WebSocketListener() {
        private var seq: Int? = null
        private var heartbeatInterval: Long? = null

        var scope = CoroutineScope(coroutineContext)

        private fun sendHeartBeat(sendIdentify: Boolean) {
            scope.cancel()
            scope = CoroutineScope(coroutineContext)
            scope.launch {
                delay(heartbeatInterval!!)
                webSocket?.send("{\"op\":${OpCode.HEARTBEAT.value}, \"d\":$seq}")
            }
            if (sendIdentify) sendIdentify()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val map = json.decodeFromString<Res>(text)
            seq = map.s

            when (map.op) {
                OpCode.HELLO.value -> {
                    heartbeatInterval = map.d.jsonObject["heartbeat_interval"]!!.jsonPrimitive.long
                    sendHeartBeat(true)
                }

                OpCode.DISPATCH.value -> if (map.t == "READY") {
                    connected = true
                    connectionState.value = true
                    reconnectAttempt = 0
                }

                OpCode.HEARTBEAT.value -> {
                    if (scope.isActive) scope.cancel()
                    webSocket.send("{\"op\":${OpCode.HEARTBEAT.value}, \"d\":$seq}")
                }

                OpCode.HEARTBEAT_ACK.value -> sendHeartBeat(false)

                OpCode.RECONNECT.value -> webSocket.close(RECONNECT_CLOSE_CODE, "Reconnect")

                OpCode.INVALID_SESSION.value -> sendHeartBeat(true)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.i { "Discord gateway closed: $code $reason" }
            if (code == NORMAL_CLOSURE_CODE) {
                scope.cancel()
            }
        }

        // Backs off exponentially (1s, 2s, 4s, ... capped at RECONNECT_MAX_DELAY_MS) instead of
        // reconnecting instantly on every failure - a dead network or a bad token would
        // otherwise have this hammering new WebSocket attempts in a tight loop.
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Logger.e(t) { "Discord WebSocket failure" }
            if (t.message == CLOSE_REASON_INTERRUPT) return

            val delayMs = min(
                RECONNECT_BASE_DELAY_MS * 2.0.pow(reconnectAttempt).toLong(),
                RECONNECT_MAX_DELAY_MS,
            )
            reconnectAttempt++
            scope.launch {
                delay(delayMs)
                this@DiscordWebSocketImpl.webSocket = client.newWebSocket(request, Listener())
            }
        }
    }
}

private const val CONNECTION_TIMEOUT_MS = 30_000L
private const val NORMAL_CLOSURE_CODE = 4000
private const val RECONNECT_CLOSE_CODE = 400
private const val CLOSE_REASON_INTERRUPT = "Interrupt"
private const val RECONNECT_BASE_DELAY_MS = 1_000L
private const val RECONNECT_MAX_DELAY_MS = 60_000L
