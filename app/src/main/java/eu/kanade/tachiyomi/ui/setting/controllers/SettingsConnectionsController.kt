package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.discord.DiscordRpcPreferences
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.editTextPreference
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.summaryMRes
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.util.system.toast
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

/**
 * Connections settings screen.
 *
 * Currently hosts the Discord Rich Presence toggles (see docs/discord-rpc). Accounts,
 * the RPC engine itself, and reader wiring are tracked separately - this screen only
 * exposes the preferences plus a placeholder entry point for account management.
 */
class SettingsConnectionsController : SettingsLegacyController() {

    private val discordRpcPreferences: DiscordRpcPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.connections

        preferenceCategory {
            titleRes = MR.strings.discord_rich_presence

            switchPreference {
                bindTo(discordRpcPreferences.enabled())
                titleRes = MR.strings.discord_rpc_enable
                summaryMRes = MR.strings.discord_rpc_enable_summary
            }

            preference {
                iconRes = R.drawable.ic_discord_24dp
                titleRes = MR.strings.discord_rpc_accounts
                summaryMRes = MR.strings.discord_rpc_accounts_summary

                discordRpcPreferences.enabled().changesIn(viewScope) { isVisible = it }

                onClick {
                    // TODO: navigate to account management once Agent 2 implements it
                    context.toast(context.getString(MR.strings.discord_rpc_accounts_coming_soon))
                }
            }

            editTextPreference(activity) {
                bindTo(discordRpcPreferences.customActivityText())
                titleRes = MR.strings.discord_rpc_custom_text
                summaryMRes = MR.strings.discord_rpc_custom_text_summary

                discordRpcPreferences.enabled().changesIn(viewScope) { isVisible = it }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.privacy

            switchPreference {
                bindTo(discordRpcPreferences.suppressInIncognito())
                titleRes = MR.strings.discord_rpc_suppress_incognito
                summaryMRes = MR.strings.discord_rpc_suppress_incognito_summary

                discordRpcPreferences.enabled().changesIn(viewScope) { isVisible = it }
            }

            switchPreference {
                bindTo(discordRpcPreferences.suppressFor18Plus())
                titleRes = MR.strings.discord_rpc_suppress_18plus
                summaryMRes = MR.strings.discord_rpc_suppress_18plus_summary

                discordRpcPreferences.enabled().changesIn(viewScope) { isVisible = it }
            }

            switchPreference {
                bindTo(discordRpcPreferences.showCoverArt())
                titleRes = MR.strings.discord_rpc_show_cover_art
                summaryMRes = MR.strings.discord_rpc_show_cover_art_summary

                discordRpcPreferences.enabled().changesIn(viewScope) { isVisible = it }
            }

            infoPreference(MR.strings.discord_rpc_info)
        }
    }
}
