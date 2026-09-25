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
)
