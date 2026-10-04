package eu.kanade.tachiyomi.data.backup.create

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import yokai.domain.backup.BackupPreferences

class BackupRetentionTest {

    private val backups = listOf(
        "rokku_2026-10-01_10-00.tachibk",
        "rokku_2026-10-02_10-00.tachibk",
        "rokku_2026-10-03_10-00.tachibk",
        "rokku_2026-10-04_10-00.tachibk",
        "rokku_2026-10-05_10-00.tachibk",
    )

    private fun toDelete(numberOfBackups: Int, files: List<String> = backups) =
        backupsToDelete(files, numberOfBackups) { it }

    @Test
    fun `no limit keeps every backup`() {
        assertTrue(toDelete(BackupPreferences.UNLIMITED_BACKUPS).isEmpty())
    }

    @Test
    fun `no limit keeps every backup however many there are`() {
        val many = (1..500).map { "rokku_2026-10-01_10-%03d.tachibk".format(it) }

        assertTrue(toDelete(BackupPreferences.UNLIMITED_BACKUPS, many).isEmpty())
    }

    @Test
    fun `the oldest backups go first to make room for the new one`() {
        assertEquals(
            listOf(
                "rokku_2026-10-03_10-00.tachibk",
                "rokku_2026-10-02_10-00.tachibk",
                "rokku_2026-10-01_10-00.tachibk",
            ),
            toDelete(3),
        )
    }

    @Test
    fun `a limit of one deletes every existing backup`() {
        assertEquals(5, toDelete(1).size)
    }

    @Test
    fun `nothing is deleted while there is room`() {
        assertTrue(toDelete(5, backups.take(4)).isEmpty())
        assertTrue(toDelete(5, emptyList()).isEmpty())
    }

    @Test
    fun `the order of the listing does not matter`() {
        assertEquals(toDelete(3), toDelete(3, backups.shuffled()))
    }
}
