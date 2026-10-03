package yokai.presentation.manga.notes

import eu.kanade.tachiyomi.data.database.models.Chapter

internal const val CHAPTER_TAG_SCHEME = "chapter://"

internal fun formatChapterNumber(number: Float): String =
    if (number % 1f == 0f) number.toInt().toString() else number.toString()

/**
 * A chapter tag is a plain markdown link. It stores the chapter number instead of the database id,
 * so it still points to the right chapter after a backup is restored.
 */
internal fun chapterTag(label: String, number: Float): String {
    val safeLabel = label
        .replace('[', '(')
        .replace(']', ')')
        .replace('\n', ' ')
        .trim()
        .ifEmpty { formatChapterNumber(number) }
    return "[$safeLabel]($CHAPTER_TAG_SCHEME${formatChapterNumber(number)})"
}

internal fun chapterNumberOfLink(link: String): Float? =
    if (link.startsWith(CHAPTER_TAG_SCHEME)) {
        link.removePrefix(CHAPTER_TAG_SCHEME).toFloatOrNull()
    } else {
        null
    }

internal fun findTaggedChapter(chapters: List<Chapter>, number: Float): Chapter? =
    chapters.firstOrNull { it.chapter_number == number }
