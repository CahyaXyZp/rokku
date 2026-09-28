// Adapted from Komikku (originally from Animiru/KizzyRPC/saikou-app) for Rokku
package eu.kanade.tachiyomi.data.connections.discord

// URL of the app icon shown as the small image on the Rich Presence status when "Show app icon"
// is enabled and a cover is being shown.
internal const val RICH_PRESENCE_APP_ICON_URL =
    "https://raw.githubusercontent.com/CahyaXyZp/rokku/master/.github/readme-images/app-icon.webp"

enum class ActivityType(val value: Int) {
    /** Playing a game. */
    PLAYING(0),

    /** Streaming a game. */
    STREAMING(1),

    /** Listening to music. */
    LISTENING(2),

    /** Watching a video. */
    WATCHING(3),

    /** Competing in a game. */
    COMPETING(5),

    /** Custom activity type, not defined by Discord. */
    CUSTOM(4),
}
