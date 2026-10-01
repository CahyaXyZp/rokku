package eu.kanade.tachiyomi.ui.reader

import android.transition.TransitionManager
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.ui.settings.ReaderPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import kotlin.math.roundToInt

/**
 * Drives the auto scroll panel under the reader toolbar. Long strip viewers scroll a little every
 * frame, pager viewers turn the page at an interval. Both pause while the menu is open and stop at
 * the end of the last chapter.
 */
class ReaderAutoScroller(private val activity: ReaderActivity) {

    private val readerPreferences: ReaderPreferences by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    private var job: Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun bind() {
        with(activity.binding) {
            val speed = readerPreferences.autoScrollSpeed().get().coerceIn(MIN_SPEED, MAX_SPEED)
            autoScrollSpeedSlider.value = speed.toFloat()
            autoScrollSpeedValue.text = speed.toString()
            autoScrollSpeedSlider.addOnChangeListener { _, value, fromUser ->
                autoScrollSpeedValue.text = value.toInt().toString()
                if (fromUser) readerPreferences.autoScrollSpeed().set(value.toInt())
            }
            autoScrollHandle.setOnClickListener { setPanelExpanded(!autoScrollPanel.isVisible) }
            autoScrollButton.setOnClickListener { if (isRunning) stop() else start() }
        }
        updateButton()
    }

    fun start() {
        if (isRunning) return
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = activity.scope.launch { run() }
        updateButton()
        setPanelExpanded(false)
        activity.hideMenu()
    }

    fun stop() {
        if (job == null) return
        job?.cancel()
        job = null
        if (!preferences.keepScreenOn().get()) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        updateButton()
    }

    private suspend fun run() {
        val density = activity.resources.displayMetrics.density
        var lastFrame = 0L
        var distance = 0f
        var waited = 0L
        while (true) {
            val frame = awaitFrame()
            val elapsed = if (lastFrame == 0L) 0L else frame - lastFrame
            lastFrame = frame

            val viewer = activity.viewer
            if (isAtEnd(viewer)) {
                stop()
                return
            }
            if (activity.menuVisible || activity.isLoading) {
                distance = 0f
                waited = 0L
                continue
            }

            val speed = readerPreferences.autoScrollSpeed().get().coerceIn(MIN_SPEED, MAX_SPEED)
            when (viewer) {
                is WebtoonViewer -> {
                    if (viewer.recycler.scrollState != RecyclerView.SCROLL_STATE_IDLE) continue
                    distance += speed * DP_PER_SECOND_PER_STEP * density * (elapsed / NANOS_PER_SECOND)
                    val step = distance.toInt()
                    if (step > 0) {
                        distance -= step
                        viewer.recycler.scrollBy(0, step)
                    }
                }

                is PagerViewer -> {
                    waited += elapsed
                    if (waited >= (PAGE_INTERVAL_SLOWEST_MS - speed * PAGE_INTERVAL_STEP_MS) * NANOS_PER_MILLI) {
                        waited = 0L
                        viewer.moveToNext()
                    }
                }

                else -> Unit
            }
        }
    }

    private fun isAtEnd(viewer: BaseViewer?): Boolean {
        val chapters = activity.viewModel.state.value.viewerChapters ?: return false
        if (chapters.nextChapter != null) return false
        return when (viewer) {
            is WebtoonViewer -> !viewer.recycler.canScrollVertically(1)
            is PagerViewer -> {
                val pages = chapters.currChapter.pages ?: return false
                activity.binding.readerNav.pageSeekbar.value.roundToInt() >= pages.lastIndex
            }

            else -> false
        }
    }

    private fun setPanelExpanded(expanded: Boolean) {
        with(activity.binding) {
            if (autoScrollPanel.isVisible == expanded) return
            TransitionManager.beginDelayedTransition(appBar)
            autoScrollPanel.isVisible = expanded
            autoScrollHandle.animate().rotation(if (expanded) 180f else 0f)
        }
    }

    private fun updateButton() {
        activity.binding.autoScrollButton.text = activity.getString(
            if (isRunning) MR.strings.auto_scroll_stop else MR.strings.auto_scroll_start,
        )
    }

    private companion object {
        const val MIN_SPEED = 1
        const val MAX_SPEED = 100
        const val DP_PER_SECOND_PER_STEP = 3f
        const val PAGE_INTERVAL_SLOWEST_MS = 16_000L
        const val PAGE_INTERVAL_STEP_MS = 150L
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
