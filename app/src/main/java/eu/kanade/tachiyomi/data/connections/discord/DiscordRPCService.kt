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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Foreground service that keeps a Discord Gateway connection open and pushes Rich Presence
 * updates while the user is reading. Entirely optional: [start] no-ops unless the user has
 * enabled it in settings, provided an account token, and isn't reading a source under
 * Incognito Mode (global or per-extension) - and stopping it (or disabling the setting)
 * never affects normal app operation.
 */
class DiscordRPCService : Service() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Starting Discord RPC service" }

        val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
        if (token.isBlank()) {
            Logger.w { "Discord RPC disabled due to missing token" }
            connectionsPreferences.enableDiscordRPC().set(false)
            stopSelf()
            return
        }

        startForeground(Notifications.ID_DISCORD_RPC, notification())
        rpc = DiscordRPC(token)
    }

    override fun onDestroy() {
        rpc?.closeRPC()
        rpc = null
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
        rpc?.closeRPC()
        rpc = null

        val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
        if (token.isBlank()) {
            Logger.w { "Discord RPC restart failed due to missing token" }
            stopSelf()
            return
        }

        rpc = DiscordRPC(token)
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
        private var since = 0L

        private const val ACTION_RESTART = "eu.kanade.tachiyomi.DISCORD_RPC_RESTART"
        private const val ACTION_STOP = "eu.kanade.tachiyomi.DISCORD_RPC_STOP"

        fun start(
            context: Context,
            sourceId: Long? = null,
            connectionsManager: ConnectionsManager = Injekt.get(),
            connectionsPreferences: ConnectionsPreferences = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            if (!connectionsPreferences.enableDiscordRPC().get()) return
            if (isIncognitoModeForSource(sourceId, preferences, extensionManager)) return

            val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
            if (token.isBlank()) {
                Logger.w { "Discord RPC not started due to missing token" }
                connectionsPreferences.enableDiscordRPC().set(false)
                return
            }

            if (rpc == null) {
                since = System.currentTimeMillis()
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

        fun restart(
            context: Context,
            sourceId: Long? = null,
            connectionsManager: ConnectionsManager = Injekt.get(),
            connectionsPreferences: ConnectionsPreferences = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
            val blocked = !connectionsPreferences.enableDiscordRPC().get() ||
                isIncognitoModeForSource(sourceId, preferences, extensionManager) ||
                token.isBlank()
            if (blocked) {
                if (token.isBlank()) connectionsPreferences.enableDiscordRPC().set(false)
                return
            }

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
         * icon is only ever attached as a small badge on top of it, and only when "Show app
         * icon" is enabled. No-ops while [sourceId] is under Incognito Mode (global or
         * per-extension).
         */
        fun setReadingActivity(
            context: Context,
            title: String,
            currentChapter: Int,
            totalChapters: Int,
            coverUrl: String?,
            sourceId: Long? = null,
            connectionsPreferences: ConnectionsPreferences = Injekt.get(),
            preferences: PreferencesHelper = Injekt.get(),
            extensionManager: ExtensionManager = Injekt.get(),
        ) {
            val activeRpc = rpc ?: return
            if (isIncognitoModeForSource(sourceId, preferences, extensionManager)) return
            launchIO {
                val appName = context.getString(MR.strings.app_name)
                val customName = connectionsPreferences.discordCustomActivityName().get()
                val showAppIcon = connectionsPreferences.discordShowAppIcon().get()
                val largeImage = coverUrl?.let { activeRpc.resolveAsset(it) }
                val smallImage = if (largeImage != null && showAppIcon) {
                    activeRpc.resolveAsset(RICH_PRESENCE_APP_ICON_URL)
                } else {
                    null
                }
                activeRpc.updateRPC(
                    activity = Activity(
                        name = customName.ifBlank { appName },
                        details = title,
                        state = context.getString(MR.strings.chapter_x_of_y, currentChapter, totalChapters),
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
