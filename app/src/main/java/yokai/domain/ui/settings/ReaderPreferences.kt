package yokai.domain.ui.settings

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import yokai.i18n.MR

class ReaderPreferences(private val preferenceStore: PreferenceStore) {
    fun cutoutShort() = preferenceStore.getBoolean("cutout_short", true)

    fun pagerCutoutBehavior() = preferenceStore.getEnum(PreferenceKeys.pagerCutoutBehavior, CutoutBehaviour.IGNORE)

    fun landscapeCutoutBehavior() = preferenceStore.getEnum("landscape_cutout_behavior", LandscapeCutoutBehaviour.HIDE)

    enum class CutoutBehaviour(val titleResId: StringResource) {
        HIDE(MR.strings.pad_cutout_areas), // Similar to CUTOUT_MODE_NEVER / J2K's pad
        SHOW(MR.strings.start_past_cutout), // Similar to CUTOUT_MODE_SHORT_EDGES / J2K's start past
        IGNORE(MR.strings.cutout_ignore), // Similar to CUTOUT_MODE_DEFAULT / J2K's ignore
        ;

        companion object {
            fun migrate(oldValue: Int) =
                when (oldValue) {
                    PagerConfig.CUTOUT_PAD -> CutoutBehaviour.HIDE
                    PagerConfig.CUTOUT_IGNORE -> CutoutBehaviour.IGNORE
                    else -> CutoutBehaviour.SHOW
                }
        }
    }

    enum class LandscapeCutoutBehaviour(val titleResId: StringResource) {
        HIDE(MR.strings.pad_cutout_areas), // Similar to CUTOUT_MODE_NEVER / J2K's pad
        DEFAULT(MR.strings.cutout_ignore), // Similar to CUTOUT_MODE_SHORT_EDGES / J2K's ignore
        ;

        companion object {
            fun migrate(oldValue: Int) =
                when (oldValue) {
                    0 -> LandscapeCutoutBehaviour.HIDE
                    else -> LandscapeCutoutBehaviour.DEFAULT
                }
        }
    }

    fun webtoonDoubleTapZoomEnabled() = preferenceStore.getBoolean("pref_enable_double_tap_zoom_webtoon", true)

    fun debugMode() = preferenceStore.getBoolean("pref_enable_reader_debug_mode", BuildConfig.DEBUG)

    fun autoScrollSpeed() = preferenceStore.getInt("reader_auto_scroll_speed", 50)

    /** Daily reading limit in minutes, 0 means the break reminder is off. */
    fun maxReadTime() = preferenceStore.getInt("reader_max_read_time", 0)

    /** Minutes after midnight the sleep reminder starts at, -1 means the sleep reminder is off. */
    fun sleepReminderTime() = preferenceStore.getInt("reader_sleep_reminder_time", -1)

    fun readingReminderDay() = preferenceStore.getString("reader_reminder_day", "")

    fun readTodayMs() = preferenceStore.getLong("reader_read_today_ms", 0L)

    fun readLimitExtraMs() = preferenceStore.getLong("reader_read_limit_extra_ms", 0L)

    fun breakReminderDismissed() = preferenceStore.getBoolean("reader_break_reminder_dismissed", false)

    fun sleepSnoozeUntil() = preferenceStore.getLong("reader_sleep_snooze_until", 0L)

    fun sleepDismissedNight() = preferenceStore.getString("reader_sleep_dismissed_night", "")

    /** The reminder the user chose to stop reading for, shown again as a dialog on the next app launch. */
    fun pendingStopReminder() = preferenceStore.getString("reader_pending_stop_reminder", "")
}
