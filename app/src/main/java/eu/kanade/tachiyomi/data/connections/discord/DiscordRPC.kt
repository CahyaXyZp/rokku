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

    private val assetCache = mutableMapOf<String, String?>()

    /**
     * Resolves [imageUrl] into a Discord "mp:" asset path usable as a large/small image,
     * caching the result per URL for the lifetime of this RPC session (e.g. the app icon
     * never changes mid-session, and a manga's cover URL is re-requested every chapter
     * update). Returns null if the exchange fails.
     */
    suspend fun resolveAsset(imageUrl: String): String? {
        assetCache[imageUrl]?.let { return it }
        return externalAsset.getDiscordUri(imageUrl).also { assetCache[imageUrl] = it }
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
