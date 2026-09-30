package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.view.View
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.add
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.preference.TrackerPreference
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

/**
 * Settings > Connections. Currently only holds Discord Rich Presence, but is named generically
 * since more connected services may land here later.
 */
class SettingsConnectionsController : SettingsLegacyController() {

    private val connectionsManager: ConnectionsManager by injectLazy()

    private var trackerPreference: TrackerPreference? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.connections

        // One-time upgrade from when activity name/app-icon/respect-incognito were global
        // instead of stored on the account - see Discord.migrateLegacyActivitySettingsIfNeeded().
        connectionsManager.discord.migrateLegacyActivitySettingsIfNeeded()

        preferenceCategory {
            titleRes = MR.strings.services

            // Styled like a tracker row (colored logo card, green check when logged in) as in
            // Settings > Tracking.
            val discordPreference = TrackerPreference(context).apply {
                key = "discord_connections_entry"
                title = context.getString(MR.strings.connections_discord)
                iconRes = connectionsManager.discord.getLogo()
                iconColor = connectionsManager.discord.getLogoColor()
                isPersistent = false
                checked = isDiscordLoggedIn()
                onClick {
                    router.pushController(SettingsDiscordController().withFadeTransaction())
                }
                onLongClick = {
                    val loggedIn = isDiscordLoggedIn()
                    if (loggedIn) confirmLogout()
                    loggedIn
                }
            }
            trackerPreference = discordPreference
            add(discordPreference)

            infoPreference(MR.strings.discord_rpc_logout_hint)
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        trackerPreference?.checked = isDiscordLoggedIn()
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        trackerPreference?.checked = isDiscordLoggedIn()
    }

    private fun confirmLogout() {
        val act = activity ?: return
        act.materialAlertDialog()
            .setMessage(MR.strings.discord_rpc_remove_account_confirm)
            .setPositiveButton(AR.string.ok) { _, _ ->
                connectionsManager.discord.logout()
                act.toast(MR.strings.discord_rpc_account_removed)
                trackerPreference?.checked = false
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun isDiscordLoggedIn(): Boolean = connectionsManager.discord.getAccount() != null
}
