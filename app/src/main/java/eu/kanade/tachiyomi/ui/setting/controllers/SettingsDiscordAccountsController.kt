package eu.kanade.tachiyomi.ui.setting.controllers

import android.content.Intent
import android.widget.EditText
import androidx.preference.PreferenceScreen
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.onLongClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.preferenceLongClickable
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

/**
 * Lists the Discord accounts saved for Rich Presence and lets the user add/remove/switch
 * between them. Adding an account offers a choice: the official Social SDK login (default), or
 * a raw account token (advanced - e.g. for accounts the SDK login flow doesn't support).
 */
class SettingsDiscordAccountsController : SettingsLegacyController() {

    private val connectionsManager: ConnectionsManager by injectLazy()

    private var screenRef: PreferenceScreen? = null

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        screenRef = this
        titleRes = MR.strings.discord_rpc_accounts
        refresh(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SDK_LOGIN) {
            // DiscordLoginActivity already saved the account (or didn't) before finishing -
            // just reflect whatever state that left us in, regardless of resultCode.
            screenRef?.let { refresh(it) }
        }
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
                        // Fetched profile picture, set once Coil loads it - the preference
                        // already renders fine without one in the meantime, just with the
                        // icon space reserved above.
                        account.avatarUrl?.let { avatarUrl ->
                            context.imageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(avatarUrl)
                                    .target(onSuccess = { image -> icon = image.asDrawable(context.resources) })
                                    .build(),
                            )
                        }
                        // Tap opens this account's Rich Presence settings (activity name, app
                        // icon badge) - there's no "set active" control anymore, accounts go
                        // active automatically as soon as they're added.
                        onClick {
                            router.pushController(SettingsDiscordAccountController(account.id).withFadeTransaction())
                        }
                        onLongClick {
                            activity?.let { act ->
                                act.materialAlertDialog()
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
                onClick { showAddAccountMethodDialog(screen) }
            }
        }
    }

    /**
     * Lets the user pick how to add an account: the official Social SDK login (default), or a
     * raw account token (advanced).
     */
    private fun showAddAccountMethodDialog(screen: PreferenceScreen) {
        val act = activity ?: return
        act.materialAlertDialog()
            .setTitle(MR.strings.discord_rpc_add_account)
            .setMessage(MR.strings.discord_rpc_add_account_choose_method)
            .setPositiveButton(act.getString(MR.strings.discord_rpc_login_via_discord)) { _, _ ->
                startActivityForResult(Intent(act, DiscordLoginActivity::class.java), REQUEST_SDK_LOGIN)
            }
            .setNegativeButton(act.getString(MR.strings.discord_rpc_login_via_token)) { _, _ ->
                showTokenLoginDialog(screen)
            }
            .show()
    }

    private fun showTokenLoginDialog(screen: PreferenceScreen) {
        val act = activity ?: return
        val editText = EditText(act)
        act.materialAlertDialog()
            .setTitle(MR.strings.discord_rpc_add_account)
            .setMessage(MR.strings.discord_rpc_add_account_dialog_message)
            .setView(editText)
            .setPositiveButton(AR.string.ok) { _, _ ->
                val token = editText.text.toString().trim()
                if (token.isBlank()) return@setPositiveButton
                // fetchProfile does network I/O and must run off the main thread, but every
                // call after it (toast, saving the account, rebuilding the screen) touches
                // UI/main-thread-only APIs, so it's all funneled back through runOnUiThread.
                viewScope.launchIO {
                    val account = connectionsManager.discord.fetchProfile(token)
                    act.runOnUiThread {
                        if (account != null) {
                            connectionsManager.discord.addAccount(account)
                            act.toast(MR.strings.discord_rpc_account_added)
                        } else {
                            act.toast(MR.strings.discord_rpc_account_add_failed)
                        }
                        refresh(screen)
                    }
                }
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    companion object {
        private const val REQUEST_SDK_LOGIN = 2001
    }
}
