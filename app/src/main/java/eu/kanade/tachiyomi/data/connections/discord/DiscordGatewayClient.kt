package eu.kanade.tachiyomi.data.connections.discord

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.random.Random

private const val TAG = "DiscordGatewayClient"
private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
private const val MAX_BACKOFF_MS = 60_000L

sealed interface GatewayState {
    data object Disconnected : GatewayState
    data object Connecting : GatewayState
    data object Connected : GatewayState
    data class Failed(val reason: String) : GatewayState
}

/**
 * Minimal Discord Gateway client scoped to what Rich Presence needs: identify, heartbeat,
 * presence updates, and reconnect/resume. Not a general-purpose gateway library - no dispatch
 * event handling beyond READY/RESUMED/INVALID_SESSION.
 *
 * One instance = one Discord account's connection. DiscordRpcService owns the lifecycle and
 * is responsible for tearing this down when the reader closes or presence is disabled.
 */
class DiscordGatewayClient(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = false },
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var heartbeatJob: Job? = null
    private var socket: WebSocket? = null

    private var token: String? = null
    private var pendingPresence: PresenceUpdate? = null
    private var sessionId: String? = null
    private var resumeUrl: String? = null
    private var lastSequence: Int? = null
    private var reconnectAttempt = 0
    private var shouldRun = false
    private var lastHeartbeatAcked = true

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Disconnected)
    val state: StateFlow<GatewayState> = _state.asStateFlow()

    /** Connects (or reconnects) with the given token and an initial presence to identify with. */
    fun connect(token: String, initialPresence: PresenceUpdate) {
        this.token = token
        this.pendingPresence = initialPresence
        shouldRun = true
        reconnectAttempt = 0
        openSocket()
    }

    /** Pushes a new presence over an existing connection. No-ops if not currently connected. */
    fun updatePresence(presence: PresenceUpdate) {
        pendingPresence = presence
        if (_state.value == GatewayState.Connected) {
            send(GatewayPayload(op = GatewayOp.PRESENCE_UPDATE, data = json.encodeToJsonElement(presence)))
        }
    }

    /** Closes the connection and stops any reconnect attempts. Safe to call multiple times. */
    fun disconnect() {
        shouldRun = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        socket?.close(1000, "client disconnect")
        socket = null
        sessionId = null
        resumeUrl = null
        lastSequence = null
        _state.value = GatewayState.Disconnected
    }

    private fun openSocket() {
        _state.value = GatewayState.Connecting
        val url = resumeUrl?.let { "$it/?v=10&encoding=json" } ?: GATEWAY_URL
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Discord sends HELLO immediately; nothing to do here but wait for it.
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val payload = runCatching { json.decodeFromString<GatewayPayload>(text) }
                .getOrElse {
                    Log.w(TAG, "Failed to decode gateway frame", it)
                    return
                }
            payload.sequence?.let { lastSequence = it }
            handlePayload(payload)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleDisconnect("closed: $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDisconnect("failure: ${t.message}")
        }
    }

    private fun handlePayload(payload: GatewayPayload) {
        when (payload.op) {
            GatewayOp.HELLO -> {
                val hello = payload.data?.let { json.decodeFromJsonElement(Hello.serializer(), it) }
                    ?: return
                startHeartbeat(hello.heartbeatIntervalMs)
                resumeIfPossible() ?: identify()
            }
            GatewayOp.HEARTBEAT_ACK -> lastHeartbeatAcked = true
            GatewayOp.HEARTBEAT -> sendHeartbeat()
            GatewayOp.RECONNECT -> handleDisconnect("server requested reconnect", resumable = true)
            GatewayOp.INVALID_SESSION -> {
                // A resumable invalid session still has a chance; a non-resumable one needs a
                // fresh identify. Either way, drop the current session id and retry.
                sessionId = null
                scope.launch {
                    delay(Random.nextLong(1000, 5000))
                    identify()
                }
            }
            GatewayOp.DISPATCH -> when (payload.eventName) {
                "READY" -> {
                    val ready = payload.data?.let { json.decodeFromJsonElement(ReadyEvent.serializer(), it) }
                    sessionId = ready?.sessionId
                    resumeUrl = ready?.resumeGatewayUrl
                    reconnectAttempt = 0
                    _state.value = GatewayState.Connected
                }
                "RESUMED" -> {
                    reconnectAttempt = 0
                    _state.value = GatewayState.Connected
                }
            }
        }
    }

    private fun resumeIfPossible(): Unit? {
        val session = sessionId ?: return null
        val seq = lastSequence ?: return null
        val currentToken = token ?: return null
        send(
            GatewayPayload(
                op = GatewayOp.RESUME,
                data = json.encodeToJsonElement(ResumePayload(currentToken, session, seq)),
            ),
        )
        return Unit
    }

    private fun identify() {
        val currentToken = token ?: return
        send(
            GatewayPayload(
                op = GatewayOp.IDENTIFY,
                data = json.encodeToJsonElement(
                    IdentifyPayload(token = currentToken, presence = pendingPresence),
                ),
            ),
        )
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        lastHeartbeatAcked = true
        heartbeatJob = scope.launch {
            // Jitter the first beat per the gateway spec, then beat on a fixed interval.
            delay((intervalMs * Random.nextDouble()).toLong())
            while (isActive) {
                if (!lastHeartbeatAcked) {
                    handleDisconnect("heartbeat not acked", resumable = true)
                    return@launch
                }
                sendHeartbeat()
                delay(intervalMs)
            }
        }
    }

    private fun sendHeartbeat() {
        lastHeartbeatAcked = false
        send(GatewayPayload(op = GatewayOp.HEARTBEAT, data = lastSequence?.let { json.encodeToJsonElement(it) } ?: JsonNull))
    }

    private fun send(payload: GatewayPayload) {
        socket?.send(json.encodeToString(payload))
    }

    private fun handleDisconnect(reason: String, resumable: Boolean = false) {
        heartbeatJob?.cancel()
        socket?.close(1000, null)
        socket = null
        if (!resumable) {
            sessionId = null
            resumeUrl = null
        }
        if (!shouldRun) {
            _state.value = GatewayState.Disconnected
            return
        }
        _state.value = GatewayState.Failed(reason)
        reconnectAttempt++
        val backoff = minOf(1000L * (1 shl minOf(reconnectAttempt, 6)), MAX_BACKOFF_MS)
        scope.launch {
            delay(backoff)
            if (shouldRun) openSocket()
        }
    }
}
