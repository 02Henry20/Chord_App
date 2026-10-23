package com.henry.chord

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

fun Song.originalKeyLabel(): String = KeyContext.from(key,tonalMode)?.name ?: key.ifBlank { "Key unknown" }
fun Song.displayKey(spelling: Spelling = Spelling.AUTO): String = originalKeyLabel().let { original ->
    if (key.isBlank() || transpose % 12 == 0) original else "$original → ${KeyContext.from(key, tonalMode)?.let { Theory.currentKey(it, transpose, spelling).name } ?: Theory.transpose(key, transpose, spelling = spelling)}"
}
fun Song.tags(): List<String> = runCatching { org.json.JSONArray(tagsJson).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

@Composable
fun SongRow(song: Song, onOpen: () -> Unit, onFavorite: (() -> Unit)? = null, number: String? = null, query: String = "", excerpt: String? = null, spelling: Spelling = Spelling.AUTO) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 9.dp).testTag("song-${song.id}"), verticalAlignment = Alignment.CenterVertically) {
        SongArtwork(song, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((number?.let { "$it  " } ?: "") + song.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist.ifBlank { "Unknown artist" } + if (song.arrangementName != "Main") " · ${song.arrangementName}" else "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.displayKey(spelling) + (song.tags().firstOrNull()?.let { "   ·   $it" } ?: ""), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (excerpt != null) {
                val background = MaterialTheme.colorScheme.secondaryContainer
                val foreground = MaterialTheme.colorScheme.onSecondaryContainer
                Text(buildAnnotatedString {
                    append(excerpt.replace('\n', ' '))
                    SearchText.highlights(excerpt, query).forEach { addStyle(SpanStyle(background = background, color = foreground, fontWeight = FontWeight.Bold), it.first, it.last + 1) }
                }, fontSize = 12.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp).testTag("lyric-${song.id}"))
            }
        }
        if (onFavorite != null) IconToggleButton(song.favorite, { onFavorite() }, Modifier.testTag("favorite-${song.id}")) { Icon(if (song.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite ${song.title}", tint = if (song.favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
}

@Composable
fun LibraryScreen(dao: ChordDao, all: List<Song>, prefs: Preferences, open: (Song) -> Unit, favorite: (Song) -> Unit, add: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }; var settled by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("All songs") }; var sort by rememberSaveable { mutableStateOf(LibrarySort.TITLE) }
    var artist by rememberSaveable { mutableStateOf("") }; var tag by rememberSaveable { mutableStateOf("") }; var key by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf(false) }; var limit by rememberSaveable { mutableIntStateOf(100) }
    val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    fun reset() { limit = 100; scope.launch(Dispatchers.Main.immediate) { list.scrollToItem(0) } }
    LaunchedEffect(query) { if (settled != query) { delay(160); settled = query; reset() } }
    val filter = LibraryFilter(tab == "Favorites", artist, tag, key, tab == "Recent")
    val activeSort = if (tab == "Recent" && sort == LibrarySort.TITLE) LibrarySort.OPENED else sort
    val page by remember(settled, filter, activeSort, limit) { dao.libraryResults(LibrarySearch.query(settled, filter, activeSort, limit)).map<List<LibraryHit>, List<LibraryHit>?> { it } }.collectAsState(null, context = Dispatchers.Main.immediate)
    val results = page.orEmpty()
    val count by remember(settled, filter) { dao.libraryResultCount(LibrarySearch.query(settled, filter, activeSort, count = true)) }.collectAsState(0, context = Dispatchers.Main.immediate)
    LaunchedEffect(list, results.size, count) { snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (results.isNotEmpty() && it >= results.size - 12 && limit < count) limit += 100 } }
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val wide = window.width > window.height
    @Composable fun SearchField(modifier: Modifier) { OutlinedTextField(query, { query = it.take(256) }, modifier.testTag("library-search"), placeholder = { Text("Find a song, artist or lyric", fontSize = 14.sp) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }), shape = RoundedCornerShape(16.dp)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (wide) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.widthIn(max = 210.dp)) { Text("Your library",fontFamily = Serif,fontSize = 24.sp); Text("${all.size} songs · All offline",fontSize = 11.sp,color = MaterialTheme.colorScheme.onSurfaceVariant) }
            SearchField(Modifier.weight(1f))
            FilledTonalIconButton(add) { Icon(Icons.Outlined.Add,"New song") }
        } else {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Your library", fontFamily = Serif, fontSize = 30.sp, lineHeight = 36.sp); Text("${all.size} songs · All offline", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            FilledTonalIconButton(add) { Icon(Icons.Outlined.Add, "New song") }
        }
            SearchField(Modifier.fillMaxWidth())
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All songs", "Favorites", "Recent").forEach { value -> FilterChip(tab == value, { tab = value; reset() }, { Text(value, fontSize = 12.sp) }) }
            IconButton({ options = true }, Modifier.testTag("library-options")) { Icon(Icons.Outlined.Tune, "Sort and filter library") }
        }
        val countLabel = if (page == null) "Loading local songs…" else "$count " + (if (settled.isBlank()) { if (count == 1) "song" else "songs" } else { if (count == 1) "match · Best matches first" else "matches · Best matches first" }) + if (artist.isNotEmpty() || tag.isNotEmpty() || key.isNotEmpty()) " · Filtered" else ""
        Text(countLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp).testTag("result-count").semantics { stateDescription = settled })
        // Do not measure an empty list before Room emits: it would discard the restored scroll anchor.
        if (page == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(Modifier.fillMaxSize().testTag("library-list"), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.song.id }, contentType = { "song" }) { hit ->
                val preview by produceState<String?>(null, hit.song.id, settled, hit.lyricsMatch) {
                    val text = if (hit.lyricsMatch) withContext(Dispatchers.IO) { dao.excerpt(hit.song.id, SearchText.terms(settled).firstOrNull()?.take(64).orEmpty()) } else null
                    withContext(Dispatchers.Main.immediate) { value = text }
                }
                SongRow(hit.song, { open(hit.song) }, { favorite(hit.song) }, query = settled, excerpt = preview, spelling = prefs.spelling)
            }
            if (count == 0 && query == settled) item {
                EmptyState(if (query.isNotEmpty()) "No matches yet" else if (tab == "Favorites") "Save your session favorites" else if (tab == "Recent") "A fresh start" else "Make this library yours", if (query.isNotEmpty()) "Try a shorter phrase, artist, album or tag." else if (tab == "Favorites") "Tap a heart beside any song. Your favorites stay on this device." else if (tab == "Recent") "Songs you open will appear here." else "Import a folder of songs or write your first chord sheet.", if (query.isNotEmpty() || tab != "All songs" || filter != LibraryFilter()) "Show all songs" else "Write a song", { if (query.isNotEmpty() || tab != "All songs" || filter != LibraryFilter()) { query = ""; tab = "All songs"; artist = ""; tag = ""; key = ""; reset() } else add() })
            }
            if (limit < count) item { TextButton({ limit += 100 }, Modifier.fillMaxWidth()) { Text("Load more songs") } }
        }
    }
    if (options) LibraryOptions(all, sort, artist, tag, key, { options = false }) { s, a, t, k -> sort = s; artist = a; tag = t; key = k; options = false; reset() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryOptions(all: List<Song>, initial: LibrarySort, initialArtist: String, initialTag: String, initialKey: String, dismiss: () -> Unit, apply: (LibrarySort, String, String, String) -> Unit) {
    var sort by remember { mutableStateOf(initial) }; var artist by remember { mutableStateOf(initialArtist) }; var tag by remember { mutableStateOf(initialTag) }; var key by remember { mutableStateOf(initialKey) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Sort & filter", fontFamily = Serif) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Sort", fontWeight = FontWeight.Bold)
        LibrarySort.entries.forEach { value -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { sort = value }, verticalAlignment = Alignment.CenterVertically) { RadioButton(sort == value, null); Text(value.title) } }
        Text("Filter", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        OutlinedTextField(artist, { artist = it }, label = { Text("Artist (exact name)") }, singleLine = true)
        if (artist.isNotEmpty()) FlowRow { all.map { it.artist }.distinct().filter { SearchText.normalize(it).contains(SearchText.normalize(artist)) && it != artist }.take(4).forEach { name -> SuggestionChip({ artist = name }, { Text(name, maxLines = 1) }) } }
        OutlinedTextField(tag, { tag = it }, label = { Text("Tag (exact spelling)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) { all.flatMap { it.tags() }.distinct().filter { tag.isEmpty() || it.contains(tag, true) }.take(6).forEach { value -> FilterChip(tag == value, { tag = if (tag == value) "" else value }, { Text(value, maxLines = 1) }) } }
        OutlinedTextField(key, { key = it }, label = { Text("Original key (e.g. C, Am)") }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
        TextButton({ artist = ""; tag = ""; key = "" }) { Text("Clear filters") }
    } }, confirmButton = { TextButton({ apply(sort, artist.trim(), tag.trim(), key.trim()) }) { Text("Apply") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
