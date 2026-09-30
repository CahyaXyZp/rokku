package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.Chapter
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * The chapter count shown in the manga header: "153 chapters", or
 * "153 chapters · 3 additional chapters" when the list has additional chapters.
 */
fun Context.chapterCountText(chapters: Iterable<Chapter>): String {
    val main = chapters.mainChapterCount()
    val additional = chapters.additionalChapterCount()
    val mainText = getString(MR.plurals.chapters_plural, main, main)
    if (additional == 0) return mainText

    val additionalText = getString(MR.plurals.additional_chapters_plural, additional, additional)
    return getString(MR.strings.chapter_counts_joined, mainText, additionalText)
}
