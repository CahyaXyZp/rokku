package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdditionalChapterTest {

    private fun chapter(number: Float): Chapter = ChapterImpl().apply { chapter_number = number }

    @Test
    fun `whole numbers are main chapters`() {
        listOf(1f, 2f, 3f, 151f, 1000f).forEach {
            assertFalse(isAdditionalChapterNumber(it), "$it")
            assertTrue(chapter(it).isMainChapter, "$it")
        }
    }

    @Test
    fun `decimal numbers are additional chapters`() {
        listOf(1.1f, 1.2f, 1.5f, 1.9f, 151.5f).forEach {
            assertTrue(isAdditionalChapterNumber(it), "$it")
            assertTrue(chapter(it).isAdditionalChapter, "$it")
        }
    }

    @Test
    fun `additional chapter belongs to its integer parent`() {
        assertEquals(1, parentOfAdditionalChapter(1.1f))
        assertEquals(1, parentOfAdditionalChapter(1.5f))
        assertEquals(1, parentOfAdditionalChapter(1.9f))
        assertEquals(151, parentOfAdditionalChapter(151.5f))
        assertEquals(1, chapter(1.5f).parentChapter)
    }

    @Test
    fun `main chapters have no parent`() {
        assertNull(parentOfAdditionalChapter(1f))
        assertNull(parentOfAdditionalChapter(151f))
        assertNull(chapter(2f).parentChapter)
    }

    @Test
    fun `zero is a main chapter`() {
        assertFalse(isAdditionalChapterNumber(0f))
        assertTrue(chapter(0f).isMainChapter)
    }

    @Test
    fun `unrecognized and negative numbers are main chapters`() {
        listOf(-1f, -0.5f, -2f).forEach {
            assertFalse(isAdditionalChapterNumber(it), "$it")
            assertNull(parentOfAdditionalChapter(it), "$it")
        }
    }

    @Test
    fun `non finite numbers are main chapters`() {
        assertFalse(isAdditionalChapterNumber(Float.NaN))
        assertFalse(isAdditionalChapterNumber(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `classification does not change the stored number`() {
        val chapter = chapter(1.5f)

        chapter.isAdditionalChapter
        chapter.parentChapter

        assertEquals(1.5f, chapter.chapter_number)
    }

    @Test
    fun `counts split main and additional chapters`() {
        val chapters = listOf(1f, 1.1f, 1.5f, 2f, 2.1f, 3f, -1f).map(::chapter)

        assertEquals(4, chapters.mainChapterCount())
        assertEquals(3, chapters.additionalChapterCount())
    }

    @Test
    fun `counts are zero for an empty list`() {
        assertEquals(0, emptyList<Chapter>().mainChapterCount())
        assertEquals(0, emptyList<Chapter>().additionalChapterCount())
    }

    @Test
    fun `list without additional chapters reports none`() {
        val chapters = listOf(1f, 2f, 3f).map(::chapter)

        assertEquals(3, chapters.mainChapterCount())
        assertEquals(0, chapters.additionalChapterCount())
    }
}
