package yokai.presentation.manga.notes

import android.os.Bundle
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController

class MangaNotesController(bundle: Bundle? = null) : BaseComposeController(bundle) {

    constructor(mangaId: Long) : this(Bundle().apply { putLong(MANGA_ID_KEY, mangaId) })

    private val mangaId: Long = args.getLong(MANGA_ID_KEY)

    @Composable
    override fun ScreenContent() {
        MangaNotesScreen(
            mangaId = mangaId,
            onBack = { router.handleBack() },
        )
    }

    private companion object {
        const val MANGA_ID_KEY = "MangaNotesController.mangaId"
    }
}
