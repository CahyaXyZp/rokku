package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter

/**
 * Only main chapters with a positive number count as tracker progress. Additional chapters
 * ("1.1", "151.5"), chapter 0 and unrecognized numbers are never sent to a tracker.
 */
fun isTrackerProgressNumber(chapterNumber: Float): Boolean =
    chapterNumber > 0f && !isAdditionalChapterNumber(chapterNumber)

val Chapter.isTrackerProgress: Boolean
    get() = isTrackerProgressNumber(chapter_number)

/** The highest chapter that counts as tracker progress, or null when there is none. */
fun Iterable<Chapter>.latestTrackerChapter(): Chapter? =
    filter { it.isTrackerProgress }.maxByOrNull { it.chapter_number }

/** Tracker progress of the read chapters, 0 when no main chapter is read. */
fun Iterable<Chapter>.readTrackerProgress(): Float =
    filter { it.read }.latestTrackerChapter()?.chapter_number ?: 0f

/** Unread main chapters that a remote progress of [remoteProgress] already covers. */
fun Iterable<Chapter>.unreadMainChaptersUpTo(remoteProgress: Float): List<Chapter> {
    if (remoteProgress <= 0f) return emptyList()
    return filter {
        !it.read && it.isRecognizedNumber && it.isMainChapter && it.chapter_number <= remoteProgress
    }
}
