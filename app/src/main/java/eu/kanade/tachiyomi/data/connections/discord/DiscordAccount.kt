package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.Serializable

// Placeholders substituted by DiscordRPCService.setReadingActivity() - {chapter}/{total} in
// activityStateTemplate, {chapter_url} in either button's URL field.
const val TEMPLATE_CHAPTER = "{chapter}"
const val TEMPLATE_TOTAL = "{total}"
const val TEMPLATE_CHAPTER_URL = "{chapter_url}"

const val DEFAULT_ACTIVITY_STATE_TEMPLATE = "Chapter $TEMPLATE_CHAPTER of $TEMPLATE_TOTAL"

/** Discord presence status strings - also mapped to [DiscordRpcManager.OnlineStatus]. */
object DiscordOnlineStatus {
    const val ONLINE = "online"
    const val IDLE = "idle"
    const val DND = "dnd"
}

@Serializable
data class DiscordAccount(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    // Access token from the Social SDK login.
    val token: String,
    // Rich Presence activity name/app-icon badge. Defaults match what used to be the global
    // pref_discord_custom_activity_name/pref_discord_show_app_icon defaults, so an account saved
    // before these fields existed keeps behaving the same until migrated - see
    // Discord.migrateLegacyActivitySettingsIfNeeded().
    val customActivityName: String = "",
    val showAppIcon: Boolean = true,
    // Whether Rich Presence is skipped under Incognito Mode. Defaults match the old global
    // pref_discord_respect_incognito default (also migrated, same as above).
    val respectIncognito: Boolean = true,
    // ActivityType.value (Watching/Playing/Streaming/Listening/Competing).
    val activityType: Int = ActivityType.WATCHING.value,
    val activityStateTemplate: String = DEFAULT_ACTIVITY_STATE_TEMPLATE,
    // One of DiscordOnlineStatus's constants.
    val onlineStatus: String = DiscordOnlineStatus.ONLINE,
    // Off by default - a button is only sent when its own enabled flag is true AND its label/url
    // are non-blank after placeholder resolution (see DiscordRPCService.resolveButtons()). The
    // label/url below default to the two buttons asked for (chapter being read, this project's
    // repo) as a preset ready to go the moment either is switched on, without appearing on their
    // own before that.
    val button1Enabled: Boolean = false,
    val button1Label: String = "Read Chapter",
    val button1Url: String = TEMPLATE_CHAPTER_URL,
    val button2Enabled: Boolean = false,
    val button2Label: String = "Source Code",
    val button2Url: String = "https://github.com/CahyaXyZp/rokku",
)
