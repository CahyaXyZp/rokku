package eu.kanade.tachiyomi.data.connections.discord

/**
 * A Rich Presence payload for [DiscordRpcManager.setActivity], mirroring the fields
 * discord_bridge.cpp's SetActivity forwards to discordpp::Activity/ActivityAssets.
 *
 * Timestamps are epoch seconds (0/null = unset), matching the native side's convention of
 * "0 means no timestamp" rather than Kotlin's null for that field.
 */
data class DiscordNativeActivity(
    val name: String,
    val details: String? = null,
    val state: String? = null,
    val startTimestamp: Long = 0L,
    val endTimestamp: Long? = null,
    val largeImage: String? = null,
    val largeText: String? = null,
    val smallImage: String? = null,
    val smallText: String? = null,
    val button1Label: String? = null,
    val button1Url: String? = null,
    val button2Label: String? = null,
    val button2Url: String? = null,
    // Matches discordpp::ActivityTypes::Watching - the only type Rokku sets today.
    val activityType: Int = TYPE_WATCHING,
) {
    companion object {
        const val TYPE_WATCHING = 3
    }
}
