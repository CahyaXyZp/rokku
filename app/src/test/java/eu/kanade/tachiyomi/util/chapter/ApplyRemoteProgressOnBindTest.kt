package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.track.TrackPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yokai.domain.chapter.interactor.UpdateChapter

class ApplyRemoteProgressOnBindTest {

    private val updateChapter: UpdateChapter = mockk(relaxed = true)
    private val syncPreference: Preference<Boolean> = mockk()
    private val trackPreferences: TrackPreferences = mockk()

    private fun toggle(enabled: Boolean) {
        every { syncPreference.get() } returns enabled
        every { trackPreferences.syncProgressOnBind() } returns syncPreference
    }

    private fun chapters(vararg entries: Pair<Float, Boolean>): List<Chapter> =
        entries.mapIndexed { index, (number, read) ->
            ChapterImpl().apply {
                this.id = index.toLong()
                this.url = "chapter-$index"
                this.chapter_number = number
                this.read = read
            }
        }

    private fun apply(list: List<Chapter>, remoteProgress: Float): List<Chapter> =
        runBlocking { applyRemoteProgressOnBind(list, remoteProgress, trackPreferences, updateChapter) }

    private fun List<Chapter>.readNumbers() = filter { it.read }.map { it.chapter_number }.sorted()

    @Test
    fun `toggle on marks main chapters through the remote progress only`() {
        toggle(true)
        val entries = (1..25).map { it.toFloat() to (it <= 10) } +
            listOf(10.1f to false, 15.5f to false, 20.1f to false)
        val list = chapters(*entries.toTypedArray())

        val marked = apply(list, 20f)

        assertEquals((11..20).map { it.toFloat() }, marked.map { it.chapter_number }.sorted())
        assertEquals((1..20).map { it.toFloat() }, list.readNumbers())
        coVerify(exactly = 1) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `toggle off leaves the local chapters alone`() {
        toggle(false)
        val list = chapters(1f to false, 2f to false, 3f to false)

        val marked = apply(list, 3f)

        assertTrue(marked.isEmpty())
        assertTrue(list.readNumbers().isEmpty())
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `remote progress behind the local one changes nothing`() {
        toggle(true)
        val list = chapters(*(1..10).map { it.toFloat() to true }.toTypedArray(), 11f to false)

        val marked = apply(list, 5f)

        assertTrue(marked.isEmpty())
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `remote progress equal to the local one does not read skipped chapters`() {
        toggle(true)
        val list = chapters(1f to false, 2f to false, 3f to true)

        val marked = apply(list, 3f)

        assertTrue(marked.isEmpty())
        assertEquals(listOf(3f), list.readNumbers())
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }

    @Test
    fun `chapters missing locally are skipped and none are created`() {
        toggle(true)
        val list = chapters(5f to false, 12f to false, 25f to false)

        val marked = apply(list, 20f)

        assertEquals(listOf(5f, 12f), marked.map { it.chapter_number })
        assertEquals(listOf(5f, 12f), list.readNumbers())
        assertEquals(3, list.size)
    }

    @Test
    fun `additional chapters are never marked`() {
        toggle(true)
        val list = chapters(1.1f to false, 2.1f to false, 3f to false)

        val marked = apply(list, 3f)

        assertEquals(listOf(3f), marked.map { it.chapter_number })
        assertEquals(listOf(3f), list.readNumbers())
    }

    @Test
    fun `applying again does not update the chapters twice`() {
        toggle(true)
        val list = chapters(1f to false, 2f to false, 3f to false)

        apply(list, 3f)
        val second = apply(list, 3f)

        assertTrue(second.isEmpty())
        coVerify(exactly = 1) { updateChapter.awaitAll(any()) }
    }
}
