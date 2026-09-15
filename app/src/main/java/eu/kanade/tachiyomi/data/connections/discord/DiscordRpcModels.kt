package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Raw Discord Gateway frame. `d` is left as a JsonElement since its shape depends on `op`/`t`.
 */
@Serializable
data class GatewayPayload(
    @SerialName("op") val op: Int,
    @SerialName("d") val data: JsonElement? = null,
    @SerialName("s") val sequence: Int? = null,
    @SerialName("t") val eventName: String? = null,
)

object GatewayOp {
    const val DISPATCH = 0
    const val HEARTBEAT = 1
    const val IDENTIFY = 2
    const val PRESENCE_UPDATE = 3
    const val RESUME = 6
    const val RECONNECT = 7
    const val INVALID_SESSION = 9
    const val HELLO = 10
    const val HEARTBEAT_ACK = 11
}

@Serializable
data class Hello(
    @SerialName("heartbeat_interval") val heartbeatIntervalMs: Long,
)

@Serializable
data class IdentifyPayload(
    val token: String,
    val properties: IdentifyProperties = IdentifyProperties(),
    // Only presence updates are needed - keep the identify as lean as possible.
    val intents: Int = 0,
    val presence: PresenceUpdate? = null,
)

@Serializable
data class IdentifyProperties(
    @SerialName("os") val os: String = "android",
    @SerialName("browser") val browser: String = "Rokku",
    @SerialName("device") val device: String = "Rokku",
)

@Serializable
data class ResumePayload(
    val token: String,
    @SerialName("session_id") val sessionId: String,
    val seq: Int,
)

@Serializable
data class ReadyEvent(
    @SerialName("session_id") val sessionId: String,
    @SerialName("resume_gateway_url") val resumeGatewayUrl: String? = null,
)

/**
 * `since` is intentionally omitted - Rokku's presence is momentary (tied to the reader session),
 * not something Discord should compute an elapsed-time display for on its own.
 */
@Serializable
data class PresenceUpdate(
    val since: Long? = null,
    val activities: List<Activity>,
    val status: String = "online",
    val afk: Boolean = false,
)

@Serializable
data class Activity(
    val name: String,
    val type: Int = ActivityType.WATCHING,
    val details: String? = null,
    val state: String? = null,
    val timestamps: ActivityTimestamps? = null,
    val assets: ActivityAssets? = null,
) {
    companion object {
        /**
         * Builds the presence shown while actively reading a chapter.
         * [coverAssetUrl] should already be a Discord-resolvable "mp:" external asset id,
         * or null if cover art is disabled / unavailable - see DiscordRpcService.
         */
        fun forReading(
            mangaTitle: String,
            chapterLabel: String,
            customText: String?,
            startedAt: Long,
            coverAssetUrl: String?,
        ) = Activity(
            name = "Rokku",
            type = ActivityType.WATCHING,
            details = mangaTitle,
            state = customText?.takeIf { it.isNotBlank() } ?: chapterLabel,
            timestamps = ActivityTimestamps(start = startedAt),
            assets = coverAssetUrl?.let { ActivityAssets(largeImage = it, largeText = mangaTitle) },
        )
    }
}

object ActivityType {
    const val PLAYING = 0
    const val WATCHING = 3
}

@Serializable
data class ActivityTimestamps(
    val start: Long? = null,
    val end: Long? = null,
)

@Serializable
data class ActivityAssets(
    @SerialName("large_image") val largeImage: String? = null,
    @SerialName("large_text") val largeText: String? = null,
)
