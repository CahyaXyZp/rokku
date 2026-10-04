package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import co.touchlab.kermit.Logger
import coil3.load
import coil3.request.crossfade
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.useCustomCover
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.TrackerMangaDetails
import eu.kanade.tachiyomi.data.track.fetchMangaDetails
import eu.kanade.tachiyomi.data.track.supportsMangaDetails
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.clipboardHasImage
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.getClipboardImageUri
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.models.cover
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.coil.asTarget
import yokai.util.coil.loadManga
import yokai.util.lang.getString
import java.io.File
import android.R as AR

class EditMangaDialog : DialogController {

    private val manga: Manga

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    lateinit var binding: EditMangaDialogBinding
    private val languages = mutableListOf<String>()

    private val infoController
        get() = targetController as MangaDetailsController

    constructor(target: MangaDetailsController, manga: Manga) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, manga.id!!)
            },
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = runBlocking { Injekt.get<GetManga>().awaitById(bundle.getLong(KEY_MANGA))!! }
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val dialog = activity!!.materialAlertDialog().apply {
            setView(binding.root)
            setNegativeButton(AR.string.cancel, null)
            setPositiveButton(MR.strings.save) { _, _ -> onPositiveButtonClick() }
        }
        onViewCreated()
        val updateScrollIndicators = {
            binding.scrollIndicatorDown.isVisible = binding.scrollView.canScrollVertically(1)
        }
        binding.scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            updateScrollIndicators()
        }
        binding.scrollView.post {
            updateScrollIndicators()
        }
        return dialog.create()
    }

    fun onViewCreated() {
        val context = binding.root.context

        binding.mangaCover.loadManga(manga) {
            memoryCacheKeyExtra("size", "full")
            crossfade(false)
        }
        val isLocal = manga.isLocal()

        binding.mangaLang.isVisible = isLocal
        if (isLocal) {
            if (manga.title != manga.url) {
                binding.title.append(manga.title)
            }
            binding.title.hint = "${context.getString(MR.strings.title)}: ${manga.url}"
            binding.mangaAuthor.append(manga.author ?: "")
            binding.mangaArtist.append(manga.artist ?: "")
            binding.mangaDescription.append(manga.description ?: "")
            val preferences = infoController.presenter.preferences
            val extensionManager: ExtensionManager by injectLazy()
            val activeLangs = preferences.enabledLanguages().get()

            languages.add("")
            languages.addAll(
                extensionManager.availableExtensionsFlow.value.groupBy { it.lang }.keys
                    .sortedWith(
                        compareBy(
                            { it !in activeLangs },
                            { LocaleHelper.getSourceDisplayName(it, binding.root.context) },
                        ),
                    )
                    .filter { it != "all" && it != "other" },
            )
            binding.mangaLang.setEntries(
                languages.map {
                    LocaleHelper.getSourceDisplayName(it, binding.root.context)
                },
            )
            binding.mangaLang.setSelection(
                languages.indexOf(LocalSource.getMangaLang(manga))
                    .takeIf { it > -1 } ?: 0,
            )
        } else {
            if (manga.title != manga.ogTitle) {
                binding.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                binding.mangaAuthor.append(manga.author ?: "")
            }
            if (manga.artist != manga.originalArtist) {
                binding.mangaArtist.append(manga.artist ?: "")
            }
            if (manga.description != manga.originalDescription) {
                binding.mangaDescription.append(manga.description ?: "")
            }
            binding.title.appendOriginalTextOnLongClick(manga.originalTitle)
            binding.mangaAuthor.appendOriginalTextOnLongClick(manga.originalAuthor)
            binding.mangaArtist.appendOriginalTextOnLongClick(manga.originalArtist)
            binding.mangaDescription.appendOriginalTextOnLongClick(manga.originalDescription)
            binding.title.hint = "${context.getString(MR.strings.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                binding.mangaAuthor.hint = "${context.getString(MR.strings.author)}: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                binding.mangaArtist.hint = "${context.getString(MR.strings.artist)}: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                binding.mangaDescription.hint =
                    "${context.getString(MR.strings.description)}: ${manga.originalDescription?.replace(
                        "\n",
                        " ",
                    )?.chop(20)}"
            }
        }
        setGenreTags(manga.getGenres().orEmpty())
        if (!isLocal) {
            binding.mangaStatus.originalPosition = manga.originalStatus
            binding.seriesType.originalPosition = manga.seriesType(true) - 1
            infoController.presenter.source.icon()?.let { icon ->
                val bitD = ImageUtil.resizeBitMapDrawable(icon, resources, 24.dpToPx)
                binding.mangaStatus.originalIcon = bitD ?: icon
                binding.seriesType.originalIcon = bitD ?: icon
            }
        }
        binding.mangaStatus.setSelection(manga.status.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS))
        val oldType = manga.seriesType()
        binding.seriesType.setSelection(oldType - 1)
        binding.seriesType.onItemSelectedListener = {
            binding.resetsReadingMode.isVisible = it + 1 != oldType
        }
        binding.mangaGenresTags.clearFocus()
        binding.coverLayout.setOnClickListener {
            infoController.changeCover()
        }
        binding.coverLayout.setOnLongClickListener { view ->
            if (!view.context.clipboardHasImage()) return@setOnLongClickListener false
            view.startActionMode(pasteCoverActionModeCallback(view), ActionMode.TYPE_FLOATING)
            true
        }
        binding.resetTags.setOnClickListener { resetTags() }
        binding.resetTags.text = context.getString(
            if (manga.originalGenre.isNullOrBlank() || isLocal) {
                MR.strings.clear_tags
            } else {
                MR.strings.reset_tags
            },
        )
        binding.addTagChip.setOnClickListener {
            binding.addTagChip.isVisible = false
            binding.addTagEditText.isVisible = true
            binding.addTagEditText.requestFocus()
            showKeyboard()
        }
        binding.addTagEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v.parent != null) {
                addTags()
            }
        }
        binding.addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTags(true)
                binding.addTagEditText.clearFocus()
                hideKeyboard()
            } else {
                binding.addTagChip.isVisible = true
                binding.addTagEditText.isVisible = false
            }
            true
        }

        binding.resetCover.isVisible = !isLocal
        binding.resetCover.setOnClickListener {
            binding.mangaCover.loadManga(
                manga.cover(),
                target = binding.mangaCover.asTarget(),
            ) {
                memoryCacheKeyExtra("size", "full")
                useCustomCover(false)
            }
            customCoverUri = null
            willResetCover = true
        }

        if (!isLocal) {
            setupFetchFromTracker()
        }
    }

    /**
     * Shows the button that fills the form with what a linked tracker knows about this manga. It
     * only appears when the manga is linked to a tracker the user is logged in to and that can give
     * those details. Nothing is saved until the dialog is saved, so Cancel throws it all away.
     */
    private fun setupFetchFromTracker() {
        val getTrack: GetTrack = Injekt.get()
        val trackManager: TrackManager = Injekt.get()
        infoController.viewScope.launch {
            val candidates = withIOContext { getTrack.awaitAllByMangaId(manga.id) }
                .mapNotNull { track ->
                    trackManager.getService(track.sync_id)
                        ?.takeIf { it.isLogged && it.supportsMangaDetails() }
                        ?.let { it to track }
                }
            if (candidates.isEmpty() || !binding.root.isAttachedToWindow) return@launch

            binding.fetchFromTracker.isVisible = true
            binding.fetchFromTracker.setOnClickListener {
                if (candidates.size == 1) {
                    val (service, track) = candidates.first()
                    fetchFromTracker(service, track)
                } else {
                    val context = binding.root.context
                    activity!!.materialAlertDialog()
                        .setTitle(context.getString(MR.strings.fetch_from_tracker))
                        .setItems(
                            candidates.map { (service, _) -> context.getString(service.nameRes()) }.toTypedArray(),
                        ) { _, which ->
                            val (service, track) = candidates[which]
                            fetchFromTracker(service, track)
                        }
                        .show()
                }
            }
        }
    }

    private fun fetchFromTracker(service: TrackService, track: Track) {
        val context = binding.root.context
        binding.fetchFromTracker.isEnabled = false
        infoController.viewScope.launch {
            val details = try {
                service.fetchMangaDetails(track)
            } catch (e: Exception) {
                Logger.e(e) { "Unable to fetch manga details from the tracker" }
                null
            }
            if (!binding.root.isAttachedToWindow) return@launch

            binding.fetchFromTracker.isEnabled = true
            if (details == null) {
                context.toast(MR.strings.fetch_from_tracker_failed)
                return@launch
            }
            applyTrackerDetails(details)
            context.toast(MR.strings.fetch_from_tracker_done)
        }
    }

    private suspend fun applyTrackerDetails(details: TrackerMangaDetails) {
        details.author?.let { binding.mangaAuthor.setText(it) }
        details.artist?.let { binding.mangaArtist.setText(it) }
        details.description?.let { binding.mangaDescription.setText(it) }
        details.status?.let { binding.mangaStatus.setSelection(it.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS)) }
        if (details.genres.isNotEmpty()) {
            setGenreTags(details.genres)
            binding.seriesType.setSelection(manga.seriesType(customTags = details.genres.joinToString(", ")) - 1)
        }
        details.coverUrl?.let { downloadCover(it) }
    }

    private suspend fun downloadCover(url: String) {
        val file = try {
            withIOContext {
                val network: NetworkHelper = Injekt.get()
                network.client.newCall(GET(url)).await().use { response ->
                    if (!response.isSuccessful) return@withIOContext null
                    File(binding.root.context.cacheDir, "tracker_cover_${manga.id}").also { file ->
                        response.body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Unable to download the cover from the tracker" }
            null
        }
        if (file != null && binding.root.isAttachedToWindow) {
            updateCover(Uri.fromFile(file))
        }
    }

    private fun addTags(textCanBeBlank: Boolean = false) {
        if ((textCanBeBlank || !binding.addTagEditText.text.isNullOrBlank()) &&
            binding.addTagEditText.isVisible
        ) {
            val newTags = binding.addTagEditText.text.toString().split(",")
                .mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
            val tags: List<String> = binding.mangaGenresTags.tags.toList() + newTags
            binding.addTagEditText.setText("")
            setGenreTags(tags)
            binding.seriesType.setSelection(manga.seriesType(customTags = tags.joinToString(", ")) - 1)
            binding.addTagChip.isVisible = true
            binding.addTagEditText.isVisible = false
        }
    }

    private fun TachiyomiTextInputEditText.appendOriginalTextOnLongClick(originalText: String?) {
        setOnLongClickListener {
            if (this.text.isNullOrBlank()) {
                this.append(originalText ?: "")
                true
            } else {
                false
            }
        }
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            binding.addTagEditText,
            WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_PAN,
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.addTagEditText.windowToken, 0)
    }

    private fun setGenreTags(genres: List<String>) {
        with(binding.mangaGenresTags) {
            val addTagChip = binding.addTagChip
            val addTagEditText = binding.addTagEditText
            removeAllViews()
            val dark = context.isInNightMode()
            val amoled = infoController.presenter.preferences.themeDarkAmoled().get()
            val baseTagColor = context.getResourceColor(R.attr.background)
            val bgArray = FloatArray(3)
            val accentArray = FloatArray(3)

            ColorUtils.colorToHSL(baseTagColor, bgArray)
            ColorUtils.colorToHSL(context.getResourceColor(R.attr.colorSecondary), accentArray)
            val downloadedColor = ColorUtils.setAlphaComponent(
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        bgArray[0],
                        bgArray[1],
                        (
                            when {
                                amoled && dark -> 0.1f
                                dark -> 0.225f
                                else -> 0.85f
                            }
                            ),
                    ),
                ),
                199,
            )
            val textColor = ColorUtils.HSLToColor(
                floatArrayOf(
                    accentArray[0],
                    accentArray[1],
                    if (dark) 0.945f else 0.175f,
                ),
            )
            genres.map { genreText ->
                val chip = LayoutInflater.from(binding.root.context).inflate(
                    R.layout.genre_chip,
                    this,
                    false,
                ) as Chip
                val id = View.generateViewId()
                chip.id = id
                chip.chipBackgroundColor = ColorStateList.valueOf(downloadedColor)
                chip.setTextColor(textColor)
                chip.text = genreText
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener { view ->
                    this.removeView(view)
                    val tags: List<String> = tags.toList() - (view as Chip).text.toString()
                    binding.seriesType.setSelection(
                        manga.seriesType(
                            customTags = tags.joinToString(
                                ", ",
                            ),
                        ) - 1,
                    )
                }
                this.addView(chip)
            }
            addView(addTagChip)
            addView(addTagEditText)
        }
    }

    private val ChipGroup.tags: Array<String>
        get() = children
            .toList()
            .filterIsInstance<Chip>()
            .filter { it.isCloseIconVisible }
            .map { it.text.toString() }
            .toTypedArray()

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.isLocal()) {
            setGenreTags(emptyList())
        } else {
            setGenreTags(manga.getOriginalGenres().orEmpty())
            binding.seriesType.setSelection(manga.seriesType(true) - 1)
            binding.resetsReadingMode.isVisible = false
        }
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        binding.mangaCover.load(uri)
        customCoverUri = uri
    }

    private fun pasteCoverActionModeCallback(view: View): ActionMode.Callback =
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                menu?.add(0, MENU_PASTE, 0, AR.string.paste)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item?.itemId != MENU_PASTE) return false
                val uri = view.context.getClipboardImageUri() ?: return false
                updateCover(uri)
                mode?.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }

    private fun onPositiveButtonClick() {
        addTags()
        infoController.presenter.updateManga(
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            binding.mangaArtist.text.toString(),
            customCoverUri,
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.tags,
            binding.mangaStatus.selectedPosition,
            if (binding.resetsReadingMode.isVisible) binding.seriesType.selectedPosition + 1 else null,
            languages.getOrNull(binding.mangaLang.selectedPosition),
            willResetCover,
        )
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
        const val MENU_PASTE = 1
    }
}
