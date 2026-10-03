package yokai.presentation.manga.notes

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ChapterTagTest {

    private fun chapter(number: Float, url: String = "chapter-$number"): Chapter =
        ChapterImpl().apply {
            this.url = url
            this.chapter_number = number
        }

    @Test
    fun `whole chapter numbers have no decimals`() {
        assertEquals("12", formatChapterNumber(12f))
        assertEquals("0", formatChapterNumber(0f))
    }

    @Test
    fun `additional chapter numbers keep their decimals`() {
        assertEquals("12.5", formatChapterNumber(12.5f))
        assertEquals("1.1", formatChapterNumber(1.1f))
    }

    @Test
    fun `a tag is a markdown link to the chapter number`() {
        assertEquals("[Chapter 12](chapter://12)", chapterTag("Chapter 12", 12f))
        assertEquals("[Chapter 1.1](chapter://1.1)", chapterTag("Chapter 1.1", 1.1f))
    }

    @Test
    fun `square brackets in a chapter name cannot break the link`() {
        assertEquals("[Ch (1)](chapter://1)", chapterTag("Ch [1]", 1f))
    }

    @Test
    fun `a blank chapter name falls back to the number`() {
        assertEquals("[7](chapter://7)", chapterTag("  ", 7f))
    }

    @Test
    fun `the number is read back from a tag link`() {
        assertEquals(12f, chapterNumberOfLink("chapter://12"))
        assertEquals(1.1f, chapterNumberOfLink("chapter://1.1"))
    }

    @Test
    fun `links that are not chapter tags are ignored`() {
        assertNull(chapterNumberOfLink("https://example.com"))
        assertNull(chapterNumberOfLink("chapter://abc"))
        assertNull(chapterNumberOfLink("chapter://"))
    }

    @Test
    fun `a tag finds its chapter including additional ones`() {
        val chapters = listOf(chapter(1f), chapter(1.1f), chapter(2f))

        assertSame(chapters[1], findTaggedChapter(chapters, 1.1f))
        assertSame(chapters[2], findTaggedChapter(chapters, 2f))
    }

    @Test
    fun `a tag to a missing chapter finds nothing`() {
        assertNull(findTaggedChapter(listOf(chapter(1f), chapter(2f)), 3f))
    }

    @Test
    fun `the first chapter wins when two share a number`() {
        val chapters = listOf(chapter(5f, "scanlator-a"), chapter(5f, "scanlator-b"))

        assertSame(chapters[0], findTaggedChapter(chapters, 5f))
    }

    @Test
    fun `a tag round trips back to the same chapter`() {
        val chapters = listOf(chapter(10f), chapter(10.5f))
        val tag = chapterTag("Chapter 10.5", 10.5f)
        val link = tag.substringAfter("](").removeSuffix(")")

        assertSame(chapters[1], findTaggedChapter(chapters, chapterNumberOfLink(link)!!))
    }
}
