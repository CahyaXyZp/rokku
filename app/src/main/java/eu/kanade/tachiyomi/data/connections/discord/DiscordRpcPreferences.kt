package eu.kanade.tachiyomi.data.connections.discord

import eu.kanade.tachiyomi.core.preference.PreferenceStore

/**
 * User-facing preferences for the Discord Rich Presence integration.
 *
 * This only covers the toggles exposed on the Connections settings screen.
 * Account storage/auth and the RPC engine/service itself are tracked separately -
 * see docs/discord-rpc/AGENTS.md for the full scope breakdown.
 */
class DiscordRpcPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun enabled() = preferenceStore.getBoolean("pref_discord_rpc_enabled", false)

    fun customActivityText() = preferenceStore.getString("pref_discord_rpc_custom_text", "")

    fun suppressInIncognito() = preferenceStore.getBoolean("pref_discord_rpc_suppress_incognito", true)

    fun suppressFor18Plus() = preferenceStore.getBoolean("pref_discord_rpc_suppress_18plus", true)

    fun showCoverArt() = preferenceStore.getBoolean("pref_discord_rpc_show_cover_art", true)
}
