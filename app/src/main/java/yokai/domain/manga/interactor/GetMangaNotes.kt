package yokai.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import yokai.domain.manga.MangaNotesRepository

class GetMangaNotes(
    private val mangaNotesRepository: MangaNotesRepository,
) {
    suspend fun await(mangaId: Long): String =
        mangaNotesRepository.getNotes(mangaId)

    fun subscribe(mangaId: Long): Flow<String> =
        mangaNotesRepository.subscribeNotes(mangaId)
}
