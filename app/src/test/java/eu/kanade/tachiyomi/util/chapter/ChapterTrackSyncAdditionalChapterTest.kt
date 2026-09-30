package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.TrackImpl
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackService
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack

class ChapterTrackSyncAdditionalChapterTest {

    private val updateChapter: UpdateChapter = mockk(relaxed = true)
    private val insertTrack: InsertTrack = mockk(relaxed = true)
    private val service = mockk<TrackService>(null, true, EnhancedTrackService::class)

    private fun chapters(vararg entries: Pair<Float, Boolean>): List<Chapter> =
        entries.mapIndexed { index, (number, read) ->
            ChapterImpl().apply {
                this.id = index.toLong()
                this.url = "chapter-$index"
                this.chapter_number = number
                this.read = read
            }
        }

    private fun sync(chapters: List<Chapter>, remoteProgress: Float): Track {
        val track = TrackImpl().apply { last_chapter_read = remoteProgress }
        runBlocking { syncChaptersWithTrackServiceTwoWay(chapters, track, service, updateChapter, insertTrack) }
        return track
    }

    private fun List<Chapter>.readNumbers() = filter { it.read }.map { it.chapter_number }

    @Test
    fun `remote progress marks main chapters through it and leaves additional ones`() {
        val list = chapters(
            *((1..25).map { it.toFloat() to false } + listOf(10.1f to false, 15.5f to false, 20.1f to false))
                .toTypedArray(),
        )

        val track = sync(list, 20f)

        assertEquals((1..20).map { it.toFloat() }, list.readNumbers().sorted())
        assertEquals(20f, track.last_chapter_read)
        coVerify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `remote progress is never lowered by less local progress`() {
        val list = chapters(1f to true, 2f to true, 3f to false, 25f to false)

        val track = sync(list, 20f)

        assertEquals(20f, track.last_chapter_read)
        coVerify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `missing local chapters are not created and stay unmarked`() {
        val list = chapters(5f to false, 12f to false, 25f to false)

        val track = sync(list, 20f)

        assertEquals(listOf(5f, 12f), list.readNumbers())
        assertEquals(20f, track.last_chapter_read)
        coVerify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `local progress ahead of the remote is sent once as a main chapter`() {
        val list = chapters(1f to true, 1.1f to true, 2f to true, 2.5f to true, 3f to false)

        val track = sync(list, 1f)

        assertEquals(2f, track.last_chapter_read)
        coVerify(exactly = 1) { service.update(any(), any()) }
    }

    @Test
    fun `additional chapters never become the sent progress`() {
        val list = chapters(1f to true, 1.1f to true, 1.5f to true, 2f to false)

        val track = sync(list, 0f)

        assertEquals(1f, track.last_chapter_read)
    }

    @Test
    fun `only additional chapters read sends nothing`() {
        val list = chapters(1f to false, 1.1f to true, 2f to false)

        val track = sync(list, 0f)

        assertEquals(0f, track.last_chapter_read)
        coVerify(exactly = 0) { service.update(any(), any()) }
    }

    @Test
    fun `syncing again does not update chapters or the remote`() {
        val list = chapters(1f to false, 2f to false, 3f to false)
        val track = sync(list, 2f)

        runBlocking { syncChaptersWithTrackServiceTwoWay(list, track, service, updateChapter, insertTrack) }

        coVerify(exactly = 1) { updateChapter.awaitAll(any()) }
        coVerify(exactly = 0) { service.update(any(), any()) }
        assertTrue(list.readNumbers().containsAll(listOf(1f, 2f)))
    }

    @Test
    fun `additional chapter numbers are never sent by the tracker update`() {
        val getTrack: GetTrack = mockk()
        val insertTrack: InsertTrack = mockk()
        val preferences = mockk<eu.kanade.tachiyomi.data.preference.PreferencesHelper>()

        listOf(1.1f, 151.5f, 0f, -1f).forEach { number ->
            val failures = runBlocking {
                updateTrackChapterRead(preferences, 1L, number, getTrack = getTrack, insertTrack = insertTrack)
            }

            assertTrue(failures.isEmpty())
        }
    }
}
