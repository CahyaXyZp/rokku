package yokai.presentation.connections.discord

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccountManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy

class DiscordAccountsScreenModel : StateScreenModel<DiscordAccountsScreenModel.State>(State.Loading) {

    private val accountManager: DiscordAccountManager by injectLazy()

    init {
        refresh()
        screenModelScope.launch {
            // The encrypted accounts pref changes whenever this screen's own actions save, but
            // also covers a login completing via DiscordLoginActivity's activity-result callback.
            accountManager.changes().collectLatest { refresh() }
        }
    }

    fun refresh() {
        mutableState.value = State.Success(accountManager.accounts().toImmutableList())
    }

    fun setActiveAccount(accountId: String) {
        accountManager.setActiveAccount(accountId)
        refresh()
    }

    fun removeAccount(accountId: String) {
        accountManager.removeAccount(accountId)
        refresh()
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(val accounts: ImmutableList<DiscordAccount>) : State
    }
}
