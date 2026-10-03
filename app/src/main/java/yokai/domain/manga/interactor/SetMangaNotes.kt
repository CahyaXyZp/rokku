package yokai.domain.manga.interactor

import yokai.domain.manga.MangaNotesRepository

class SetMangaNotes(
    private val mangaNotesRepository: MangaNotesRepository,
) {
    suspend fun await(mangaId: Long, notes: String) =
        mangaNotesRepository.setNotes(mangaId, notes)
}
