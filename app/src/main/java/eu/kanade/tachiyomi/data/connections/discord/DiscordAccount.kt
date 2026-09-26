package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.Serializable

/**
 * How an account was authenticated - decides which connection [DiscordRPCService] uses to drive
 * Rich Presence for it: the official Social SDK (native, OAuth) or the existing Token Login
 * Gateway connection.
 */
@Serializable
enum class DiscordAuthMethod {
    SDK,
    TOKEN,
}

// Placeholders substituted by DiscordRPCService.setReadingActivity() - {chapter}/{total} in
// activityStateTemplate, {chapter_url} in either button's URL field.
const val TEMPLATE_CHAPTER = "{chapter}"
const val TEMPLATE_TOTAL = "{total}"
const val TEMPLATE_CHAPTER_URL = "{chapter_url}"

const val DEFAULT_ACTIVITY_STATE_TEMPLATE = "Chapter $TEMPLATE_CHAPTER of $TEMPLATE_TOTAL"

/** Discord Gateway presence status strings - also mapped to [DiscordRpcManager.OnlineStatus]. */
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
    val token: String,
    val isActive: Boolean = false,
    // Defaults to TOKEN so accounts saved before this field existed keep working unchanged.
    val authMethod: DiscordAuthMethod = DiscordAuthMethod.TOKEN,
    // Rich Presence activity name/app-icon badge, per account. Defaults match what used to be
    // the global pref_discord_custom_activity_name/pref_discord_show_app_icon defaults, so an
    // account saved before these fields existed keeps behaving the same until migrated - see
    // Discord.migrateLegacyActivitySettingsIfNeeded().
    val customActivityName: String = "",
    val showAppIcon: Boolean = true,
    // Whether this account's Rich Presence is skipped under Incognito Mode - per account since
    // each account might be used in a different context. Defaults match the old global
    // pref_discord_respect_incognito default (also migrated, same as above).
    val respectIncognito: Boolean = true,
    // ActivityType.value (Watching/Playing/Streaming/Listening/Competing).
    val activityType: Int = ActivityType.WATCHING.value,
    val activityStateTemplate: String = DEFAULT_ACTIVITY_STATE_TEMPLATE,
    // One of DiscordOnlineStatus's constants.
    val onlineStatus: String = DiscordOnlineStatus.ONLINE,
    // A button is only sent if both its label and url are non-blank after placeholder
    // resolution - see DiscordRPCService.setReadingActivity(). Defaults to the two buttons
    // asked for: the chapter being read, and this project's repo.
    val button1Label: String = "Read Chapter",
    val button1Url: String = TEMPLATE_CHAPTER_URL,
    val button2Label: String = "Source Code",
    val button2Url: String = "https://github.com/CahyaXyZp/rokku",
)
