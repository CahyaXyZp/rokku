package eu.kanade.tachiyomi.ui.reader

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.ui.settings.ReaderPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import java.util.Calendar
import java.util.Locale

/**
 * Tracks how long the reader has been open today and blocks it with a banner at the top once the
 * daily reading limit is reached, or when the user is still reading past their sleep time.
 */
class ReaderReadingReminder(private val activity: ReaderActivity) {

    private val readerPreferences: ReaderPreferences by injectLazy()

    private var tickJob: Job? = null
    private var lastTick = 0L
    private var overlay: View? = null
    private var currentReminder: String? = null

    fun start() {
        if (tickJob?.isActive == true) return
        lastTick = SystemClock.elapsedRealtime()
        tickJob = activity.scope.launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        accumulate()
        lastTick = 0L
    }

    private fun accumulate() {
        if (lastTick == 0L) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTick
        lastTick = now
        rollOverDay()
        if (overlay == null && elapsed > 0) {
            val readToday = readerPreferences.readTodayMs()
            readToday.set(readToday.get() + elapsed)
        }
    }

    private fun rollOverDay() {
        val today = dayKey(Calendar.getInstance())
        if (readerPreferences.readingReminderDay().get() == today) return
        readerPreferences.readingReminderDay().set(today)
        readerPreferences.readTodayMs().set(0L)
        readerPreferences.readLimitExtraMs().set(0L)
        readerPreferences.breakReminderDismissed().set(false)
    }

    private fun tick() {
        accumulate()
        if (overlay != null) return

        val night = sleepNight()
        if (night != null &&
            readerPreferences.sleepDismissedNight().get() != night &&
            System.currentTimeMillis() >= readerPreferences.sleepSnoozeUntil().get()
        ) {
            show(SLEEP_PREFIX + night)
            return
        }

        val limit = readerPreferences.maxReadTime().get()
        if (limit > 0 &&
            !readerPreferences.breakReminderDismissed().get() &&
            readerPreferences.readTodayMs().get() >= limit * MS_PER_MINUTE + readerPreferences.readLimitExtraMs().get()
        ) {
            show(BREAK)
        }
    }

    /**
     * The night the current time belongs to when it falls inside the sleep reminder window, which
     * starts at the chosen sleep time and lasts [SLEEP_WINDOW_MINUTES]. Null outside of that window.
     */
    private fun sleepNight(): String? {
        val sleepMinute = readerPreferences.sleepReminderTime().get()
        if (sleepMinute < 0) return null
        val now = Calendar.getInstance()
        val nowMinute = now.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + now.get(Calendar.MINUTE)
        val sinceSleep = (nowMinute - sleepMinute + MINUTES_PER_DAY) % MINUTES_PER_DAY
        if (sinceSleep >= SLEEP_WINDOW_MINUTES) return null
        now.add(Calendar.MINUTE, -sinceSleep)
        return dayKey(now)
    }

