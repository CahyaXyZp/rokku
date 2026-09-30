package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import kotlin.math.floor

/**
 * Chapters whose number has a fractional part ("1.1", "151.5") are additional chapters that belong
 * to the whole-number chapter in front of the dot. Every other chapter, including ones with an
 * unrecognized (negative) number, is a main chapter.
 *
 * This only reads the stored chapter number, it never changes it.
 */
fun isAdditionalChapterNumber(chapterNumber: Float): Boolean =
    chapterNumber >= 0f && chapterNumber != floor(chapterNumber)

/** The main chapter number an additional chapter belongs to, or null when it isn't one. */
fun parentOfAdditionalChapter(chapterNumber: Float): Int? =
    if (isAdditionalChapterNumber(chapterNumber)) floor(chapterNumber).toInt() else null

val Chapter.isAdditionalChapter: Boolean
    get() = isAdditionalChapterNumber(chapter_number)

val Chapter.isMainChapter: Boolean
    get() = !isAdditionalChapter

/** The main chapter number this chapter belongs to when it is an additional chapter. */
val Chapter.parentChapter: Int?
    get() = parentOfAdditionalChapter(chapter_number)

fun Iterable<Chapter>.mainChapterCount(): Int = count { it.isMainChapter }

fun Iterable<Chapter>.additionalChapterCount(): Int = count { it.isAdditionalChapter }
