package eu.kanade.tachiyomi.ui.setting.controllers

import android.widget.EditText
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.onLongClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.preferenceLongClickable
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

/**
 * Lists the Discord accounts saved for Rich Presence and lets the user add/remove/switch
 * between them. Accounts are added by token only for now - a WebView-based login flow (like
 * the one used for trackers) is a separate follow-up.
 */
class SettingsDiscordAccountsController : SettingsLegacyController() {

    private val connectionsManager: ConnectionsManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.discord_rpc_accounts
        refresh(this)
    }

    private fun refresh(screen: PreferenceScreen) {
        screen.removeAll()
        val accounts = connectionsManager.discord.getAccounts()

        screen.preferenceCategory {
            titleRes = MR.strings.discord_rpc_accounts

            if (accounts.isEmpty()) {
                infoPreference(MR.strings.discord_rpc_no_accounts)
            } else {
                accounts.forEach { account ->
                    preferenceLongClickable {
                        isIconSpaceReserved = true
                        title = account.username
                        summary = if (account.isActive) context.getString(MR.strings.discord_rpc_active_account) else null
                        onClick {
                            connectionsManager.discord.setActiveAccount(account.id)
                            refresh(screen)
                        }
                        onLongClick {
                            activity?.let { act ->
                                materialAlertDialog(act)
                                    .setMessage(MR.strings.discord_rpc_remove_account_confirm)
                                    .setPositiveButton(AR.string.ok) { _, _ ->
                                        connectionsManager.discord.removeAccount(account.id)
                                        act.toast(MR.strings.discord_rpc_account_removed)
                                        refresh(screen)
                                    }
                                    .setNegativeButton(AR.string.cancel, null)
                                    .show()
                            }
                        }
                    }
                }
            }

            preference {
                iconRes = R.drawable.ic_add_24dp
                titleRes = MR.strings.discord_rpc_add_account
                isPersistent = false
                onClick { showAddAccountDialog(screen) }
            }
        }
    }

    private fun showAddAccountDialog(screen: PreferenceScreen) {
        val act = activity ?: return
        val editText = EditText(act)
        materialAlertDialog(act)
            .setTitle(MR.strings.discord_rpc_add_account)
            .setMessage(MR.strings.discord_rpc_add_account_dialog_message)
            .setView(editText)
            .setPositiveButton(AR.string.ok) { _, _ ->
                val token = editText.text.toString().trim()
                if (token.isBlank()) return@setPositiveButton
                viewScope.launchIO {
                    val account = connectionsManager.discord.fetchProfile(token)
                    if (account != null) {
                        connectionsManager.discord.addAccount(account)
                        act.toast(MR.strings.discord_rpc_account_added)
                    } else {
                        act.toast(MR.strings.discord_rpc_account_add_failed)
                    }
                    act.runOnUiThread { refresh(screen) }
                }
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }
}
