package yokai.presentation.settings.screen.discord

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.connections.discord.ActivityType
import eu.kanade.tachiyomi.data.connections.discord.Discord
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes one field of the saved [DiscordAccount] as a [Preference], so the shared Compose
 * settings widgets can bind to it. Reads and writes go through [Discord.getAccount] and
 * [Discord.updateAccount], so the account is still stored as a single JSON blob.
 */
internal class DiscordAccountPreference<T>(
    private val discord: Discord,
    private val key: String,
    private val fallback: T,
    private val read: (DiscordAccount) -> T,
    private val write: (DiscordAccount, T) -> DiscordAccount,
) : Preference<T> {

    private val state = MutableStateFlow(current())

    private fun current(): T = discord.getAccount()?.let(read) ?: fallback

    override fun key(): String = key

    override fun get(): T = current()

    override fun set(value: T) {
        discord.updateAccount { write(it, value) }
        state.value = current()
    }

    override fun isSet(): Boolean = discord.getAccount() != null

    override fun delete() = set(fallback)

    override fun defaultValue(): T = fallback

    override fun changes(): Flow<T> = state

    override fun stateIn(scope: CoroutineScope): StateFlow<T> = state
}

/** All per-account Rich Presence settings, created once per settings screen. */
internal class DiscordAccountPreferences(discord: Discord) {

    val activityName = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_activity_name",
        fallback = "",
        read = { it.customActivityName },
        write = { account, value -> account.copy(customActivityName = value.trim()) },
    )

    val activityType = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_activity_type",
        fallback = ActivityType.WATCHING.value,
        read = { it.activityType },
        write = { account, value -> account.copy(activityType = value) },
    )

    val activityState = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_state_template",
        fallback = "",
        read = { it.activityStateTemplate },
        write = { account, value -> account.copy(activityStateTemplate = value.trim()) },
    )

    val showAppIcon = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_show_app_icon",
        fallback = true,
        read = { it.showAppIcon },
        write = { account, value -> account.copy(showAppIcon = value) },
    )

    val onlineStatus = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_online_status",
        fallback = "",
        read = { it.onlineStatus },
        write = { account, value -> account.copy(onlineStatus = value) },
    )

    val button1Enabled = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button1_enabled",
        fallback = false,
        read = { it.button1Enabled },
        write = { account, value -> account.copy(button1Enabled = value) },
    )

    val button1Label = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button1_label",
        fallback = "",
        read = { it.button1Label },
        write = { account, value -> account.copy(button1Label = value.trim()) },
    )

    val button1Url = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button1_url",
        fallback = "",
        read = { it.button1Url },
        write = { account, value -> account.copy(button1Url = value.trim()) },
    )

    val button2Enabled = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button2_enabled",
        fallback = false,
        read = { it.button2Enabled },
        write = { account, value -> account.copy(button2Enabled = value) },
    )

    val button2Label = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button2_label",
        fallback = "",
        read = { it.button2Label },
        write = { account, value -> account.copy(button2Label = value.trim()) },
    )

    val button2Url = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_button2_url",
        fallback = "",
        read = { it.button2Url },
        write = { account, value -> account.copy(button2Url = value.trim()) },
    )

    val respectIncognito = DiscordAccountPreference(
        discord = discord,
        key = "discord_account_respect_incognito",
        fallback = true,
        read = { it.respectIncognito },
        write = { account, value -> account.copy(respectIncognito = value) },
    )
}