    private fun show(reminder: String) {
        if (overlay != null) return
        currentReminder = reminder
        activity.stopAutoScroll()
        activity.hideMenu()

        val surface = activity.getResourceColor(R.attr.colorPrimaryVariant)
        val onSurface = activity.getResourceColor(R.attr.colorOnSurface)
        val accent = activity.getResourceColor(R.attr.colorSecondary)

        val message = TextView(activity).apply {
            text = activity.getString(
                if (reminder.startsWith(SLEEP_PREFIX)) {
                    MR.strings.reading_reminder_sleep_message
                } else {
                    MR.strings.reading_reminder_break_message
                },
            )
            setTextColor(onSurface)
            textSize = 14f
            setPadding(0, 0, 0, 8.dpToPx)
        }

        fun button(label: String, onClick: () -> Unit) = MaterialButton(
            activity,
            null,
            androidx.appcompat.R.attr.borderlessButtonStyle,
        ).apply {
            text = label
            textSize = 13f
            maxLines = 2
            setTextColor(accent)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button(activity.getString(MR.strings.reading_reminder_stop)) { onStopReading() })
            addView(button(activity.getString(MR.strings.reading_reminder_add_time)) { showAddTimeDialog() })
            addView(button(activity.getString(MR.strings.reading_reminder_continue)) { onContinueReading() })
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx, 16.dpToPx, 16.dpToPx, 8.dpToPx)
            addView(message)
            addView(actions)
        }

        val card = MaterialCardView(activity).apply {
            radius = 12.dpToPx.toFloat()
            cardElevation = 6.dpToPx.toFloat()
            setCardBackgroundColor(surface)
            strokeWidth = 1.dpToPx
            strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(onSurface, STROKE_ALPHA)).defaultColor
            addView(content)
        }

        val topInset = activity.window.decorView.rootWindowInsetsCompat
            ?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true
            isFocusable = true
            addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ).apply { setMargins(12.dpToPx, topInset + 12.dpToPx, 12.dpToPx, 0) },
            )
        }
        activity.binding.readerLayout.addView(
            scrim,
            CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        overlay = scrim
    }

    private fun hide() {
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
        currentReminder = null
        lastTick = SystemClock.elapsedRealtime()
    }

    private fun onStopReading() {
        val reminder = currentReminder ?: return
        readerPreferences.pendingStopReminder().set(reminder)
        hide()
        closeApp(activity)
    }

    private fun onContinueReading() {
        val reminder = currentReminder ?: return
        dismiss(readerPreferences, reminder)
        hide()
    }

    private fun showAddTimeDialog() {
        val reminder = currentReminder ?: return
        val minutes = listOf(15, 30, 60)
        val labels = arrayOf(
            activity.getString(MR.strings.reading_reminder_add_15),
            activity.getString(MR.strings.reading_reminder_add_30),
            activity.getString(MR.strings.reading_reminder_add_60),
        )
        activity.materialAlertDialog()
            .setTitle(activity.getString(MR.strings.reading_reminder_add_time_title))
            .setItems(labels) { _, which -> addTime(reminder, minutes[which]) }
            .show()
    }

    private fun addTime(reminder: String, minutes: Int) {
        val added = minutes * MS_PER_MINUTE
        if (reminder.startsWith(SLEEP_PREFIX)) {
            readerPreferences.sleepSnoozeUntil().set(System.currentTimeMillis() + added)
        } else {
            val limit = readerPreferences.maxReadTime().get() * MS_PER_MINUTE
            val extra = (readerPreferences.readTodayMs().get() + added - limit).coerceAtLeast(0L)
            readerPreferences.readLimitExtraMs().set(extra)
        }
        hide()
    }

    companion object {
        const val BREAK = "break"
        const val SLEEP_PREFIX = "sleep:"

        private const val TICK_MS = 5_000L
        private const val MS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
        private const val SLEEP_WINDOW_MINUTES = 6 * MINUTES_PER_HOUR
        private const val KILL_DELAY_MS = 500L
        private const val SCRIM_COLOR = 0x66000000
        private const val STROKE_ALPHA = 31

        private fun dayKey(calendar: Calendar): String = String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )

        /** Marks [reminder] as dismissed so it doesn't show again for the rest of the day or night. */
        fun dismiss(readerPreferences: ReaderPreferences, reminder: String) {
            if (reminder.startsWith(SLEEP_PREFIX)) {
                readerPreferences.sleepDismissedNight().set(reminder.removePrefix(SLEEP_PREFIX))
            } else {
                readerPreferences.breakReminderDismissed().set(true)
            }
        }

        fun closeApp(activity: Activity) {
            activity.finishAffinity()
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, KILL_DELAY_MS)
        }
    }
}

/**
 * Shown on the next app launch after the user chose to stop reading from a reminder.
 */
object ReadingReminderStopDialog {

    private var showing = false

    fun showIfPending(activity: Activity) {
        val readerPreferences = Injekt.get<ReaderPreferences>()
        val pending = readerPreferences.pendingStopReminder().get()
        if (showing || pending.isEmpty()) return
        showing = true
        activity.materialAlertDialog()
            .setTitle(activity.getString(MR.strings.reading_reminder_stop_dialog_title))
            .setMessage(activity.getString(MR.strings.reading_reminder_stop_dialog_message))
            .setCancelable(false)
            .setPositiveButton(activity.getString(MR.strings.reading_reminder_stop)) { _, _ ->
                showing = false
                ReaderReadingReminder.closeApp(activity)
            }
            .setNegativeButton(activity.getString(MR.strings.reading_reminder_continue)) { _, _ ->
                showing = false
                ReaderReadingReminder.dismiss(readerPreferences, pending)
                readerPreferences.pendingStopReminder().set("")
            }
            .show()
    }
}
