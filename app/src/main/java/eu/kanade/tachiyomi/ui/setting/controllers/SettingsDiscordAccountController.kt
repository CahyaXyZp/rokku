package eu.kanade.tachiyomi.ui.setting.controllers

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.ActivityType
import eu.kanade.tachiyomi.data.connections.discord.DiscordOnlineStatus
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Rich Presence settings for a single Discord account - activity name/type/state template, app
 * icon badge, online status, up to two toggleable buttons, and whether Incognito Mode should
 * suppress Rich Presence for this account. Pushed from [SettingsDiscordAccountsController] when
 * tapping an account - holding an account there removes it instead, no activation control here
 * since accounts go active automatically as soon as they're added.
 */
class SettingsDiscordAccountController(bundle: Bundle) : SettingsLegacyController(bundle) {

    constructor(accountId: String) : this(
        Bundle().apply { putString(ACCOUNT_ID, accountId) },
    )

    private val connectionsManager: ConnectionsManager by injectLazy()

    private val accountId: String
        get() = args.getString(ACCOUNT_ID).orEmpty()

    // Discord's own fixed vocabulary for these (shown on Discord itself, not app UI copy), so
    // kept as plain literals here rather than translated strings - same treatment as button
    // defaults like "Read Chapter" a few lines below.
    private val activityTypeOptions = listOf(
        ActivityType.PLAYING to "Playing",
        ActivityType.STREAMING to "Streaming",
        ActivityType.LISTENING to "Listening",
        ActivityType.WATCHING to "Watching",
        ActivityType.COMPETING to "Competing",
    )

