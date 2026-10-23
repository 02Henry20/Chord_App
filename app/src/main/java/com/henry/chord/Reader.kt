package com.henry.chord

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(song: Song, prefs: Preferences, sets: List<Setlist>, entries: List<SetlistEntry>, songs: List<Song>, setId: Long?, dao: ChordDao, back: () -> Unit, edit: () -> Unit, next: (Song) -> Unit, queueEntryId: String? = null, openQueue: (SetlistEntry) -> Unit = {}) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val uriHandler = LocalUriHandler.current
    val scroll = rememberLazyListState()
    val queue = entries.filter { it.setlistId == setId }.sortedBy { it.position }
    val queueIndex = if (queueEntryId != null) queue.indexOfFirst { it.entryId == queueEntryId } else queue.indexOfFirst { it.songId == song.id }
    val currentEntry=queue.getOrNull(queueIndex)
    val transpose=currentEntry?.transposeOverride ?: song.transpose
    fun setTranspose(value: Int) { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { if (currentEntry?.transposeOverride != null) dao.updateEntry(currentEntry.copy(transposeOverride=value.coerceIn(-24,24))) else dao.saveOffsets(ArrangementUserState(song.id,value.coerceIn(-24,24),song.capo)) } }
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val compactHeader = windowSize.width > windowSize.height || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val darkSurface=MaterialTheme.colorScheme.background.luminance()<.3f
    var linksOpen by remember { mutableStateOf(false) }
    val document = remember(song.documentJson) { runCatching { Canonical.decode(song.documentJson).single().document }.getOrNull() }
    val canonical = remember(song.documentJson, song.body) { runCatching { Canonical.decode(song.documentJson).single().document.arrangements.single() }.getOrElse { SheetParser.parse(song.body, "song.cho").document.arrangements.single() } }
    var fullscreen by rememberSaveable { mutableStateOf(setId != null) }; var scrolling by remember { mutableStateOf(false) }; var speed by rememberSaveable { mutableFloatStateOf(prefs.scrollSpeed) }; val font = prefs.fontSize; var labelMode by rememberSaveable(song.id, prefs.chordLabels) { mutableStateOf(prefs.chordLabels) }; var selectedChord by remember { mutableStateOf<String?>(null) }; var addToSet by remember { mutableStateOf(false) }; var controls by rememberSaveable { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    var readerOptions by remember { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = remember { FocusRequester() }
    fun savePreference(value: Preferences) { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { Settings(context).save(value) } }
    fun toggleScroll() { if (scrolling || countdown > 0) { scrolling = false; countdown = 0 } else if (prefs.leadIn > 0) countdown = prefs.leadIn else scrolling = true; showSpeed = true }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(dragged) { if (dragged) { scrolling = false; countdown = 0 } }
    LaunchedEffect(selectedChord) { if (selectedChord != null) { scrolling = false; countdown = 0 } }
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); if (countdown == 1) { countdown = 0; scrolling = true } else countdown-- } }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { scrolling = false; countdown = 0 } }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { message = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(SongImport.export(song).toByteArray(Charsets.UTF_8)) } ?: error("Cannot write file") }; "Song exported" }.getOrElse { "Export failed: ${it.message}" } }
    }
    val exportJson=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
        message=runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(Canonical.encode(document ?: ChordDao.legacyDocument(song)).toByteArray(Charsets.UTF_8)) } ?: error("Cannot write JSON.") }; "Native JSON exported. Artwork files are included in ZIP backups." }.getOrElse { "Export failed: ${it.message}" }
    } }
    val window = (context as? Activity)?.window
    val nativeView=androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(transpose,labelMode,scrolling,fullscreen,countdown,speed,prefs.sheetDisplay) {
        // Dispatch after layout: native accessibility clients must see the newly applied semantics.
        withFrameNanos { }; withFrameNanos { }
        val event=android.view.accessibility.AccessibilityEvent.obtain(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        event.contentChangeTypes=android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        nativeView.sendAccessibilityEventUnchecked(event)
    }
    DisposableEffect(prefs.keepAwake) {
        if (prefs.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(fullscreen) {
        window?.let { val controller = WindowCompat.getInsetsController(it, it.decorView); controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars()) }
        onDispose { window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    BackHandler(fullscreen) { fullscreen = false }
    LaunchedEffect(scrolling, speed, density) {
        var previous = withFrameNanos { it }
        while (scrolling) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / 1_000_000_000.0).coerceIn(0.0,0.1)
            previous = now
            if (!scroll.canScrollForward) { scrolling = false; break }
            scroll.scrollBy((speed * density * elapsed).toFloat())
        }
    }
    val keyContext = KeyContext.from(canonical.originalKey, canonical.tonalMode)
    val key = keyContext?.let { Theory.currentKey(it, transpose, prefs.spelling).name } ?: song.key.takeIf { it.isNotBlank() }?.let { Theory.transpose(it, transpose, spelling = prefs.spelling) } ?: "Unknown"
    val currentContext = keyContext?.let { Theory.currentKey(it, transpose, prefs.spelling) }
    val chords = canonical.sections.flatMap { it.lines }.flatMap { it.segments }.mapNotNull { it.anchor?.rawChord }.distinct()
    var setKey by remember { mutableStateOf(false) }
    val sounding: (String) -> String = remember(transpose,keyContext,prefs.spelling) { { raw -> Theory.transpose(raw,transpose,keyContext,prefs.spelling) } }
    val label: (String) -> String = remember(transpose,keyContext,labelMode,prefs.spelling) { { raw -> Theory.label(raw,transpose,keyContext,labelMode,prefs.spelling) } }
    val versions = songs.filter { it.songUuid == song.songUuid }
    var versionMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent { event ->
        if (event.key == Key.Spacebar && event.type == KeyEventType.KeyDown) { toggleScroll(); true } else false
    }.focusable()) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.Outlined.ArrowBack, "Back") }; Spacer(Modifier.weight(1f))
                IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(favorite = !song.favorite)) } }) { Icon(if (song.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite song", tint = MaterialTheme.colorScheme.primary) }
                IconButton({ addToSet = true }) { Icon(Icons.Outlined.PlaylistAdd, "Add to setlist") }
                IconButton({ linksOpen = true }) { Icon(Icons.Outlined.Link, "Song links") }
                IconButton(edit) { Icon(Icons.Outlined.EditNote, "Edit song") }
                IconButton({ export.launch("${song.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.cho") }) { Icon(Icons.Outlined.IosShare, "Export song") }
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                ReaderHeader(song.copy(transpose=transpose), compactHeader)
                if (versions.size > 1) Box { TextButton({ versionMenu = true }) { Text("${song.arrangementName} · ${versions.size} versions"); Icon(Icons.Outlined.ExpandMore, null) }; DropdownMenu(versionMenu, { versionMenu = false }) { versions.forEach { version -> DropdownMenuItem(text = { Text("${version.arrangementName} · ${version.key.ifBlank { "Unknown key" }} · ${version.arrangementUuid.take(6)}") }, onClick = { versionMenu = false; next(version) }) } } }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ setTranspose(transpose-1) }) { Icon(Icons.Outlined.Remove, "Transpose down") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("KEY $key", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("${if (transpose > 0) "+" else ""}${transpose} semitones", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({ setTranspose(transpose+1) }) { Icon(Icons.Outlined.Add, "Transpose up") }
                        VerticalDivider(Modifier.height(30.dp))
                        TextButton({ controls = !controls }) { Text("More", fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                    }
                }
                if (controls) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Capo", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo - 1).coerceAtLeast(0))) } }, enabled = song.capo > 0) { Icon(Icons.Outlined.Remove, "Lower capo") }; Text("${song.capo}", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo + 1).coerceAtMost(12))) } }, enabled = song.capo < 12) { Icon(Icons.Outlined.Add, "Raise capo") }
                        Spacer(Modifier.weight(1f)); TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { if (currentEntry?.transposeOverride != null) dao.updateEntry(currentEntry.copy(transposeOverride=0)); dao.saveOffsets(ArrangementUserState(song.id,if (currentEntry?.transposeOverride != null) song.transpose else 0,0)) } }) { Text("Reset original key") }
                    }
                    Text("Concert key: $key · capo ${song.capo} is a guitar shape aid. Symbols and piano always show sounding pitches.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ setKey = true }) { Text("Set key / mode") }
                }
                if (keyContext == null) TextButton({ setKey = true }) { Text("Key required · Set key / mode") }
                if (!compactHeader) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEach { raw -> key(raw,transpose,labelMode) { AssistChip({ selectedChord = sounding(raw) }, { Text(label(raw), fontWeight = FontWeight.SemiBold) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription="Chord ${label(raw)}. Sounding ${sounding(raw)}" }) } }
                }
            }
        }
        if (fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(song.title,Modifier.weight(1f),maxLines=1,fontWeight=FontWeight.SemiBold)
            IconButton({ setTranspose(transpose-1) }) { Icon(Icons.Outlined.Remove,"Transpose down") }
            Text("KEY $key",fontWeight=FontWeight.Bold,fontSize=12.sp)
            IconButton({ setTranspose(transpose+1) }) { Icon(Icons.Outlined.Add,"Transpose up") }
        }
        LazyColumn(Modifier.weight(1f).widthIn(max=840.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = 24.dp).testTag("reader-scroll").semantics { contentDescription="Song sheet"; stateDescription = "${scroll.firstVisibleItemIndex}:${scroll.firstVisibleItemScrollOffset}" }, state = scroll, contentPadding = PaddingValues(vertical = if (fullscreen) 28.dp else 10.dp)) {
            if (fullscreen) item { Text(song.title, fontFamily = Serif, fontSize = 25.sp); Spacer(Modifier.height(24.dp)) }
            canonical.sections.forEach { section ->
                if (section.label.isNotEmpty()) item(key = "section-${section.id}") { Spacer(Modifier.height(14.dp)); Text(section.label.uppercase(), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
                section.lines.forEach { line -> item(key = "line-${line.id}") { key(transpose,labelMode,prefs.sheetDisplay) { CanonicalSheetLine(line, font, label, sounding, { selectedChord = it }, prefs.sheetDisplay,prefs.lineSpacing,
                    when (prefs.chordColor) { "Blue" -> if (darkSurface) Color(0xFFA8C7FA) else Color(0xFF174EA6); "Amber" -> if (darkSurface) Color(0xFFFFD180) else Color(0xFF795000); else -> MaterialTheme.colorScheme.primary }) } } }
            }
            item {
            if (currentEntry?.notes?.isNotBlank() == true) Text(currentEntry.notes,modifier=Modifier.padding(vertical=12.dp)); if (song.notes.isNotBlank()) { Spacer(Modifier.height(24.dp)); Eyebrow("Your notes"); Text(song.notes, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            if (song.source.startsWith("https://") || song.source.startsWith("http://")) TextButton({ runCatching { uriHandler.openUri(song.source) }.onFailure { message = "No browser is available." } }) { Text("Open source link"); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)) }
            Spacer(Modifier.height(50.dp)); Eyebrow("End of song")
            }
        }
        if (message.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), fontSize = 12.sp); TextButton({ message = "" }) { Text("Dismiss") } }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                if (showSpeed) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (countdown > 0) "$countdown…" else "Speed", fontSize = 12.sp); Slider(speed, { speed = it }, Modifier.weight(1f).padding(horizontal = 8.dp).testTag("scroll-speed"), valueRange = 8f..100f,onValueChangeFinished = { savePreference(prefs.copy(scrollSpeed=speed)) }); Text("${speed.toInt()} dp/s", fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(scrolling,countdown) {
                        IconButton({ toggleScroll() }) { Icon(if (scrolling || countdown > 0) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (scrolling || countdown > 0) "Pause auto-scroll" else "Start auto-scroll") }
                        Text(if (scrolling) "Playing" else "Paused", fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton({ savePreference(prefs.copy(fontSize=(font-1).coerceAtLeast(14))) }, enabled = font > 14) { Text("A−", fontSize = 14.sp) }
                    IconButton({ savePreference(prefs.copy(fontSize=(font+1).coerceAtMost(30))) }, enabled = font < 30) { Text("A+", fontSize = 19.sp) }
                    IconButton({ labelMode = LabelMode.entries[(labelMode.ordinal + 1) % LabelMode.entries.size] }, Modifier.semantics { contentDescription = "Chord labels: ${labelMode.title}. Change for this reader" }) { Text(when (labelMode) { LabelMode.SYMBOLS -> "C–G"; LabelMode.ROMAN -> "I–V"; LabelMode.BOTH -> "C (I)" }, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    IconButton({ fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fullscreen) "Exit fullscreen" else "Fullscreen reader") }
                    IconButton({ readerOptions = true; scrolling = false; countdown = 0 }) { Icon(Icons.Outlined.MoreVert,"Reader options") }
                }
                if (queueIndex >= 0) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ scrolling = false; openQueue(queue[queueIndex - 1]) }, enabled = queueIndex > 0) { Icon(Icons.Outlined.SkipPrevious, "Previous song") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(sets.find { it.id == setId }?.name ?: "Setlist", fontSize = 12.sp); Text("${queueIndex + 1} / ${queue.size}", fontSize = 10.sp) }
                    IconButton({ scrolling = false; openQueue(queue[queueIndex + 1]) }, enabled = queueIndex < queue.lastIndex) { Icon(Icons.Outlined.SkipNext, "Next song") }
                }
            }
        }
    }
    if (readerOptions) AlertDialog(onDismissRequest={ readerOptions=false },title={ Text("Reader options") },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        TextButton({ exportJson.launch("song.json"); readerOptions=false }) { Text("Export native JSON") }
        TextButton({
            val text=listOf(song.title,song.artist,document?.links?.spotify?.takeIf { SongLinks.valid(it,LinkService.SPOTIFY) != null },document?.links?.youtube?.takeIf { SongLinks.valid(it,LinkService.YOUTUBE) != null }).filterNotNull().joinToString("\n")
            runCatching { context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,text),"Share song details")) }.onFailure { message="No sharing app available." }; readerOptions=false
        }) { Text("Share title, artist & links") }
        SheetDisplay.entries.forEach { value -> TextButton({ savePreference(prefs.copy(sheetDisplay=value)) }) { Text((if (prefs.sheetDisplay == value) "✓ " else "") + value.title) } }
        TextButton({ scrolling=false; countdown=0; scope.launch { scroll.scrollToItem(0) }; readerOptions=false }) { Text("Return to top") }
        TextButton({ showSpeed=!showSpeed; readerOptions=false }) { Text("Show / hide speed") }
        canonical.sections.forEach { section -> TextButton({
            var index = if (fullscreen) 1 else 0
            for (s in canonical.sections) { if (s.id == section.id) break; index += s.lines.size + if (s.label.isNotEmpty()) 1 else 0 }
            scope.launch { scroll.scrollToItem(index) }; readerOptions=false
        }) { Text("Jump to ${section.label.ifBlank { section.type }}") } }
    } },confirmButton={ TextButton({ readerOptions=false }) { Text("Done") } })
    if (linksOpen) SongLinksDialog(song, document?.links ?: MusicLinks(), { linksOpen = false }) { url -> if (!SongLinks.open(context, url)) message = "No app can open this link. Install a browser or the corresponding music app." }
    if (selectedChord != null) ChordDialog(selectedChord!!, currentContext, prefs.instrument, song.capo, { instrument -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { Settings(context).save(prefs.copy(instrument = instrument)) } }, { selectedChord = null })
    if (setKey) SetKeyDialog(canonical.originalKey.orEmpty(), canonical.tonalMode, { setKey = false }) { original, mode ->
        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            runCatching {
                val document = Canonical.decode(song.documentJson).single().document
                dao.store(document.copy(arrangements = listOf(canonical.copy(originalKey = original, tonalMode = mode))), song.id)
            }.onSuccess { setKey = false }.onFailure { message = it.message ?: "Could not save key" }
        }
    }
    if (addToSet) AlertDialog(onDismissRequest = { addToSet = false }, title = { Text("Add to setlist", fontFamily = Serif) }, text = { Column { if (sets.isEmpty()) Text("Create a setlist from the Setlists tab first."); sets.forEach { set -> TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.addEntry(SetlistEntry(set.id, song.id, (entries.filter { it.setlistId == set.id }.maxOfOrNull { it.position } ?: -1) + 1)) }; addToSet = false; message = "Added to ${set.name}" }) { Text(set.name) } } } }, confirmButton = { TextButton({ addToSet = false }) { Text("Done") } })
}
