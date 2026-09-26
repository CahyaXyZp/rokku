// Adapted from Komikku (originally from Animiru) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Foreground service that keeps Discord Rich Presence updated while the user is reading.
 * Entirely optional: [start] no-ops unless the user has enabled it in settings, has an active
 * account, and (when that account's "Respect Incognito Mode" is on) isn't reading a source
 * under Incognito Mode (global or per-extension) - and stopping it (or disabling the setting)
 * never affects normal app operation.
 *
 * Backs onto whichever connection the active account's [DiscordAuthMethod] calls for:
 * [DiscordRPC] (a Discord Gateway connection over the account's token) for Token Login accounts,
 * or [DiscordRpcManager] (the official Social SDK, over JNI) for accounts added via the SDK.
 * One active account at a time for now - multi-account (Rich Presence for every connected
 * account at once) is a planned follow-up.
 *
 * Doesn't stop the instant reading pauses either: see [scheduleStop]/[resumeReading].
 */
class DiscordRPCService : Service() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Starting Discord RPC service" }

        val account = connectionsManager.discord.getAccounts().find { it.isActive }
        val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
        if (account == null || token.isBlank()) {
            Logger.w { "Discord RPC disabled due to missing account/token" }
            connectionsPreferences.enableDiscordRPC().set(false)
            stopSelf()
            return
        }

        startForeground(Notifications.ID_DISCORD_RPC, notification())
        connectUsing(account, token)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESTART -> restartRPC()

            ACTION_STOP -> {
                Logger.i { "Stopping Discord RPC service" }
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun restartRPC() {
        disconnect()

        val account = connectionsManager.discord.getAccounts().find { it.isActive }
        val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
        if (account == null || token.isBlank()) {
            Logger.w { "Discord RPC restart failed due to missing account/token" }
            stopSelf()
            return
        }

        connectUsing(account, token)
    }

    private fun connectUsing(account: DiscordAccount, token: String) {
        when (account.authMethod) {
            DiscordAuthMethod.SDK -> {
                usingSdk = true
                if (!DiscordRpcManager.isInitialized()) {
                    DiscordRpcManager.init()
                }
                DiscordRpcManager.reconnectWithToken(token)
            }

            DiscordAuthMethod.TOKEN -> {
                usingSdk = false
                rpc = DiscordRPC(token)
            }
        }
    }

    private fun disconnect() {
        if (usingSdk) {
            DiscordRpcManager.disconnect()
        } else {
            rpc?.closeRPC()
        }
        rpc = null
        usingSdk = false
    }

    private fun notification(): Notification {
        return notificationBuilder(Notifications.CHANNEL_DISCORD_RPC) {
            setSmallIcon(R.drawable.ic_discord_24dp)
            setContentTitle(getString(MR.strings.connections_discord))
            setContentText(getString(MR.strings.discord_rpc_notification_content))
            setOngoing(true)
            setAutoCancel(false)
            setUsesChronometer(true)
        }.build()
    }

    companion object {
        private var rpc: DiscordRPC? = null
        private var usingSdk = false
        private var since = 0L

        private const val ACTION_RESTART = "eu.kanade.tachiyomi.DISCORD_RPC_RESTART"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.DISCORD_RPC_STOP"

        // Fixed grace period - see scheduleStop()/resumeReading().
        private const val STOP_DEBOUNCE_MS = 5 * 60 * 1000L

        // Outlives any single Service instance (companion, like rpc/usingSdk above), since the
        // whole point of scheduleStop() is for the delayed stop to keep counting down across
        // the reader activity's onPause/onResume/onDestroy, not just within one of them.
        private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pendingStopJob: Job? = null
        private var pendingStopMangaId: Long? = null

        private fun isConnected() = rpc != null || usingSdk

        private fun respectsIncognito(
            account: DiscordAccount,
            sourceId: Long?,
            preferences: PreferencesHelper,
            extensionManager: ExtensionManager,
        ): Boolean {
            if (!account.respectIncognito) return false
            return isIncognitoModeForSource(sourceId, preferences, extensionManager)
        }

        fun start(
            context: Context,
            sourceId: Long? = null,
            connectionsManager: ConnectionsManager = Injekt.get(),
            connectionsPreferences: ConnectionsPreferences = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            if (!connectionsPreferences.enableDiscordRPC().get()) return

            val account = connectionsManager.discord.getAccounts().find { it.isActive }
            val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
            if (account == null || token.isBlank()) {
                Logger.w { "Discord RPC not started due to missing account/token" }
                connectionsPreferences.enableDiscordRPC().set(false)
                return
            }
            if (respectsIncognito(account, sourceId, preferences, extensionManager)) return

            // Always restarts the elapsed-time counter - this used to sit inside the
            // `!isConnected()` check below, but scheduleStop()/resumeReading() can now leave
            // the service running (isConnected() == true) across a switch to a different
            // manga, and that switch should still restart the timestamp.
            since = System.currentTimeMillis()
            if (!isConnected()) {
                context.startForegroundService(Intent(context, DiscordRPCService::class.java))
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, DiscordRPCService::class.java).apply { action = ACTION_STOP },
                )
            } catch (e: Exception) {
                Logger.e(e) { "Failed to stop Discord RPC service" }
            }
        }

        /**
         * Call from the reader's onPause() instead of [stop] directly. Doesn't stop anything
         * immediately - schedules [stop] to run after [STOP_DEBOUNCE_MS], tagged with
         * [mangaId], so a brief app-switch or screen-off doesn't drop the connection or reset
         * the Rich Presence timestamp. See [resumeReading] for the other half of this.
         */
        fun scheduleStop(context: Context, mangaId: Long?) {
            pendingStopJob?.cancel()
            pendingStopMangaId = mangaId
            pendingStopJob = debounceScope.launch {
                delay(STOP_DEBOUNCE_MS)
                pendingStopMangaId = null
                stop(context)
            }
        }

        /**
         * Call from the reader's onResume(), instead of unconditionally calling [start]:
         * ```
         * if (!DiscordRPCService.resumeReading(manga.id)) {
         *     DiscordRPCService.start(context, sourceId = manga.source)
         * }
         * ```
         * Returns true when [mangaId] matches a pending [scheduleStop] call from the same
         * manga - meaning the grace period already covered this resume, so the existing
         * connection and timestamp are left completely untouched and the caller should skip
         * [start]. Otherwise (different manga, or nothing was pending) cancels any stale
         * pending stop and returns false, so the caller proceeds with [start] as normal -
         * which still won't actually reconnect if the service never stopped, but will restart
         * the timestamp for the new manga.
         */
        fun resumeReading(mangaId: Long?): Boolean {
            val sameMangaPending = mangaId != null && mangaId == pendingStopMangaId && pendingStopJob != null
            pendingStopJob?.cancel()
            pendingStopJob = null
            pendingStopMangaId = null
            return sameMangaPending
        }

        fun restart(
            context: Context,
            sourceId: Long? = null,
            connectionsManager: ConnectionsManager = Injekt.get(),
            connectionsPreferences: ConnectionsPreferences = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            val account = connectionsManager.discord.getAccounts().find { it.isActive }
            val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
            val missingAccount = !connectionsPreferences.enableDiscordRPC().get() ||
                account == null || token.isBlank()
            if (missingAccount) {
                if (account == null || token.isBlank()) connectionsPreferences.enableDiscordRPC().set(false)
                return
            }
            if (respectsIncognito(account, sourceId, preferences, extensionManager)) return

            try {
                context.startForegroundService(
                    Intent(context, DiscordRPCService::class.java).apply { action = ACTION_RESTART },
                )
            } catch (e: Exception) {
                Logger.e(e) { "Failed to restart Discord RPC service" }
            }
        }

        /**
         * Updates the Rich Presence: Activity "Watching", Details [title],
         * State "Chapter [currentChapter] of [totalChapters]", Timestamp elapsed since the
         * service started. The large image is the manga's own cover ([coverUrl]); the app
         * icon is only ever attached as a small badge on top of it, and only when the active
         * account's "Show app icon" setting is enabled. No-ops while [sourceId] is under
         * Incognito Mode (global or per-extension) and the active account's "Respect Incognito
         * Mode" is on, or if there's no active account.
         */
        fun setReadingActivity(
            context: Context,
            title: String,
            currentChapter: Int,
            totalChapters: Int,
            coverUrl: String?,
            sourceId: Long? = null,
            connectionsManager: ConnectionsManager = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            if (!isConnected()) return
            val account = connectionsManager.discord.getAccounts().find { it.isActive } ?: return
            if (respectsIncognito(account, sourceId, preferences, extensionManager)) return
            launchIO {
                val appName = context.getString(MR.strings.app_name)
                val showAppIcon = account.showAppIcon
                val name = account.customActivityName.ifBlank { appName }
                val state = context.getString(MR.strings.chapter_x_of_y, currentChapter, totalChapters)

                if (usingSdk) {
                    // The native SDK takes image URLs directly rather than the pre-resolved
                    // asset IDs the Gateway-based RPCExternalAsset flow needs - unverified
                    // against a real device/account yet, worth double-checking that Discord
                    // actually renders a bare https cover URL as the large image here.
                    val smallImage = if (coverUrl != null && showAppIcon) RICH_PRESENCE_APP_ICON_URL else null
                    DiscordRpcManager.setActivity(
                        DiscordNativeActivity(
                            name = name,
                            details = title,
                            state = state,
                            startTimestamp = since,
                            largeImage = coverUrl,
                            largeText = title,
                            smallImage = smallImage,
                            smallText = smallImage?.let { appName },
                        ),
                    )
                } else {
                    val activeRpc = rpc ?: return@launchIO
                    val largeImage = coverUrl?.let { activeRpc.resolveAsset(it) }
                    val smallImage = if (largeImage != null && showAppIcon) {
                        activeRpc.resolveAsset(RICH_PRESENCE_APP_ICON_URL)
                    } else {
                        null
                    }
                    activeRpc.updateRPC(
                        activity = Activity(
                            name = name,
                            details = title,
                            state = state,
                            type = ActivityType.WATCHING.value,
                            timestamps = Activity.Timestamps(start = since),
                            assets = largeImage?.let {
                                Activity.Assets(
                                    largeImage = it,
                                    largeText = title,
                                    smallImage = smallImage,
                                    smallText = smallImage?.let { appName },
                                )
                            },
                        ),
                        since = since,
                    )
                }
            }
        }
    }
}
