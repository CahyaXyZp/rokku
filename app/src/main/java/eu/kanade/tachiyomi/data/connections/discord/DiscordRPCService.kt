// Adapted from Komikku (originally from Animiru) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.connections.service.ConnectionsPreferences
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Keeps Discord Rich Presence updated while the user is reading, over the official Social SDK
 * ([DiscordRpcManager], via JNI). Runs inside the app process without a foreground service (and
 * so without a notification): Android may end the connection once the app has been in the
 * background for a while.
 * Entirely optional: [start] no-ops unless the user has enabled it in settings, is logged in,
 * and (when "Respect Incognito Mode" is on) isn't reading a source under Incognito Mode (global
 * or per-extension) - and stopping it (or disabling the setting) never affects normal app
 * operation.
 *
 * Doesn't stop the instant reading pauses either: see [scheduleStop]/[resumeReading].
 */
object DiscordRPCService {

    @Volatile
    private var connected = false

    private var since = 0L

    // Fixed grace period - see scheduleStop()/resumeReading().
    private const val STOP_DEBOUNCE_MS = 5 * 60 * 1000L

    private const val MAX_FIELD_LENGTH = 128

    // Outlives any single reading session, since the whole point of scheduleStop() is for the
    // delayed stop to keep counting down across the reader activity's
    // onPause/onResume/onDestroy, not just within one of them.
    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingStopJob: Job? = null
    private var pendingStopMangaId: Long? = null

    private class Button(val label: String, val url: String)

