package yokai.presentation.settings.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.core.storage.preference.collectAsState
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.Discord
import eu.kanade.tachiyomi.data.connections.discord.DiscordOnlineStatus
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.presentation.component.preference.Preference
import yokai.presentation.settings.ComposableSettings
import yokai.presentation.settings.screen.discord.DiscordAccountPreferences

object SettingsDiscordScreen : ComposableSettings() {

    private fun readResolve() = SettingsDiscordScreen

    @Composable
    override fun getTitleRes(): StringResource = MR.strings.connections_discord

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val discord = remember { Injekt.get<ConnectionsManager>().discord }
        var account by remember { mutableStateOf(discord.getAccount()) }

        val loginLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            // DiscordLoginActivity saves the account (or doesn't) before finishing, whatever the result code.
            account = discord.getAccount()
        }

        return if (account == null) {
            getLoggedOutPreferences(
                onLogin = { loginLauncher.launch(Intent(context, DiscordLoginActivity::class.java)) },
            )
        } else {
            getAccountPreferences(discord)
        }
    }

    @Composable
    private fun getLoggedOutPreferences(onLogin: () -> Unit): List<Preference> {
        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.connections_discord),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(MR.strings.discord_rpc_not_connected),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.discord_rpc_login_via_discord),
                        onClick = onLogin,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun getAccountPreferences(discord: Discord): List<Preference> {
        val prefs = remember(discord) { DiscordAccountPreferences(discord) }
        val connectionsPreferences = remember { Injekt.get<ConnectionsPreferences>() }
        val enableRpc = remember { connectionsPreferences.enableDiscordRPC() }
        val uploadLocalCovers = remember { connectionsPreferences.discordUploadLocalCovers() }

        val rpcEnabled by enableRpc.collectAsState()
        val button1Enabled by prefs.button1Enabled.collectAsState()
        val button2Enabled by prefs.button2Enabled.collectAsState()
        val customName by prefs.activityName.collectAsState()

        val onlineStatuses = persistentMapOf(
            DiscordOnlineStatus.ONLINE to "Online",
            DiscordOnlineStatus.IDLE to "Idle",
            DiscordOnlineStatus.DND to "Do Not Disturb",
        )

        return persistentListOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.connections_discord),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = enableRpc,
                        title = stringResource(MR.strings.discord_rpc_enable),
                        subtitle = stringResource(MR.strings.discord_rpc_enable_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.discord_rpc_section_activity),
                enabled = rpcEnabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.activityName,
                        title = stringResource(MR.strings.discord_rpc_activity_name),
                        subtitle = customName.ifBlank { stringResource(MR.strings.discord_rpc_activity_name_summary) },
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.activityState,
                        title = stringResource(MR.strings.discord_rpc_state_template),
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(MR.strings.discord_rpc_state_template_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.appearance),
                enabled = rpcEnabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.showAppIcon,
                        title = stringResource(MR.strings.discord_rpc_show_app_icon),
                        subtitle = stringResource(MR.strings.discord_rpc_show_app_icon_summary),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = uploadLocalCovers,
                        title = stringResource(MR.strings.discord_rpc_upload_local_covers),
                        subtitle = stringResource(MR.strings.discord_rpc_upload_local_covers_summary),
                    ),
                    Preference.PreferenceItem.ListPreference(
                        pref = prefs.onlineStatus,
                        title = stringResource(MR.strings.discord_rpc_online_status),
                        entries = onlineStatuses,
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.discord_rpc_section_buttons),
                enabled = rpcEnabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.button1Enabled,
                        title = stringResource(MR.strings.discord_rpc_button1_enabled),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.button1Label,
                        title = stringResource(MR.strings.discord_rpc_button1_label),
                        enabled = button1Enabled,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.button1Url,
                        title = stringResource(MR.strings.discord_rpc_button1_url),
                        enabled = button1Enabled,
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.button2Enabled,
                        title = stringResource(MR.strings.discord_rpc_button2_enabled),
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.button2Label,
                        title = stringResource(MR.strings.discord_rpc_button2_label),
                        enabled = button2Enabled,
                    ),
                    Preference.PreferenceItem.EditTextPreference(
                        pref = prefs.button2Url,
                        title = stringResource(MR.strings.discord_rpc_button2_url),
                        enabled = button2Enabled,
                    ),
                    Preference.PreferenceItem.InfoPreference(
                        title = stringResource(MR.strings.discord_rpc_button_url_summary),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.discord_rpc_section_privacy),
                enabled = rpcEnabled,
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        pref = prefs.respectIncognito,
                        title = stringResource(MR.strings.discord_rpc_respect_incognito),
                        subtitle = stringResource(MR.strings.discord_rpc_respect_incognito_summary),
                    ),
                ),
            ),
        )
    }
}
