package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackerProgressTest {

    private fun chapters(vararg entries: Pair<Float, Boolean>): List<Chapter> =
        entries.mapIndexed { index, (number, read) ->
            ChapterImpl().apply {
                this.id = index.toLong()
                this.url = "chapter-$index"
                this.chapter_number = number
                this.read = read
            }
        }

    @Test
    fun `only positive main chapters count as tracker progress`() {
        assertTrue(isTrackerProgressNumber(1f))
        assertTrue(isTrackerProgressNumber(151f))

        assertFalse(isTrackerProgressNumber(0f))
        assertFalse(isTrackerProgressNumber(-1f))
        assertFalse(isTrackerProgressNumber(0.5f))
        assertFalse(isTrackerProgressNumber(1.1f))
        assertFalse(isTrackerProgressNumber(151.5f))
    }

    @Test
    fun `additional chapters are not tracker progress`() {
        val list = chapters(1f to true, 1.1f to true, 1.5f to false, 2f to true)

        assertEquals(listOf(true, false, false, true), list.map { it.isTrackerProgress })
    }

    @Test
    fun `read progress skips additional chapters`() {
        val progress = chapters(
            1f to true,
            1.1f to true,
            1.5f to false,
            2f to true,
        ).readTrackerProgress()

        assertEquals(2f, progress)
    }

    @Test
    fun `read progress is the highest read main chapter`() {
        val progress = chapters(
            1f to true,
            2f to false,
            3f to true,
            3.5f to true,
            4f to false,
        ).readTrackerProgress()

        assertEquals(3f, progress)
    }

    @Test
    fun `read progress is zero when only additional chapters are read`() {
        val progress = chapters(
            1f to false,
            1.1f to true,
            2f to false,
        ).readTrackerProgress()

        assertEquals(0f, progress)
    }

    @Test
    fun `read progress ignores chapter zero and unrecognized numbers`() {
        val progress = chapters(
            -1f to true,
            0f to true,
            1f to false,
        ).readTrackerProgress()

        assertEquals(0f, progress)
    }

    @Test
    fun `latest tracker chapter picks the main chapter whatever the order`() {
        val additionalFirst = chapters(1.1f to true, 1f to true).latestTrackerChapter()
        val mainFirst = chapters(1f to true, 1.1f to true).latestTrackerChapter()

        assertEquals(1f, additionalFirst?.chapter_number)
        assertEquals(1f, mainFirst?.chapter_number)
    }

    @Test
    fun `latest tracker chapter is null when nothing counts`() {
        assertNull(chapters(1.1f to true, 0f to true, -1f to true).latestTrackerChapter())
        assertNull(emptyList<Chapter>().latestTrackerChapter())
    }

    @Test
    fun `remote progress marks existing main chapters only`() {
        val list = chapters(
            1f to false,
            10f to false,
            10.1f to false,
            15.5f to false,
            20f to false,
            20.1f to false,
            21f to false,
        )

        val toMark = list.unreadMainChaptersUpTo(20f)

        assertEquals(listOf(1f, 10f, 20f), toMark.map { it.chapter_number })
    }

    @Test
    fun `remote progress skips chapters that are already read`() {
        val list = chapters(1f to true, 2f to false, 3f to true, 4f to false)

        val toMark = list.unreadMainChaptersUpTo(4f)

        assertEquals(listOf(2f, 4f), toMark.map { it.chapter_number })
    }

    @Test
    fun `remote progress only covers chapters that exist locally`() {
        val list = chapters(5f to false, 12f to false, 25f to false)

        val toMark = list.unreadMainChaptersUpTo(20f)

        assertEquals(listOf(5f, 12f), toMark.map { it.chapter_number })
    }

    @Test
    fun `remote progress never marks unrecognized chapters`() {
        val list = chapters(-1f to false, 1f to false)

        val toMark = list.unreadMainChaptersUpTo(20f)

        assertEquals(listOf(1f), toMark.map { it.chapter_number })
    }

    @Test
    fun `no remote progress marks nothing`() {
        val list = chapters(0f to false, 1f to false)

        assertTrue(list.unreadMainChaptersUpTo(0f).isEmpty())
    }
}
