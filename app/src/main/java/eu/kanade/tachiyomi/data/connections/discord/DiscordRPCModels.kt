// Adapted from Komikku (originally from Animiru/KizzyRPC/saikou-app) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Constant for application id, internal only - not user configurable
internal const val RICH_PRESENCE_APPLICATION_ID = "1547043658719698984"

// Key of the Rich Presence asset used for the "show app icon" large image. Rich Presence assets
// are uploaded separately in the Discord Developer Portal for this application id - this key
// only has an effect once an asset with this exact name exists there.
internal const val RICH_PRESENCE_APP_ICON_ASSET_KEY = "app_icon"

@Serializable
data class Activity(
    @SerialName("application_id")
    val applicationId: String? = RICH_PRESENCE_APPLICATION_ID,
    val name: String? = null,
    val details: String? = null,
    val state: String? = null,
    val type: Int? = null,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
) {
    @Serializable
    data class Assets(
        @SerialName("large_image")
        val largeImage: String? = null,
        @SerialName("large_text")
        val largeText: String? = null,
        @SerialName("small_image")
        val smallImage: String? = null,
        @SerialName("small_text")
        val smallText: String? = null,
    )

    @Serializable
    data class Timestamps(
        val start: Long? = null,
        val end: Long? = null,
    )
}

@Serializable
data class Presence(
    val status: String? = null,
    val afk: Boolean = true,
    val activities: List<Activity> = listOf(),
    val since: Long? = null,
) {
    @Serializable
    data class Response(
        val op: Long,
        val d: Presence,
    )
}

@Serializable
data class Identity(
    val token: String,
    val properties: Properties,
    val compress: Boolean,
    val intents: Long,
) {

    @Serializable
    data class Response(
        val op: Long,
        val d: Identity,
    )

    @Serializable
    data class Properties(
        @SerialName("\$os")
        val os: String,

        @SerialName("\$browser")
        val browser: String,

        @SerialName("\$device")
        val device: String,
    )
}

@Serializable
data class Res(
    val t: String?,
    val s: Int?,
    val op: Int,
    val d: JsonElement,
)

enum class ActivityType(val value: Int) {
    /** Playing a game. */
    PLAYING(0),

    /** Streaming a game. */
    STREAMING(1),

    /** Listening to music. */
    LISTENING(2),

    /** Watching a video. */
    WATCHING(3),

    /** Competing in a game. */
    COMPETING(5),

    /** Custom activity type, not defined by Discord. */
    CUSTOM(4),
}

@Suppress("MagicNumber")
enum class OpCode(val value: Int) {
    /** An event was dispatched. */
    DISPATCH(0),

    /** Fired periodically by the client to keep the connection alive. */
    HEARTBEAT(1),

    /** Starts a new session during the initial handshake. */
    IDENTIFY(2),

    /** Update the client's presence. */
    PRESENCE_UPDATE(3),

    /** Joins/leaves or moves between voice channels. */
    VOICE_STATE(4),

    /** Resume a previous session that was disconnected. */
    RESUME(6),

    /** You should attempt to reconnect and resume immediately. */
    RECONNECT(7),

    /** Request information about offline guild members in a large guild. */
    REQUEST_GUILD_MEMBERS(8),

    /** The session has been invalidated. You should reconnect and identify/resume accordingly */
    INVALID_SESSION(9),

    /** Sent immediately after connecting, contains the heartbeat_interval to use. */
    HELLO(10),

    /** Sent in response to receiving a heartbeat to acknowledge that it has been received. */
    HEARTBEAT_ACK(11),

    /** For future use or unknown opcodes. */
    UNKNOWN(-1),
}
