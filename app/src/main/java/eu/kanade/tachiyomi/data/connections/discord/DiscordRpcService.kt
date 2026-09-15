package eu.kanade.tachiyomi.data.connections.discord

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Facade the reader talks to. Owns exactly one [DiscordGatewayClient] and decides, based on
 * [DiscordRpcPreferences] and the suppression checks passed in by the caller, whether a call
 * actually reaches the gateway.
 *
 * Deliberately dumb about *why* something is suppressed - incognito/18+/account-selection logic
 * lives in Agent 3's integration layer (ReaderActivity + a helper class), which calls
 * [onChapterOpened] / [onChapterClosed] with the answer already worked out. Keeping that logic
 * out of this class means Agent 1 and Agent 3's work don't need to land in the same PR to be
 * individually testable.
 *
 * Not yet wired to anything: no caller exists until Agent 3's ReaderActivity hook and Agent 2's
 * token storage are in. See docs/discord-rpc/STATUS.md.
 */
class DiscordRpcService(
    private val preferences: DiscordRpcPreferences,
) {
    // A dedicated client so a slow/hung gateway connection can't be blamed on (or starved by)
    // the app's general-purpose network client. Long read timeout: this is a persistent
    // WebSocket, not a request/response call.
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val gateway = DiscordGatewayClient(httpClient)

    val connectionState get() = gateway.state

    /**
     * Call when the reader opens a chapter and presence should be shown.
     *
     * @param token the active account's Discord token (Agent 2). Passing null is treated as
     *   "no account configured" and is a no-op, same as [preferences.enabled] being false.
     * @param suppressed true if the caller has already determined this update should be
     *   withheld (incognito, 18+, etc.) - checked here as a final guard, not the primary gate.
     */
    fun onChapterOpened(
        token: String?,
        mangaTitle: String,
        chapterLabel: String,
        coverAssetUrl: String?,
        suppressed: Boolean,
    ) {
        if (!preferences.enabled().get() || token.isNullOrBlank() || suppressed) return

        val presence = PresenceUpdate(
            activities = listOf(
                Activity.forReading(
                    mangaTitle = mangaTitle,
                    chapterLabel = chapterLabel,
                    customText = preferences.customActivityText().get(),
                    startedAt = System.currentTimeMillis(),
                    coverAssetUrl = coverAssetUrl.takeIf { preferences.showCoverArt().get() },
                ),
            ),
        )

        when (gateway.state.value) {
            is GatewayState.Connected -> gateway.updatePresence(presence)
            else -> gateway.connect(token, presence)
        }
    }

    /** Call when the reader closes, or suppression conditions newly apply mid-session. */
    fun onChapterClosed() {
        gateway.disconnect()
    }
}
