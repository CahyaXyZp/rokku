package eu.kanade.tachiyomi.data.connections.discord

import android.os.Handler
import android.os.Looper
import android.util.Base64
import co.touchlab.kermit.Logger
import com.discord.socialsdk.AuthenticationClientCallback
import com.discord.socialsdk.NativeCalls
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** A Discord user profile, fetched from the API right after a successful OAuth authorization. */
data class DiscordUser(
    val id: String,
    val username: String,
    val name: String,
    val avatarUrl: String?,
)

/**
 * Drives Discord Rich Presence via the official Social SDK, over JNI to [discord_bridge.cpp].
 *
 * Single account at a time for now, matching [DiscordRPCService]'s current scope - this
 * authenticates/connects for whichever one account is active. Multi-account (one native
 * connection per account) is a planned follow-up; the SDK itself supports it; this class and the
 * bridge don't yet.
 *
 * Flow: [init] loads the native lib and starts the callback pump once -> [authorize] opens the
 * Discord app for OAuth PKCE consent and exchanges the resulting code for an access token ->
 * [reconnectWithToken] (re)connects the native client with an already-known token, e.g. when
 * switching back to a previously authorized account.
 */
object DiscordRpcManager {
    private const val TAG = "DiscordRpcManager"
    private val APP_ID = BuildConfig.DISCORD_APP_ID
    private const val SCOPES = "identify openid sdk.social_layer_presence"
    private val REDIRECT_URI = "discord-$APP_ID:///authorize/callback"
    private const val AUTH_URL = "https://discord.com/oauth2/authorize"
    private const val TOKEN_URL = "https://discord.com/api/v10/oauth2/token"
    private const val USER_URL = "https://discord.com/api/v10/users/@me"

    enum class Status { Disconnected, Authorizing, Connected }

    enum class OnlineStatus(val value: Int) {
        Online(0),
        Idle(3),
        DoNotDisturb(4),
    }

    private val initialized = AtomicBoolean(false)

    @Volatile
    private var readyInternal = false

    private var callbackJob: Job? = null

    private val _connectionStatus = MutableStateFlow(Status.Disconnected)
    val connectionStatus: StateFlow<Status> = _connectionStatus

    fun isInitialized(): Boolean = initialized.get()
    fun isReady(): Boolean = readyInternal

    /** Called from discord_bridge.cpp whenever the native client's connection status changes. */
    @JvmStatic
    private fun onNativeStatusChanged(statusCode: Int, ready: Boolean, authorized: Boolean) {
        readyInternal = ready
        _connectionStatus.value = when {
            ready && authorized -> Status.Connected
            !ready && !authorized -> Status.Disconnected
            else -> Status.Authorizing
        }
        Logger.d(TAG) { "onNativeStatusChanged: statusCode=$statusCode -> ${_connectionStatus.value}" }
    }

    private external fun nativeInit(appId: Long): Boolean
    private external fun nativeIsAuthorized(): Boolean
    private external fun nativeIsReady(): Boolean
    private external fun nativeSetTokenAndConnect(token: String)
    private external fun nativeConnect()
    private external fun nativeSetActivity(
        activityType: Int,
        name: String?,
        state: String?,
        details: String?,
        startSecs: Long,
        endSecs: Long,
        largeImage: String?,
        largeText: String?,
        smallImage: String?,
        smallText: String?,
        button1Label: String?,
        button1Url: String?,
        button2Label: String?,
        button2Url: String?,
    )
    private external fun nativeSetOnlineStatus(statusType: Int)
    private external fun nativeClear()
    private external fun nativeRunCallbacks()
    private external fun nativeDisconnect()
    private external fun nativeDestroy()

    /** Loads the native library and starts the callback pump. Safe to call more than once. */
    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        try {
            System.loadLibrary("rokku_discord")
        } catch (e: UnsatisfiedLinkError) {
            Logger.e(TAG, e) { "Failed to load librokku_discord" }
            initialized.set(false)
            return
        }
        if (!nativeInit(APP_ID)) {
            Logger.w(TAG) { "nativeInit returned false" }
            initialized.set(false)
            return
        }

        callbackJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    nativeRunCallbacks()
                } catch (e: Exception) {
                    Logger.w(TAG, e) { "nativeRunCallbacks threw" }
                }
                delay(1_000)
            }
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Opens the Discord app for OAuth PKCE consent and exchanges the resulting code for an
     * access token. [onComplete] runs on the main thread with the access token on success, or
     * null on failure/cancellation - the caller is responsible for fetching the profile (see
     * [fetchCurrentUser]) and persisting the resulting account.
     */
    fun authorize(onComplete: (String?) -> Unit) {
        if (!initialized.get()) {
            onComplete(null)
            return
        }

        _connectionStatus.value = Status.Authorizing
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)

        val oauthUrl = "$AUTH_URL" +
            "?client_id=$APP_ID" +
            "&response_type=code" +
            "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=${URLEncoder.encode(SCOPES, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge"

        var callbackFired = false
        val callback = object : AuthenticationClientCallback(0) {
            override fun onAuthorizationComplete(error: String?, authCode: String?, state: String?) {
                if (callbackFired) return
                callbackFired = true

                if (!error.isNullOrEmpty() || authCode.isNullOrEmpty()) {
                    Logger.w(TAG) { "Authorization failed or cancelled: $error" }
                    _connectionStatus.value = Status.Disconnected
                    Handler(Looper.getMainLooper()).post { onComplete(null) }
                    return
                }
                exchangeCodeForToken(authCode, verifier, onComplete)
            }
        }

        try {
            NativeCalls.authorize(oauthUrl, callback)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "NativeCalls.authorize threw" }
            _connectionStatus.value = Status.Disconnected
            onComplete(null)
        }
    }

    private fun exchangeCodeForToken(
        authCode: String,
        codeVerifier: String,
        onComplete: (String?) -> Unit,
    ) {
        Thread {
            val accessToken = try {
                val body = "client_id=$APP_ID" +
                    "&grant_type=authorization_code" +
                    "&code=${URLEncoder.encode(authCode, "UTF-8")}" +
                    "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                    "&code_verifier=$codeVerifier"

                val conn = URL(TOKEN_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                OutputStreamWriter(conn.outputStream).use { it.write(body) }

                val code = conn.responseCode
                val responseBody = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: ""
                }
                conn.disconnect()

                if (code !in 200..299) {
                    Logger.w(TAG) { "Token exchange failed: HTTP $code" }
                    null
                } else {
                    JSONObject(responseBody).optString("access_token").ifEmpty { null }
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Exception during token exchange" }
                null
            }

            if (accessToken == null) {
                _connectionStatus.value = Status.Disconnected
                Handler(Looper.getMainLooper()).post { onComplete(null) }
                return@Thread
            }

            nativeSetTokenAndConnect(accessToken)
            Handler(Looper.getMainLooper()).post {
                nativeConnect()
                onComplete(accessToken)
            }
        }.apply { name = "DiscordTokenExchange" }.start()
    }

    /**
     * Fetches the Discord user profile for [token] (an OAuth access token, sent as a Bearer
     * token - not the same header format [Discord.fetchProfile] uses for Token Login).
     * Performs blocking network I/O; call off the main thread.
     */
    fun fetchCurrentUser(token: String): DiscordUser? {
        return try {
            val conn = URL(USER_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()
            if (code !in 200..299) {
                Logger.w(TAG) { "fetchCurrentUser: HTTP $code" }
                return null
            }

            val json = JSONObject(responseBody)
            val id = json.getString("id")
            val username = json.getString("username")
            val name = json.optString("global_name").ifEmpty { username }
            val avatarHash = json.optString("avatar")
            val avatarUrl = if (avatarHash.isNotEmpty()) {
                "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
            } else {
                null
            }
            DiscordUser(id, username, name, avatarUrl)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchCurrentUser threw" }
            null
        }
    }

    /**
     * (Re)connects the native client using an already-known access token - used when switching
     * back to (or starting the RPC service for) an SDK account that was authorized before.
     */
    fun reconnectWithToken(token: String) {
        if (!initialized.get()) return
        _connectionStatus.value = Status.Authorizing
        nativeSetTokenAndConnect(token)
        Handler(Looper.getMainLooper()).post { nativeConnect() }
    }

    fun setActivity(activity: DiscordNativeActivity) {
        if (!readyInternal) return
        nativeSetActivity(
            activity.activityType,
            activity.name, activity.state, activity.details,
            activity.startTimestamp, activity.endTimestamp ?: 0L,
            activity.largeImage, activity.largeText,
            activity.smallImage, activity.smallText,
            activity.button1Label, activity.button1Url,
            activity.button2Label, activity.button2Url,
        )
    }

    fun setOnlineStatus(status: OnlineStatus) {
        if (!readyInternal) return
        nativeSetOnlineStatus(status.value)
    }

    fun clear() {
        if (!readyInternal) return
        nativeClear()
    }

    /** Drops the native connection. Safe to call [reconnectWithToken] again afterwards. */
    fun disconnect() {
        if (!initialized.get()) return
        _connectionStatus.value = Status.Disconnected
        readyInternal = false
        nativeDisconnect()
    }

    /** Tears down the native client entirely. Call [init] again before reusing this manager. */
    fun destroy() {
        if (!initialized.compareAndSet(true, false)) return
        readyInternal = false
        _connectionStatus.value = Status.Disconnected
        callbackJob?.cancel()
        callbackJob = null
        nativeDestroy()
    }
}
