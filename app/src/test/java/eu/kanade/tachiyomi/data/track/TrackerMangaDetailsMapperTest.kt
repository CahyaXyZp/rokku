package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.dto.ALMangaDetailsResult
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaCovers
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaInfo
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TrackerMangaDetailsMapperTest {

    @Test
    fun `line breaks and tags are cleaned from a description`() {
        val raw = "First line<br><br>Second <i>line</i><br />Third"

        assertEquals("First line\n\nSecond line\nThird", cleanTrackerDescription(raw))
    }

    @Test
    fun `html entities are decoded after the tags are removed`() {
        assertEquals("Tom & Jerry's <story>", cleanTrackerDescription("Tom &amp; Jerry&#039;s &lt;story&gt;"))
    }

    @Test
    fun `the mal signature is dropped`() {
        assertEquals("Plot", cleanTrackerDescription("Plot\n\n[Written by MAL Rewrite]"))
    }

    @Test
    fun `a blank description is nothing`() {
        assertNull(cleanTrackerDescription(null))
        assertNull(cleanTrackerDescription("   "))
        assertNull(cleanTrackerDescription("<br>"))
    }

    @Test
    fun `story and art roles are split into author and artist`() {
        val (author, artist) = splitCreators(
            listOf(
                "Writer" to "Story",
                "Drawer" to "Art",
                "Both" to "Story & Art",
                "Someone" to "Translator",
            ),
        )

        assertEquals("Writer, Both", author)
        assertEquals("Drawer, Both", artist)
    }

    @Test
    fun `no matching role gives no author or artist`() {
        val (author, artist) = splitCreators(listOf("Someone" to "Translator", "Other" to null))

        assertNull(author)
        assertNull(artist)
    }

    @Test
    fun `a person with several credits is listed once`() {
        val (author, _) = splitCreators(listOf("Same" to "Story", "Same" to "Original Story"))

        assertEquals("Same", author)
    }

    @Test
    fun `anilist statuses map to the app statuses`() {
        assertEquals(SManga.ONGOING, anilistStatusToSManga("RELEASING"))
        assertEquals(SManga.COMPLETED, anilistStatusToSManga("FINISHED"))
        assertEquals(SManga.CANCELLED, anilistStatusToSManga("CANCELLED"))
        assertEquals(SManga.ON_HIATUS, anilistStatusToSManga("HIATUS"))
        assertEquals(SManga.UNKNOWN, anilistStatusToSManga("NOT_YET_RELEASED"))
        assertNull(anilistStatusToSManga("SOMETHING_NEW"))
        assertNull(anilistStatusToSManga(null))
    }

    @Test
    fun `mal statuses map to the app statuses`() {
        assertEquals(SManga.ONGOING, malStatusToSManga("currently_publishing"))
        assertEquals(SManga.COMPLETED, malStatusToSManga("finished"))
        assertEquals(SManga.CANCELLED, malStatusToSManga("discontinued"))
        assertEquals(SManga.ON_HIATUS, malStatusToSManga("on_hiatus"))
        assertEquals(SManga.UNKNOWN, malStatusToSManga("not_yet_published"))
        assertNull(malStatusToSManga("something_new"))
    }

    @Test
    fun `an anilist entry becomes details`() {
        val media = ALMangaDetailsResult.Media(
            coverImage = ALMangaDetailsResult.Cover(extraLarge = "xl", large = "l"),
            description = "A<br>B",
            genres = listOf("Action", "", "Adventure"),
            status = "FINISHED",
            staff = ALMangaDetailsResult.Staff(
                listOf(
                    ALMangaDetailsResult.Edge("Story", ALMangaDetailsResult.Node(ALMangaDetailsResult.Name("Li"))),
                    ALMangaDetailsResult.Edge("Art", ALMangaDetailsResult.Node(ALMangaDetailsResult.Name("Wu"))),
                    ALMangaDetailsResult.Edge("Art", ALMangaDetailsResult.Node(null)),
                ),
            ),
        )

        val details = media.toTrackerDetails()

        assertEquals("xl", details.coverUrl)
        assertEquals("A\nB", details.description)
        assertEquals(listOf("Action", "Adventure"), details.genres)
        assertEquals("Li", details.author)
        assertEquals("Wu", details.artist)
        assertEquals(SManga.COMPLETED, details.status)
    }

    @Test
    fun `an anilist entry with nothing in it gives empty details`() {
        val details = ALMangaDetailsResult.Media().toTrackerDetails()

        assertNull(details.coverUrl)
        assertNull(details.description)
        assertEquals(emptyList<String>(), details.genres)
        assertNull(details.author)
        assertNull(details.artist)
        assertNull(details.status)
    }

    @Test
    fun `a mal entry becomes details`() {
        val info = MALMangaInfo(
            synopsis = "Plot\n\n[Written by MAL Rewrite]",
            covers = MALMangaCovers(large = null, medium = "m"),
            genres = listOf(MALMangaInfo.Genre("Action"), MALMangaInfo.Genre(null)),
            authors = listOf(
                MALMangaInfo.Author(MALMangaInfo.Person("Zhiheng", "Li"), "Story & Art"),
                MALMangaInfo.Author(MALMangaInfo.Person(null, null), "Art"),
            ),
            status = "currently_publishing",
        )

        val details = info.toTrackerDetails()

        assertEquals("m", details.coverUrl)
        assertEquals("Plot", details.description)
        assertEquals(listOf("Action"), details.genres)
        assertEquals("Zhiheng Li", details.author)
        assertEquals("Zhiheng Li", details.artist)
        assertEquals(SManga.ONGOING, details.status)
    }
}