    @Synchronized
    private fun connect(token: String) {
        Logger.i { "Starting Discord RPC" }
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init()
        }
        DiscordRpcManager.reconnectWithToken(token)
        connected = true
    }

    @Synchronized
    private fun disconnect(restoreStatus: Boolean = false) {
        if (connected) {
            DiscordRpcManager.disconnect(restoreStatus)
        }
        connected = false
    }

    private fun respectsIncognito(
        account: DiscordAccount,
        sourceId: Long?,
        preferences: PreferencesHelper,
        extensionManager: ExtensionManager,
    ): Boolean {
        if (!account.respectIncognito) return false
        return isIncognitoModeForSource(sourceId, preferences, extensionManager)
    }

    /**
     * Resolves both of [account]'s configured buttons, substituting [TEMPLATE_CHAPTER_URL]
     * with [chapterUrl]. A button is dropped entirely if it isn't switched on
     * (button1Enabled/button2Enabled), or if its label or resolved url end up blank
     * (including a `{chapter_url}` placeholder left unresolved because [chapterUrl] is
     * null) - Discord rejects buttons with an empty label/url.
     */
    private fun resolveButtons(account: DiscordAccount, chapterUrl: String?): List<Button> {
        fun resolve(enabled: Boolean, label: String, url: String): Button? {
            if (!enabled) return null
            val resolvedUrl = url.replace(TEMPLATE_CHAPTER_URL, chapterUrl.orEmpty())
            if (label.isBlank() || resolvedUrl.isBlank()) return null
            return Button(label, resolvedUrl)
        }
        return listOfNotNull(
            resolve(account.button1Enabled, account.button1Label, account.button1Url),
            resolve(account.button2Enabled, account.button2Label, account.button2Url),
        )
    }

    private fun sdkOnlineStatus(status: String) = when (status) {
        DiscordOnlineStatus.IDLE -> DiscordRpcManager.OnlineStatus.Idle
        DiscordOnlineStatus.DND -> DiscordRpcManager.OnlineStatus.DoNotDisturb
        else -> DiscordRpcManager.OnlineStatus.Online
    }

    // Discord rejects blank, 1-character, and over-128-character details/state.
    private fun sanitizeField(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.length < 2 -> "$trimmed "
            else -> trimmed.take(MAX_FIELD_LENGTH)
        }
    }

    private fun remoteImageUrl(url: String?): String? =
        url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    // Only the extension-level NSFW flag is available; there is no per-source or per-manga one.
    private fun isNsfwSource(sourceId: Long?, extensionManager: ExtensionManager): Boolean {
        if (sourceId == null) return false
        val pkgName = extensionManager.getPackageName(sourceId) ?: return false
        return extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }?.isNsfw == true
    }

    /**
     * Picks the large image for the presence. Covers Discord can't fetch itself (a custom cover,
     * or a local file path / content:// URI) are only uploaded to a public host when the user
     * turned that on and [sourceId] doesn't belong to an extension flagged as NSFW. Otherwise
     * only http(s) covers are shown.
     */
    private suspend fun resolveCover(
        context: Context,
        coverUrl: String?,
        mangaId: Long?,
        sourceId: Long?,
        connectionsPreferences: ConnectionsPreferences,
        extensionManager: ExtensionManager,
    ): String? {
        val canUpload = connectionsPreferences.discordUploadLocalCovers().get() &&
            !isNsfwSource(sourceId, extensionManager)

        if (canUpload && mangaId != null) {
            val customCover = Injekt.get<CoverCache>().getCustomCoverFile(mangaId)
            if (customCover.exists()) {
                DiscordImageUploader.resolveUrl(customCover)?.let { return it }
            }
        }

        remoteImageUrl(coverUrl)?.let { return it }
        if (!canUpload || coverUrl.isNullOrBlank()) return null
        return DiscordImageUploader.resolveUrl(context, coverUrl)
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

        val account = connectionsManager.discord.getAccount()
        if (account == null || account.token.isBlank()) {
            Logger.w { "Discord RPC not started due to missing account/token" }
            connectionsPreferences.enableDiscordRPC().set(false)
            return
        }
        if (respectsIncognito(account, sourceId, preferences, extensionManager)) return

        // Always restarts the elapsed-time counter, even when the connection is already up:
        // scheduleStop()/resumeReading() can leave it running across a switch to a different
        // manga, and that switch should still restart the timestamp.
        since = System.currentTimeMillis()
        if (!connected) {
            connect(account.token)
        }
    }

    fun stop(context: Context) {
        Logger.i { "Stopping Discord RPC" }
        disconnect(restoreStatus = true)
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
     * which still won't actually reconnect if the connection never dropped, but will
     * restart the timestamp for the new manga.
     */
    fun resumeReading(mangaId: Long?): Boolean {
        val sameMangaPending = mangaId != null && mangaId == pendingStopMangaId && pendingStopJob != null
        pendingStopJob?.cancel()
        pendingStopJob = null
        pendingStopMangaId = null
        return sameMangaPending
    }

    /**
     * Drops the current connection and reconnects with the saved account, e.g. after its
     * settings change. No-ops when Rich Presence is disabled.
     */
    fun restart(
        context: Context,
        sourceId: Long? = null,
        connectionsManager: ConnectionsManager = Injekt.get(),
        connectionsPreferences: ConnectionsPreferences = Injekt.get(),
        preferences: PreferencesHelper = Injekt.get(),
        extensionManager: ExtensionManager = Injekt.get(),
    ) {
        if (!connectionsPreferences.enableDiscordRPC().get()) return

        val account = connectionsManager.discord.getAccount()
        if (account == null || account.token.isBlank()) {
            connectionsPreferences.enableDiscordRPC().set(false)
            return
        }
        if (respectsIncognito(account, sourceId, preferences, extensionManager)) return

        disconnect()
        connect(account.token)
    }

    /**
     * Updates the Rich Presence using the account's own settings: a state built from its
     * `{chapter}`/`{total}` template, its custom online status, its two configured buttons
     * (each resolving a `{chapter_url}` placeholder against [chapterUrl], dropped if still
     * blank after that), and the large/small images. The activity type is always Watching.
     * No-ops while [sourceId] is under Incognito Mode (global or per-extension) and "Respect
     * Incognito Mode" is on, or if no account is saved. [mangaId] is only needed to find a
     * custom cover to upload.
     */
    fun setReadingActivity(
        context: Context,
        title: String,
        currentChapter: Int,
        totalChapters: Int,
        coverUrl: String?,
        sourceId: Long? = null,
        chapterUrl: String? = null,
        mangaId: Long? = null,
        connectionsManager: ConnectionsManager = Injekt.get(),
        connectionsPreferences: ConnectionsPreferences = Injekt.get(),
        preferences: PreferencesHelper = Injekt.get(),
        extensionManager: ExtensionManager = Injekt.get(),
    ) {
        if (!connected) return
        val account = connectionsManager.discord.getAccount() ?: return
        if (respectsIncognito(account, sourceId, preferences, extensionManager)) return
        launchIO {
            val appName = context.getString(MR.strings.app_name)
            val name = account.customActivityName.ifBlank { appName }
            val details = sanitizeField(title)
            val state = sanitizeField(
                account.activityStateTemplate
                    .replace(TEMPLATE_CHAPTER, currentChapter.toString())
                    .replace(TEMPLATE_TOTAL, totalChapters.toString()),
            )
            val cover = resolveCover(context, coverUrl, mangaId, sourceId, connectionsPreferences, extensionManager)
            val smallImage = if (cover != null && account.showAppIcon) RICH_PRESENCE_APP_ICON_URL else null
            val buttons = resolveButtons(account, chapterUrl)

            DiscordRpcManager.setOnlineStatus(sdkOnlineStatus(account.onlineStatus))
            DiscordRpcManager.setActivity(
                DiscordNativeActivity(
                    name = name,
                    details = details,
                    state = state,
                    startTimestamp = since,
                    largeImage = cover,
                    smallImage = smallImage,
                    smallText = smallImage?.let { appName },
                    button1Label = buttons.getOrNull(0)?.label,
                    button1Url = buttons.getOrNull(0)?.url,
                    button2Label = buttons.getOrNull(1)?.label,
                    button2Url = buttons.getOrNull(1)?.url,
                ),
            )
        }
    }
}
