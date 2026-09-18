// Adapted from Komikku (originally from KizzyRPC/saikou-app) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Wraps a [DiscordWebSocket] connection and tracks the currently active Rich Presence.
 * @param token Discord account token used to authenticate the Gateway connection.
 */
class DiscordRPC(private val token: String) {
    private val discordWebSocket: DiscordWebSocket = DiscordWebSocketImpl(token)

    private val externalAsset = RPCExternalAsset(
        applicationId = RICH_PRESENCE_APPLICATION_ID,
        token = token,
        client = OkHttpClient(),
        json = Injekt.get(),
    )

    private var appIconAssetPath: String? = null

    /**
     * Resolves the app icon into a Discord "mp:" asset path usable as a large image, caching
     * the result for the lifetime of this RPC session. Returns null if the exchange fails.
     */
    suspend fun resolveAppIcon(): String? {
        appIconAssetPath?.let { return it }
        return externalAsset.getDiscordUri(RICH_PRESENCE_APP_ICON_URL).also { appIconAssetPath = it }
    }

    /**
     * Closes the Rich Presence connection.
     */
    fun closeRPC() {
        discordWebSocket.close()
    }

    /**
     * Sets the activity for the Rich Presence.
     * @param activity the activity to set.
     * @param since the activity start time.
     */
    suspend fun updateRPC(activity: Activity, since: Long? = null) {
        val presence = Presence(
            activities = listOf(activity),
            afk = false,
            since = since,
            status = "online",
        )
        discordWebSocket.sendActivity(presence)
    }
}
