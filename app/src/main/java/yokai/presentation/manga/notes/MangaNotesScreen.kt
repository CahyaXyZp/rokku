package yokai.presentation.manga.notes

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.toast
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.GetMangaNotes
import yokai.domain.manga.interactor.SetMangaNotes
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold

private const val AUTOSAVE_DELAY_MS = 600L

@Composable
fun MangaNotesScreen(mangaId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val getMangaNotes = remember { Injekt.get<GetMangaNotes>() }
    val setMangaNotes = remember { Injekt.get<SetMangaNotes>() }
    val getChapter = remember { Injekt.get<GetChapter>() }
    val getManga = remember { Injekt.get<GetManga>() }

    var value by remember { mutableStateOf(TextFieldValue()) }
    var savedText by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    var chapters by remember { mutableStateOf(emptyList<Chapter>()) }

    LaunchedEffect(mangaId) {
        val notes = getMangaNotes.await(mangaId)
        chapters = getChapter.awaitAll(mangaId, false)
        value = TextFieldValue(notes, TextRange(notes.length))
        savedText = notes
        editing = notes.isBlank()
        loaded = true
    }

    // Saves shortly after the user stops typing
    LaunchedEffect(value.text, loaded) {
        if (!loaded || value.text == savedText) return@LaunchedEffect
        delay(AUTOSAVE_DELAY_MS)
        setMangaNotes.await(mangaId, value.text)
        savedText = value.text
    }

    // ...and once more when the screen goes away, in case that happens before the delay is over
    val currentText by rememberUpdatedState(value.text)
    val currentSavedText by rememberUpdatedState(savedText)
    val currentLoaded by rememberUpdatedState(loaded)
    DisposableEffect(Unit) {
        onDispose {
            if (currentLoaded && currentText != currentSavedText) {
                launchIO { setMangaNotes.await(mangaId, currentText) }
            }
        }
    }

    val openChapter: (Float) -> Unit = { number ->
        scope.launch {
            val chapter = findTaggedChapter(getChapter.awaitAll(mangaId, false), number)
            val manga = getManga.awaitById(mangaId)
            if (chapter == null || manga == null) {
                context.toast(MR.strings.notes_chapter_not_found)
            } else {
                context.startActivity(ReaderActivity.newIntent(context, manga, chapter))
            }
        }
    }

    YokaiScaffold(
        onNavigationIconClicked = onBack,
        title = stringResource(MR.strings.manga_notes),
        appBarType = AppBarType.SMALL,
        actions = {
            TextButton(onClick = { editing = !editing }) {
                Text(stringResource(if (editing) MR.strings.notes_preview else MR.strings.notes_edit))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (editing) {
                NotesEditor(
                    value = value,
                    onValueChange = { new ->
                        if (isChapterTagTrigger(value, new)) {
                            showPicker = true
                        } else {
                            value = new
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                NotesToolbar(
                    onTagChapter = { showPicker = true },
                    onWrap = { marker -> value = value.wrapSelection(marker) },
                    onInsert = { snippet -> value = value.insertAtCursor(snippet) },
                )
            } else {
                NotesPreview(
                    text = value.text,
                    onChapterClick = openChapter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }

    if (showPicker) {
        ChapterPickerDialog(
            chapters = chapters,
            onDismiss = { showPicker = false },
            onPick = { chapter ->
                value = value.insertAtCursor(chapterTag(chapter.name, chapter.chapter_number))
                showPicker = false
            },
        )
    }
}

/**
 * Typing "@" at the start of a line or after a space opens the chapter picker instead of
 * inserting the character. An "@" inside a word, like in an email address, stays a normal character.
 */
private fun isChapterTagTrigger(old: TextFieldValue, new: TextFieldValue): Boolean {
    val cursor = new.selection.start
    return new.text.length == old.text.length + 1 &&
        new.selection.collapsed &&
        cursor > 0 &&
        new.text[cursor - 1] == '@' &&
        (cursor == 1 || new.text[cursor - 2].isWhitespace())
}

private fun TextFieldValue.insertAtCursor(snippet: String): TextFieldValue {
    val range = selection
    return copy(
        text = text.replaceRange(range.min, range.max, snippet),
        selection = TextRange(range.min + snippet.length),
    )
}

private fun TextFieldValue.wrapSelection(marker: String): TextFieldValue {
    val range = selection
    val selected = text.substring(range.min, range.max)
    val cursor = if (range.collapsed) {
        range.min + marker.length
    } else {
        range.min + marker.length * 2 + selected.length
    }
    return copy(
        text = text.replaceRange(range.min, range.max, "$marker$selected$marker"),
        selection = TextRange(cursor),
    )
}

@Composable
private fun NotesEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(stringResource(MR.strings.notes_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun NotesToolbar(
    onTagChapter: () -> Unit,
    onWrap: (String) -> Unit,
    onInsert: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(onClick = onTagChapter, label = { Text(stringResource(MR.strings.notes_tag_chapter)) })
        AssistChip(onClick = { onWrap("**") }, label = { Text("B") })
        AssistChip(onClick = { onWrap("*") }, label = { Text("I") })
        AssistChip(onClick = { onWrap("~~") }, label = { Text("S") })
        AssistChip(onClick = { onInsert("\n- ") }, label = { Text("•") })
        AssistChip(onClick = { onInsert("\n- [ ] ") }, label = { Text("☐") })
    }
}

@Composable
private fun NotesPreview(
    text: String,
    onChapterClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentOnChapterClick by rememberUpdatedState(onChapterClick)
    val markwon = remember(context) { buildMarkwon(context) { currentOnChapterClick(it) } }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (text.isBlank()) {
            Text(
                text = stringResource(MR.strings.notes_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        textSize = 16f
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                update = { view ->
                    view.setTextColor(textColor)
                    view.setLinkTextColor(linkColor)
                    markwon.setMarkdown(view, text)
                },
            )
        }
    }
}

private fun buildMarkwon(context: Context, onChapterClick: (Float) -> Unit): Markwon =
    Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(
            object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    val fallback = LinkResolverDef()
                    builder.linkResolver { view, link ->
                        val number = chapterNumberOfLink(link)
                        if (number != null) onChapterClick(number) else fallback.resolve(view, link)
                    }
                }
            },
        )
        .build()

@Composable
private fun ChapterPickerDialog(
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onPick: (Chapter) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(chapters) { chapters.sortedByDescending { it.chapter_number } }
    val filtered = remember(sorted, query) {
        if (query.isBlank()) {
            sorted
        } else {
            sorted.filter {
                it.name.contains(query, ignoreCase = true) ||
                    formatChapterNumber(it.chapter_number).contains(query)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.notes_tag_chapter)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(MR.strings.search_chapters)) },
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .padding(top = 8.dp),
                ) {
                    items(filtered, key = { it.url }) { chapter ->
                        Text(
                            text = chapter.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(chapter) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.cancel))
            }
        },
    )
}
