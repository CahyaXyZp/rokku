package yokai.presentation.connections.discord

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import coil3.compose.AsyncImage
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.ui.setting.connections.DiscordLoginActivity
import eu.kanade.tachiyomi.util.compose.LocalBackPress
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.component.EmptyScreen
import yokai.util.Screen

class DiscordAccountsScreen : Screen() {
    @Composable
    override fun Content() {
        val onBackPress = LocalBackPress.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel { DiscordAccountsScreenModel() }
        val state by screenModel.state.collectAsState()

        val loginLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            // DiscordLoginActivity already saves the account itself on success; this is just
            // an immediate refresh so the new entry doesn't wait for the preference-change flow.
            if (result.resultCode == Activity.RESULT_OK) screenModel.refresh()
        }

        YokaiScaffold(
            onNavigationIconClicked = onBackPress,
            title = stringResource(MR.strings.discord_accounts),
            appBarType = AppBarType.SMALL,
            actions = {
                IconButton(onClick = { loginLauncher.launch(Intent(context, DiscordLoginActivity::class.java)) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(MR.strings.discord_accounts_add),
                    )
                }
            },
        ) { innerPadding ->
            val accounts = (state as? DiscordAccountsScreenModel.State.Success)?.accounts

            if (accounts == null) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@YokaiScaffold
            }

            if (accounts.isEmpty()) {
                EmptyScreen(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    image = Icons.Filled.Person,
                    message = stringResource(MR.strings.discord_accounts_empty),
                    isTablet = false,
                )
                return@YokaiScaffold
            }

            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(accounts, key = { it.id }) { account ->
                    DiscordAccountRow(
                        account = account,
                        onSetActive = { screenModel.setActiveAccount(account.id) },
                        onRemove = { screenModel.removeAccount(account.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscordAccountRow(
    account: DiscordAccount,
    onSetActive: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSetActive)
            .background(
                if (account.isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = account.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = account.username, style = MaterialTheme.typography.titleMedium)
            if (account.isActive) {
                Text(
                    text = stringResource(MR.strings.discord_accounts_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(MR.strings.discord_accounts_remove),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
