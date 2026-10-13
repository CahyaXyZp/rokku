package yokai.presentation.settings.screen

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.presentation.component.preference.Preference
import yokai.presentation.settings.ComposableSettings
import yokai.util.lang.getString

object SettingsTrackingScreen : ComposableSettings() {

    private fun readResolve() = SettingsTrackingScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes(): StringResource = MR.strings.tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://mihon.app/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val preferences: PreferencesHelper by injectLazy()
        val trackManager: TrackManager by injectLazy()
        val sourceManager: SourceManager by injectLazy()

        // Replaces updatePreference()/onActivityResumed from the old controller.
        var refresh by remember { mutableIntStateOf(0) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

        var dialog by remember { mutableStateOf<Any?>(null) }
        when (val d = dialog) {
            is LoginDialog -> TrackingLoginDialog(d.tracker, d.uNameStringRes) {
                dialog = null
                refresh++
            }
            is LogoutDialog -> TrackingLogoutDialog(d.tracker) {
                dialog = null
                refresh++
            }
        }

        fun oauth(service: TrackService, url: () -> Uri) = Preference.PreferenceItem.TrackerPreference(
            tracker = service,
            title = context.getString(service.nameRes()),
            login = { context.openInBrowser(url(), service.getLogoColor(), true) },
            logout = { dialog = LogoutDialog(service) },
        )

        fun credentials(service: TrackService, uname: StringResource) =
            Preference.PreferenceItem.TrackerPreference(
                tracker = service,
                title = context.getString(service.nameRes()),
                login = { dialog = LoginDialog(service, uname) },
                logout = { dialog = LogoutDialog(service) },
            )

        val services = remember(refresh) {
            buildList<Preference.PreferenceItem<out Any>> {
                add(oauth(trackManager.myAnimeList) { MyAnimeListApi.authUrl() })
                add(oauth(trackManager.aniList) { AnilistApi.authUrl() })
                add(credentials(trackManager.kitsu, MR.strings.email))
                add(credentials(trackManager.mangaUpdates, MR.strings.username))
                add(oauth(trackManager.shikimori) { ShikimoriApi.authUrl() })
                add(oauth(trackManager.bangumi) { BangumiApi.authUrl() })
                add(oauth(trackManager.mangaBaka) { MangaBakaApi.authUrl() })
                add(oauth(trackManager.hikka) { HikkaApi.authUrl() })
                add(Preference.PreferenceItem.InfoPreference(context.getString(MR.strings.tracking_info)))
            }.toImmutableList()
        }

        val enhanced = remember(refresh) {
            buildList<Preference.PreferenceItem<out Any>> {
                trackManager.services
                    .filter { s ->
                        s is EnhancedTrackService &&
                            sourceManager.getCatalogueSources().any { s.accept(it) }
                    }
                    .forEach { s ->
                        add(
                            Preference.PreferenceItem.TrackerPreference(
                                tracker = s,
                                title = context.getString(s.nameRes()),
                                login = {
                                    (s as EnhancedTrackService).loginNoop()
                                    refresh++
                                },
                                logout = {
                                    s.logout()
                                    refresh++
                                },
                            ),
                        )
                    }
                add(Preference.PreferenceItem.InfoPreference(context.getString(MR.strings.enhanced_tracking_info)))
            }.toImmutableList()
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.autoUpdateTrack(),
                title = stringResource(MR.strings.update_tracking_after_reading),
            ),
            Preference.PreferenceItem.SwitchPreference(
                pref = preferences.trackMarkedAsRead(),
                title = stringResource(MR.strings.update_tracking_marked_read),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = services,
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = enhanced,
            ),
        )
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: TrackService,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var username by remember { mutableStateOf(tracker.getUsername()) }
        var password by remember { mutableStateOf(tracker.getPassword()) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }
        var hidePassword by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.log_in_to_, stringResource(tracker.nameRes())))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(text = stringResource(uNameStringRes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = inputError && !processing,
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(text = stringResource(MR.strings.password)) },
                        singleLine = true,
                        visualTransformation = if (hidePassword) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                Icon(
                                    imageVector = if (hidePassword) {
                                        Icons.Rounded.Visibility
                                    } else {
                                        Icons.Rounded.VisibilityOff
                                    },
                                    contentDescription = null,
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        isError = inputError && !processing,
                    )
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            processing = true
                            val ok = checkLogin(context, tracker, username, password)
                            inputError = !ok
                            if (ok) onDismissRequest()
                            processing = false
                        }
                    },
                ) {
                    Text(text = stringResource(if (processing) MR.strings.loading else MR.strings.log_in))
                }
            },
        )
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: TrackService,
        username: String,
        password: String,
    ): Boolean {
        return try {
            val ok = tracker.login(username, password)
            if (!ok) {
                tracker.logout()
                withContext(Dispatchers.Main) { context.toast(context.getString(MR.strings.unknown_error)) }
                return false
            }
            withContext(Dispatchers.Main) { context.toast(context.getString(MR.strings.successfully_logged_in)) }
            true
        } catch (e: Throwable) {
            tracker.logout()
            withContext(Dispatchers.Main) { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: TrackService,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.log_out_from_, stringResource(tracker.nameRes())),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(context.getString(MR.strings.successfully_logged_out))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.log_out))
                    }
                }
            },
        )
    }
}

private data class LoginDialog(
    val tracker: TrackService,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: TrackService,
)
