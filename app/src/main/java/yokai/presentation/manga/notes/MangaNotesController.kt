package yokai.presentation.manga.notes

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController

class MangaNotesController(bundle: Bundle? = null) : BaseComposeController(bundle) {

    constructor(mangaId: Long) : this(Bundle().apply { putLong(MANGA_ID_KEY, mangaId) })

    private val mangaId: Long = args.getLong(MANGA_ID_KEY)

    private var hasUnsavedChanges = false
    private var showExitPrompt by mutableStateOf(false)

    @Composable
    override fun ScreenContent() {
        MangaNotesScreen(
            mangaId = mangaId,
            showExitPrompt = showExitPrompt,
            onUnsavedChanges = { hasUnsavedChanges = it },
            onExitPromptDismiss = { showExitPrompt = false },
            onExit = {
                hasUnsavedChanges = false
                showExitPrompt = false
                router.popCurrentController()
            },
            onBack = { router.handleBack() },
        )
    }

    override fun handleBack(): Boolean {
        if (hasUnsavedChanges) {
            showExitPrompt = true
            return true
        }
        return super.handleBack()
    }

    private companion object {
        const val MANGA_ID_KEY = "MangaNotesController.mangaId"
    }
}
