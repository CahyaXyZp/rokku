package eu.kanade.tachiyomi.data.connections.discord

import kotlinx.serialization.Serializable

/**
 * A single logged-in Discord account.
 *
 * [token] is only ever handled in-memory or via [DiscordAccountCrypto] - the on-disk form
 * (see [DiscordAccountManager]) is always encrypted, never this class serialized directly
 * to plaintext preferences.
 */
@Serializable
data class DiscordAccount(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val token: String,
    val isActive: Boolean = false,
)
