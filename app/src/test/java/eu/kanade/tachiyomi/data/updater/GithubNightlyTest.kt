package eu.kanade.tachiyomi.data.updater

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GithubNightlyTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val runUrl = "https://github.com/CahyaXyZp/rokku/actions/runs/42"

    private fun compare(status: String, aheadBy: Int, vararg messages: String) = GithubCompare(
        status = status,
        aheadBy = aheadBy,
        commits = messages.mapIndexed { index, message ->
            GithubCompare.Commit("abcdef$index".padEnd(40, '0'), GithubCompare.Detail(message))
        },
    )

    @Test
    fun `workflow runs are parsed from the api payload`() {
        val payload = """
            {"total_count": 1, "workflow_runs": [
                {"id": 42, "head_sha": "abc123", "html_url": "$runUrl", "conclusion": "success"}
            ]}
        """.trimIndent()

        val run = json.decodeFromString<GithubWorkflowRuns>(payload).runs.single()

        assertEquals("abc123", run.headSha)
        assertEquals(runUrl, run.url)
    }

    @Test
    fun `comparison is parsed from the api payload`() {
        val payload = """
            {"status": "ahead", "ahead_by": 2, "behind_by": 0, "commits": [
                {"sha": "aaa111", "commit": {"message": "first\n\nbody", "author": {"name": "x"}}},
                {"sha": "bbb222", "commit": {"message": "second"}}
            ]}
        """.trimIndent()

        val compare = json.decodeFromString<GithubCompare>(payload)

        assertEquals("ahead", compare.status)
        assertEquals(2, compare.aheadBy)
        assertEquals(listOf("aaa111", "bbb222"), compare.commits.map { it.sha })
    }

    @Test
    fun `a run ahead of the installed build becomes an update`() {
        val release = compare("ahead", 3, "one", "two", "three").toNightlyRelease(runUrl, 100)

        assertEquals("r103", release?.version)
        assertEquals(runUrl, release?.releaseLink)
        assertEquals(true, release?.preRelease)
    }

    @Test
    fun `a nightly update links to the run page and has no apk`() {
        val release = compare("ahead", 1, "one").toNightlyRelease(runUrl, 100)!!

        assertFalse(release.isApkDownloadable)
        assertEquals(runUrl, release.downloadLink)
    }

    @Test
    fun `release notes list the newest commit first with its title only`() {
        val release = compare("ahead", 2, "older change\n\nlong body", "newer change").toNightlyRelease(runUrl, 100)!!

        assertEquals(
            "- newer change (abcdef1)\n- older change (abcdef0)",
            release.info,
        )
    }

    @Test
    fun `release notes keep only the newest ten commits`() {
        val messages = (1..12).map { "change $it" }.toTypedArray()

        val release = compare("ahead", 12, *messages).toNightlyRelease(runUrl, 100)!!

        val lines = release.info.lines()
        assertEquals(10, lines.size)
        assertTrue(lines.first().startsWith("- change 12"))
        assertTrue(lines.last().startsWith("- change 3"))
    }

    @Test
    fun `no update unless the run is ahead`() {
        assertNull(compare("identical", 0).toNightlyRelease(runUrl, 100))
        assertNull(compare("behind", 0).toNightlyRelease(runUrl, 100))
        assertNull(compare("diverged", 2, "one", "two").toNightlyRelease(runUrl, 100))
        assertNull(compare("ahead", 0).toNightlyRelease(runUrl, 100))
    }

    @Test
    fun `version falls back to the newest commit when the count is unknown`() {
        val release = compare("ahead", 2, "one", "two").toNightlyRelease(runUrl, null)

        assertEquals("abcdef1", release?.version)
    }

    @Test
    fun `only actions run pages are treated as pages`() {
        assertTrue(runUrl.isActionsRunUrl())
        assertFalse("https://github.com/CahyaXyZp/rokku/releases/download/v1.7.2/rokku-v1.7.2.apk".isActionsRunUrl())
    }
}
