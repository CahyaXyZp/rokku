package eu.kanade.tachiyomi.ui.setting.connections

import android.app.Activity
import android.os.Bundle
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordAuthMethod
import eu.kanade.tachiyomi.data.connections.discord.DiscordRpcManager
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Transparent activity that runs the Social SDK's OAuth PKCE flow (via [DiscordRpcManager]) and,
 * on success, fetches the profile and saves the resulting [DiscordAccount]. Finishes with
 * RESULT_OK on success or RESULT_CANCELED otherwise - [SettingsDiscordAccountsController]
 * doesn't need to inspect which, it just refreshes its account list either way.
 */
class DiscordLoginActivity : Activity() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val scope = CoroutineScope(SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init()
        }

        DiscordRpcManager.authorize { accessToken ->
            if (accessToken == null) {
                finishWithResult(success = false)
                return@authorize
            }

            scope.launchIO {
                val user = DiscordRpcManager.fetchCurrentUser(accessToken)
                if (user == null) {
                    runOnUiThread {
                        toast(MR.strings.discord_rpc_account_add_failed)
                        finishWithResult(success = false)
                    }
                    return@launchIO
                }

                val account = DiscordAccount(
                    id = user.id,
                    username = user.username,
                    avatarUrl = user.avatarUrl,
                    token = accessToken,
                    authMethod = DiscordAuthMethod.SDK,
                )
                connectionsManager.discord.addAccount(account)

                runOnUiThread {
                    toast(MR.strings.discord_rpc_account_added)
                    finishWithResult(success = true)
                }
            }
        }
    }

    private fun finishWithResult(success: Boolean) {
        setResult(if (success) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}
