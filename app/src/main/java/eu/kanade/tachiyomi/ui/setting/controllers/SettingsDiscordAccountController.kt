package eu.kanade.tachiyomi.ui.setting.controllers

import android.content.Intent
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.ActivityType
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordOnlineStatus
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setMessage
import uy.kohesive.injekt.injectLazy
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

/**
 * Settings > Connections > Discord. Logged out it offers the Social SDK login; logged in it shows
 * the account and its Rich Presence settings, grouped into Activity/Appearance/Buttons/Privacy
 * sections (activity name/type/state template, app icon badge, online status, up to two
 * toggleable buttons, whether Incognito Mode should suppress Rich Presence), plus logout.
 */
class SettingsDiscordAccountController : SettingsLegacyController() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    private var screenRef: PreferenceScreen? = null

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
        screenRef = this
        titleRes = MR.strings.connections_discord
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
        val account = connectionsManager.discord.getAccount()
        if (account == null) showLogin(screen) else showSettings(screen, account)
    }

    private fun showLogin(screen: PreferenceScreen) {
        screen.preferenceCategory {
            titleRes = MR.strings.connections_discord

            infoPreference(MR.strings.discord_rpc_not_connected)

            preference {
                titleRes = MR.strings.discord_rpc_login_via_discord
                isIconSpaceReserved = false
                isPersistent = false
                onClick {
                    activity?.let { act ->
                        startActivityForResult(Intent(act, DiscordLoginActivity::class.java), REQUEST_SDK_LOGIN)
                    }
                }
            }
        }
    }

    private fun showSettings(screen: PreferenceScreen, account: DiscordAccount) {
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
            connectionsManager.discord.updateAccount {
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

        screen.apply {
            preference {
                isSelectable = false
                isIconSpaceReserved = true
                isPersistent = false
                title = account.username
                // Fetched profile picture, set once Coil loads it - the preference already
                // renders fine without one in the meantime, just with the icon space reserved.
                account.avatarUrl?.let { avatarUrl ->
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(avatarUrl)
                            .target(onSuccess = { image -> icon = image.asDrawable(context.resources) })
                            .build(),
                    )
                }
            }

            preferenceCategory {
                title = context.getString(MR.strings.discord_rpc_section_activity)

                val nameInput = EditTextPreference(context).apply {
                    // Not persisted to SharedPreferences (isPersistent = false below), but the
                    // dialog framework still looks the preference up by key when the edit dialog
                    // is opened (PreferenceManager.showDialog -> findPreference) - without one it
                    // throws "Key cannot be null" as soon as this preference is tapped.
                    key = KEY_ACTIVITY_NAME
                    title = context.getString(MR.strings.discord_rpc_activity_name)
                    dialogTitle = title
                    dialogMessage = context.getString(MR.strings.discord_rpc_activity_name_summary)
                    isIconSpaceReserved = false
                    isPersistent = false
                    text = customActivityName
                    summary =
                        customActivityName.ifBlank { context.getString(MR.strings.discord_rpc_activity_name_summary) }
                    setOnPreferenceChangeListener { _, newValue ->
                        customActivityName = (newValue as String).trim()
                        summary =
                            customActivityName.ifBlank { context.getString(MR.strings.discord_rpc_activity_name_summary) }
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
            }

            preferenceCategory {
                title = context.getString(MR.strings.appearance)

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

                val uploadCoversSwitch = SwitchPreferenceCompat(context).apply {
                    key = KEY_UPLOAD_LOCAL_COVERS
                    title = context.getString(MR.strings.discord_rpc_upload_local_covers)
                    summary = context.getString(MR.strings.discord_rpc_upload_local_covers_summary)
                    isIconSpaceReserved = false
                    isPersistent = false
                    isChecked = connectionsPreferences.discordUploadLocalCovers().get()
                    setOnPreferenceChangeListener { _, newValue ->
                        connectionsPreferences.discordUploadLocalCovers().set(newValue as Boolean)
                        true
                    }
                }
                addPreference(uploadCoversSwitch)

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
            }

            preferenceCategory {
                title = context.getString(MR.strings.discord_rpc_section_buttons)

                // Each "enable" switch is added before its label/url preferences, and
                // `.dependency = ...` on those is set only AFTER they themselves are added - a
                // preference has no PreferenceManager to search until it's attached
                // (addPreference/onAttachedToHierarchy), and Preference.setDependency() tries to
                // resolve the target through that manager immediately, synchronously. Setting
                // dependency inside the same apply{} block that builds the preference (i.e.
                // before it's attached) throws "Dependency ... not found" even though the target
                // switch above is already attached and would resolve fine once this one is too.
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
                button1LabelInput.dependency = KEY_BUTTON1_ENABLED

                val button1UrlInput = EditTextPreference(context).apply {
                    key = KEY_BUTTON1_URL
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
                button1UrlInput.dependency = KEY_BUTTON1_ENABLED

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
                button2LabelInput.dependency = KEY_BUTTON2_ENABLED

                val button2UrlInput = EditTextPreference(context).apply {
                    key = KEY_BUTTON2_URL
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
                button2UrlInput.dependency = KEY_BUTTON2_ENABLED
            }

            preferenceCategory {
                title = context.getString(MR.strings.discord_rpc_section_privacy)

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

            preferenceCategory {
                preference {
                    titleRes = MR.strings.discord_rpc_logout
                    isIconSpaceReserved = false
                    isPersistent = false
                    onClick {
                        activity?.let { act ->
                            act.materialAlertDialog()
                                .setMessage(MR.strings.discord_rpc_remove_account_confirm)
                                .setPositiveButton(AR.string.ok) { _, _ ->
                                    connectionsManager.discord.logout()
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
    }

    companion object {
        private const val REQUEST_SDK_LOGIN = 2001
        private const val KEY_ACTIVITY_NAME = "discord_account_activity_name"
        private const val KEY_ACTIVITY_TYPE = "discord_account_activity_type"
        private const val KEY_STATE_TEMPLATE = "discord_account_state_template"
        private const val KEY_SHOW_APP_ICON = "discord_account_show_app_icon"
        private const val KEY_UPLOAD_LOCAL_COVERS = "discord_account_upload_local_covers"
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
