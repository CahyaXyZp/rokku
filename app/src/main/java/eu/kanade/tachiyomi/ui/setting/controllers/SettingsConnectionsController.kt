package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

/**
 * Settings > Connections. Currently only holds Discord Rich Presence, but is named generically
 * since more connected services may land here later.
 */
class SettingsConnectionsController : SettingsLegacyController() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    private var accountsPreference: Preference? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.connections

        // One-time upgrade from when activity name/app-icon were global instead of per-account -
        // see Discord.migrateLegacyActivitySettingsIfNeeded() for what this actually does.
        connectionsManager.discord.migrateLegacyActivitySettingsIfNeeded()

        preferenceCategory {
            titleRes = MR.strings.connections_discord

            switchPreference {
                bindTo(connectionsPreferences.enableDiscordRPC())
                titleRes = MR.strings.discord_rpc_enable
                summary = context.getString(MR.strings.discord_rpc_enable_summary)
            }

            accountsPreference = preference {
                iconRes = R.drawable.ic_discord_24dp
                titleRes = MR.strings.discord_rpc_connect_account
                isPersistent = false
                summary = accountSummary(context)
                onClick {
                    router.pushController(SettingsDiscordAccountsController().withFadeTransaction())
                }
            }

            switchPreference {
                bindTo(connectionsPreferences.discordRespectIncognito())
                titleRes = MR.strings.discord_rpc_respect_incognito
                summary = context.getString(MR.strings.discord_rpc_respect_incognito_summary)
            }
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        accountsPreference?.summary = accountSummary(activity)
    }

    private fun accountSummary(context: Context): String {
        val accounts = connectionsManager.discord.getAccounts()
        return when {
            accounts.isEmpty() -> context.getString(MR.strings.discord_rpc_not_connected)
            accounts.size == 1 -> accounts.first().username
            else -> context.getString(MR.strings.discord_rpc_accounts_connected, accounts.size)
        }
    }
}
