package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChapterSortAdditionalChapterTest {

    private val preferences: PreferencesHelper = mockk()
    private val downloadManager: DownloadManager = mockk()
    private val chapterFilter = ChapterFilter(preferences, downloadManager)

    private val manga = MangaImpl().apply {
        setFilterToLocal()
        setChapterOrder(Manga.CHAPTER_SORTING_NUMBER, Manga.CHAPTER_SORT_ASC)
    }

    private val sort = ChapterSort(manga, chapterFilter, preferences)

    // Ids are the position in the list so results can be compared by id.
    private fun chapters(vararg entries: Pair<Float, Boolean>) = entries.mapIndexed { index, (number, read) ->
        ChapterImpl().apply {
            this.id = index.toLong()
            this.url = "chapter-$index"
            this.name = "Chapter $number"
            this.chapter_number = number
            this.read = read
        }
    }

    private fun numberOfNextUnread(vararg entries: Pair<Float, Boolean>): Float? =
        sort.getNextUnreadChapter(chapters(*entries), andFiltered = false)?.chapter_number

    @Test
    fun `unread additional chapter does not block the next main chapter`() {
        val next = numberOfNextUnread(
            1f to true,
            1.1f to false,
            1.5f to false,
            2f to false,
            2.1f to false,
            3f to false,
        )

        assertEquals(2f, next)
    }

    @Test
    fun `main progression goes one main chapter to the next`() {
        val next = numberOfNextUnread(
            1f to true,
            1.1f to true,
            2f to true,
            2.1f to false,
            3f to false,
        )

        assertEquals(3f, next)
    }

    @Test
    fun `unread main chapter is picked before an earlier unread additional one`() {
        val next = numberOfNextUnread(
            1f to false,
            1.1f to false,
            2f to false,
        )

        assertEquals(1f, next)
    }

    @Test
    fun `falls back to the first unread additional chapter when every main chapter is read`() {
        val next = numberOfNextUnread(
            1f to true,
            1.1f to false,
            2f to true,
            2.1f to false,
            3f to true,
        )

        assertEquals(1.1f, next)
    }

    @Test
    fun `returns null when every chapter including additional ones is read`() {
        val next = numberOfNextUnread(
            1f to true,
            1.1f to true,
            2f to true,
        )

        assertNull(next)
    }

    @Test
    fun `reading an additional chapter does not read its parent`() {
        val next = numberOfNextUnread(
            1f to false,
            1.1f to true,
            2f to false,
        )

        assertEquals(1f, next)
    }

    @Test
    fun `reading a main chapter does not read its additional chapters`() {
        val list = chapters(
            1f to true,
            1.1f to false,
            2f to true,
        )

        val additional = list.first { it.chapter_number == 1.1f }

        assertEquals(false, additional.read)
    }

    @Test
    fun `additional chapters stay in the sorted list in natural order`() {
        val list = chapters(
            2f to false,
            1.5f to false,
            1f to false,
            1.1f to false,
        )

        val sorted = sort.getChaptersSorted(list, andFiltered = false)

        assertEquals(listOf(1f, 1.1f, 1.5f, 2f), sorted.map { it.chapter_number })
    }

    @Test
    fun `getNextChapter still follows natural order and includes additional chapters`() {
        val list = chapters(
            1f to true,
            1.1f to false,
            2f to false,
        )

        val next = sort.getNextChapter(list, andFiltered = false)

        assertEquals(1f, next?.chapter_number)
    }
}
