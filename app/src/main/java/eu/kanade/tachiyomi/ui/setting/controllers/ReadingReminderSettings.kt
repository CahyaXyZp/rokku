package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.text.format.DateFormat
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.ui.settings.ReaderPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import java.util.Calendar
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

fun PreferenceScreen.readingReminderCategory(activity: Activity?) {
    val readerPreferences = Injekt.get<ReaderPreferences>()

    preferenceCategory {
        titleRes = MR.strings.reading_reminder

        intListPreference(activity) {
            bindTo(readerPreferences.maxReadTime())
            titleRes = MR.strings.max_read_time
            entryValues = listOf(0, 30, 60, 120, 180, 240, 300, 360)
            entries = entryValues.map { minutes ->
                when {
                    minutes == 0 -> context.getString(MR.strings.disabled)
                    minutes < 60 -> context.getString(MR.strings.reading_reminder_minutes, minutes)
                    else -> context.getString(MR.strings.reading_reminder_hours, minutes / 60)
                }
            }
        }

        intListPreference(activity) {
            bindTo(readerPreferences.sleepReminderTime())
            titleRes = MR.strings.sleep_reminder
            entryValues = listOf(-1, 1200, 1260, 1320, 1380, 0, 60, 120, 180, 240)
            val timeFormat = DateFormat.getTimeFormat(context)
            entries = entryValues.map { minutes ->
                if (minutes < 0) {
                    context.getString(MR.strings.disabled)
                } else {
                    val time = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, minutes / 60)
                        set(Calendar.MINUTE, minutes % 60)
                    }
                    timeFormat.format(time.time)
                }
            }
        }

        infoPreference(MR.strings.reading_reminder_info)
    }
}
