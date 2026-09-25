package eu.kanade.tachiyomi.ui.setting.controllers

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Rich Presence settings for a single Discord account: custom activity name and whether to show
 * the app icon badge. Pushed from [SettingsDiscordAccountsController] when tapping an account -
 * holding an account there removes it instead, no activation control here since accounts go
 * active automatically as soon as they're added.
 */
class SettingsDiscordAccountController(bundle: Bundle) : SettingsLegacyController(bundle) {

    constructor(accountId: String) : this(
        Bundle().apply { putString(ACCOUNT_ID, accountId) },
    )

    private val connectionsManager: ConnectionsManager by injectLazy()

    private val accountId: String
        get() = args.getString(ACCOUNT_ID).orEmpty()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        val account = connectionsManager.discord.getAccounts().find { it.id == accountId }
        title = account?.username

        // account can be null if it was removed (e.g. from another device/session) while this
        // screen was still on the back stack - nothing to configure in that case.
        if (account == null) return@apply

        var customActivityName = account.customActivityName
        var showAppIcon = account.showAppIcon

        val nameInput = EditTextPreference(context).apply {
            // Not persisted to SharedPreferences (isPersistent = false below), but the dialog
            // framework still looks the preference up by key when the edit dialog is opened
            // (PreferenceManager.showDialog -> findPreference) - without one it throws
            // "Key cannot be null" as soon as this preference is tapped.
            key = KEY_ACTIVITY_NAME
            title = context.getString(MR.strings.discord_rpc_activity_name)
            dialogTitle = title
            dialogMessage = context.getString(MR.strings.discord_rpc_activity_name_summary)
            isIconSpaceReserved = false
            isPersistent = false
            text = customActivityName
            summary = customActivityName.ifBlank { context.getString(MR.strings.discord_rpc_activity_name_summary) }
            setOnPreferenceChangeListener { _, newValue ->
                customActivityName = (newValue as String).trim()
                summary = customActivityName.ifBlank { context.getString(MR.strings.discord_rpc_activity_name_summary) }
                connectionsManager.discord.updateAccountSettings(accountId, customActivityName, showAppIcon)
                true
            }
        }
        addPreference(nameInput)

        val showIconSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_SHOW_APP_ICON
            title = context.getString(MR.strings.discord_rpc_show_app_icon)
            summary = context.getString(MR.strings.discord_rpc_show_app_icon_summary)
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = showAppIcon
            setOnPreferenceChangeListener { _, newValue ->
                showAppIcon = newValue as Boolean
                connectionsManager.discord.updateAccountSettings(accountId, customActivityName, showAppIcon)
                true
            }
        }
        addPreference(showIconSwitch)
    }

    companion object {
        private const val ACCOUNT_ID = "account_id"
        private const val KEY_ACTIVITY_NAME = "discord_account_activity_name"
        private const val KEY_SHOW_APP_ICON = "discord_account_show_app_icon"
    }
}
