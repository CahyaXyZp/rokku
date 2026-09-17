// Adapted from Komikku (originally from KizzyRPC/saikou-app) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

/**
 * Wraps a [DiscordWebSocket] connection and tracks the currently active Rich Presence.
 * @param token Discord account token used to authenticate the Gateway connection.
 */
class DiscordRPC(token: String) {
    private val discordWebSocket: DiscordWebSocket = DiscordWebSocketImpl(token)

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
