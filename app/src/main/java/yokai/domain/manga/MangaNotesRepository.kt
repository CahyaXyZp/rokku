package yokai.domain.manga

import kotlinx.coroutines.flow.Flow

interface MangaNotesRepository {
    suspend fun getNotes(mangaId: Long): String
    fun subscribeNotes(mangaId: Long): Flow<String>
    suspend fun setNotes(mangaId: Long, notes: String)
}
