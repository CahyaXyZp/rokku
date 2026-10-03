package yokai.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import yokai.data.DatabaseHandler
import yokai.domain.manga.MangaNotesRepository

class MangaNotesRepositoryImpl(private val handler: DatabaseHandler) : MangaNotesRepository {
    override suspend fun getNotes(mangaId: Long): String =
        handler.awaitOneOrNull { manga_notesQueries.getNotes(mangaId) }.orEmpty()

    override fun subscribeNotes(mangaId: Long): Flow<String> =
        handler.subscribeToOneOrNull { manga_notesQueries.getNotes(mangaId) }.map { it.orEmpty() }

    override suspend fun setNotes(mangaId: Long, notes: String) {
        handler.await {
            if (notes.isBlank()) {
                manga_notesQueries.deleteForManga(mangaId)
            } else {
                manga_notesQueries.upsert(mangaId, notes)
            }
        }
    }
}