    private val onlineStatusOptions = listOf(
        DiscordOnlineStatus.ONLINE to "Online",
        DiscordOnlineStatus.IDLE to "Idle",
        DiscordOnlineStatus.DND to "Do Not Disturb",
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        val account = connectionsManager.discord.getAccounts().find { it.id == accountId }
        title = account?.username

        // account can be null if it was removed (e.g. from another device/session) while this
        // screen was still on the back stack - nothing to configure in that case.
        if (account == null) return@apply

        var customActivityName = account.customActivityName
        var showAppIcon = account.showAppIcon
        var respectIncognito = account.respectIncognito
        var activityType = account.activityType
        var activityStateTemplate = account.activityStateTemplate
        var onlineStatus = account.onlineStatus
        var button1Enabled = account.button1Enabled
        var button1Label = account.button1Label
        var button1Url = account.button1Url
        var button2Enabled = account.button2Enabled
        var button2Label = account.button2Label
        var button2Url = account.button2Url

        fun persist() {
            connectionsManager.discord.updateAccount(accountId) {
                it.copy(
                    customActivityName = customActivityName,
                    showAppIcon = showAppIcon,
                    respectIncognito = respectIncognito,
                    activityType = activityType,
                    activityStateTemplate = activityStateTemplate,
                    onlineStatus = onlineStatus,
                    button1Enabled = button1Enabled,
                    button1Label = button1Label,
                    button1Url = button1Url,
                    button2Enabled = button2Enabled,
                    button2Label = button2Label,
                    button2Url = button2Url,
                )
            }
        }

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
                persist()
                true
            }
        }
        addPreference(nameInput)

        val activityTypePref = ListPreference(context).apply {
            key = KEY_ACTIVITY_TYPE
            title = context.getString(MR.strings.discord_rpc_activity_type)
            dialogTitle = title
            isIconSpaceReserved = false
            isPersistent = false
            entries = activityTypeOptions.map { it.second }.toTypedArray()
            entryValues = activityTypeOptions.map { it.first.value.toString() }.toTypedArray()
            value = activityType.toString()
            summary = activityTypeOptions.find { it.first.value == activityType }?.second
            setOnPreferenceChangeListener { _, newValue ->
                activityType = (newValue as String).toInt()
                summary = activityTypeOptions.find { it.first.value == activityType }?.second
                persist()
                true
            }
        }
        addPreference(activityTypePref)

        val stateTemplateInput = EditTextPreference(context).apply {
            key = KEY_STATE_TEMPLATE
            title = context.getString(MR.strings.discord_rpc_state_template)
            dialogTitle = title
            dialogMessage = context.getString(MR.strings.discord_rpc_state_template_summary)
            isIconSpaceReserved = false
            isPersistent = false
            text = activityStateTemplate
            summary = activityStateTemplate
            setOnPreferenceChangeListener { _, newValue ->
                activityStateTemplate = (newValue as String).trim()
                summary = activityStateTemplate
                persist()
                true
            }
        }
        addPreference(stateTemplateInput)

        val showIconSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_SHOW_APP_ICON
            title = context.getString(MR.strings.discord_rpc_show_app_icon)
            summary = context.getString(MR.strings.discord_rpc_show_app_icon_summary)
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = showAppIcon
            setOnPreferenceChangeListener { _, newValue ->
                showAppIcon = newValue as Boolean
                persist()
                true
            }
        }
        addPreference(showIconSwitch)

        val onlineStatusPref = ListPreference(context).apply {
            key = KEY_ONLINE_STATUS
            title = context.getString(MR.strings.discord_rpc_online_status)
            dialogTitle = title
            isIconSpaceReserved = false
            isPersistent = false
            entries = onlineStatusOptions.map { it.second }.toTypedArray()
            entryValues = onlineStatusOptions.map { it.first }.toTypedArray()
            value = onlineStatus
            summary = onlineStatusOptions.find { it.first == onlineStatus }?.second
            setOnPreferenceChangeListener { _, newValue ->
                onlineStatus = newValue as String
                summary = onlineStatusOptions.find { it.first == onlineStatus }?.second
                persist()
                true
            }
        }
        addPreference(onlineStatusPref)

        // The dependency-key wiring below (dependency = KEY_BUTTON1_ENABLED) needs the switch
        // it points at already attached to the screen, so each "enable" switch is added before
        // its own label/url preferences.
        val button1EnabledSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_BUTTON1_ENABLED
            title = context.getString(MR.strings.discord_rpc_button1_enabled)
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = button1Enabled
            setOnPreferenceChangeListener { _, newValue ->
                button1Enabled = newValue as Boolean
                persist()
                true
            }
        }
        addPreference(button1EnabledSwitch)

        val button1LabelInput = EditTextPreference(context).apply {
            key = KEY_BUTTON1_LABEL
            dependency = KEY_BUTTON1_ENABLED
            title = context.getString(MR.strings.discord_rpc_button1_label)
            dialogTitle = title
            isIconSpaceReserved = false
            isPersistent = false
            text = button1Label
            summary = button1Label
            setOnPreferenceChangeListener { _, newValue ->
                button1Label = (newValue as String).trim()
                summary = button1Label
                persist()
                true
            }
        }
        addPreference(button1LabelInput)

        val button1UrlInput = EditTextPreference(context).apply {
            key = KEY_BUTTON1_URL
            dependency = KEY_BUTTON1_ENABLED
            title = context.getString(MR.strings.discord_rpc_button1_url)
            dialogTitle = title
            dialogMessage = context.getString(MR.strings.discord_rpc_button_url_summary)
            isIconSpaceReserved = false
            isPersistent = false
            text = button1Url
            summary = button1Url.ifBlank { context.getString(MR.strings.discord_rpc_button_url_summary) }
            setOnPreferenceChangeListener { _, newValue ->
                button1Url = (newValue as String).trim()
                summary = button1Url.ifBlank { context.getString(MR.strings.discord_rpc_button_url_summary) }
                persist()
                true
            }
        }
        addPreference(button1UrlInput)

        val button2EnabledSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_BUTTON2_ENABLED
            title = context.getString(MR.strings.discord_rpc_button2_enabled)
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = button2Enabled
            setOnPreferenceChangeListener { _, newValue ->
                button2Enabled = newValue as Boolean
                persist()
                true
            }
        }
        addPreference(button2EnabledSwitch)

        val button2LabelInput = EditTextPreference(context).apply {
            key = KEY_BUTTON2_LABEL
            dependency = KEY_BUTTON2_ENABLED
            title = context.getString(MR.strings.discord_rpc_button2_label)
            dialogTitle = title
            isIconSpaceReserved = false
            isPersistent = false
            text = button2Label
            summary = button2Label
            setOnPreferenceChangeListener { _, newValue ->
                button2Label = (newValue as String).trim()
                summary = button2Label
                persist()
                true
            }
        }
        addPreference(button2LabelInput)

        val button2UrlInput = EditTextPreference(context).apply {
            key = KEY_BUTTON2_URL
            dependency = KEY_BUTTON2_ENABLED
            title = context.getString(MR.strings.discord_rpc_button2_url)
            dialogTitle = title
            dialogMessage = context.getString(MR.strings.discord_rpc_button_url_summary)
            isIconSpaceReserved = false
            isPersistent = false
            text = button2Url
            summary = button2Url.ifBlank { context.getString(MR.strings.discord_rpc_button_url_summary) }
            setOnPreferenceChangeListener { _, newValue ->
                button2Url = (newValue as String).trim()
                summary = button2Url.ifBlank { context.getString(MR.strings.discord_rpc_button_url_summary) }
                persist()
                true
            }
        }
        addPreference(button2UrlInput)

        val respectIncognitoSwitch = SwitchPreferenceCompat(context).apply {
            key = KEY_RESPECT_INCOGNITO
            title = context.getString(MR.strings.discord_rpc_respect_incognito)
            summary = context.getString(MR.strings.discord_rpc_respect_incognito_summary)
            isIconSpaceReserved = false
            isPersistent = false
            isChecked = respectIncognito
            setOnPreferenceChangeListener { _, newValue ->
                respectIncognito = newValue as Boolean
                persist()
                true
            }
        }
        addPreference(respectIncognitoSwitch)
    }

    companion object {
        private const val ACCOUNT_ID = "account_id"
        private const val KEY_ACTIVITY_NAME = "discord_account_activity_name"
        private const val KEY_ACTIVITY_TYPE = "discord_account_activity_type"
        private const val KEY_STATE_TEMPLATE = "discord_account_state_template"
        private const val KEY_SHOW_APP_ICON = "discord_account_show_app_icon"
        private const val KEY_ONLINE_STATUS = "discord_account_online_status"
        private const val KEY_BUTTON1_ENABLED = "discord_account_button1_enabled"
        private const val KEY_BUTTON1_LABEL = "discord_account_button1_label"
        private const val KEY_BUTTON1_URL = "discord_account_button1_url"
        private const val KEY_BUTTON2_ENABLED = "discord_account_button2_enabled"
        private const val KEY_BUTTON2_LABEL = "discord_account_button2_label"
        private const val KEY_BUTTON2_URL = "discord_account_button2_url"
        private const val KEY_RESPECT_INCOGNITO = "discord_account_respect_incognito"
    }
}
