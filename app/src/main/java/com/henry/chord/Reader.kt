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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
fun ReaderScreen(song: Song, prefs: Preferences, songs: List<Song>, dao: ChordDao, back: () -> Unit, edit: () -> Unit, next: (Song) -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val uriHandler = LocalUriHandler.current
    val scroll = rememberLazyListState()
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(song.transpose) }
    var pendingTransposeWrites by remember(song.id) { mutableIntStateOf(0) }
    val transposeWrites=remember(song.id) { Mutex() }
    LaunchedEffect(song.transpose) { if (pendingTransposeWrites==0) transpose=song.transpose }
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    val compactHeader = windowSize.width > windowSize.height || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val darkSurface=MaterialTheme.colorScheme.background.luminance()<.3f
    var linksOpen by remember { mutableStateOf(false) }
    val document = remember(song.documentJson) { runCatching { Canonical.decode(song.documentJson).single().document }.getOrNull() }
    val canonical = remember(song.documentJson, song.body) { runCatching { Canonical.decode(song.documentJson).single().document.arrangements.single() }.getOrElse { SheetParser.parse(song.body, "song.cho").document.arrangements.single() } }
    var fullscreen by rememberSaveable { mutableStateOf(false) }; var scrolling by remember { mutableStateOf(false) }; val font = prefs.fontSize; val labelMode = prefs.chordLabels; var selectedChord by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf("") }
    val speed=rememberScrollSpeed(prefs.scrollSpeed) { value -> scope.launch { Settings(context).saveScrollSpeed(value) } }
    val appliedSpeed by rememberUpdatedState(speed.applied)
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = remember { FocusRequester() }
    fun savePreference(change: (Preferences)->Preferences) { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { Settings(context).update(change) } }
    fun setTranspose(value: Int) {
        val selected=value.coerceIn(-24,24); transpose=selected; pendingTransposeWrites++
        scope.launch {
            try { transposeWrites.withLock { dao.saveOffsets(ArrangementUserState(song.id,selected,song.capo)) } }
            catch (e: Exception) { message="Could not save transpose: ${e.message}"; transpose=song.transpose }
            finally { pendingTransposeWrites-- }
        }
    }
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
    val window = (context as? Activity)?.window
    val nativeView=androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(transpose,labelMode,scrolling,fullscreen,countdown) {
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
    LaunchedEffect(scrolling, density) {
        var previous = withFrameNanos { it }
        while (scrolling) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / 1_000_000_000.0).coerceIn(0.0,0.1)
            previous = now
            if (!scroll.canScrollForward) { scrolling = false; break }
            scroll.scrollBy((appliedSpeed * density * elapsed).toFloat())
        }
    }
    val keyContext = KeyContext.from(canonical.originalKey, canonical.tonalMode)
    val key = keyContext?.let { Theory.currentKey(it, transpose, prefs.spelling).name } ?: song.key.takeIf { it.isNotBlank() }?.let { Theory.transpose(it, transpose, spelling = prefs.spelling) } ?: "Unknown"
    val currentContext = keyContext?.let { Theory.currentKey(it, transpose, prefs.spelling) }
    val chords = canonical.sections.flatMap { it.lines }.flatMap { it.segments }.mapNotNull { it.anchor?.rawChord }.distinct()
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
                        TextButton({ setTranspose(0) },enabled=transpose!=0) { Text("Reset",fontSize=12.sp) }
                    }
                }
                if (keyContext == null) Text("Key required for Roman labels. Add it in Edit song.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!compactHeader) Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEach { raw -> key(raw,transpose,labelMode) { AssistChip({ selectedChord = sounding(raw) }, { Text(label(raw), fontWeight = FontWeight.SemiBold) }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription="Chord ${label(raw)}. Sounding ${sounding(raw)}" }) } }
                }
            }
        }
        if (fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("${if (transpose>0) "+" else ""}$transpose semitones",fontSize=11.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("fullscreen-transpose")); Text(song.title,maxLines=1,fontWeight=FontWeight.SemiBold) }
            IconButton({ setTranspose(transpose-1) }) { Icon(Icons.Outlined.Remove,"Transpose down") }
            Text("KEY $key",fontWeight=FontWeight.Bold,fontSize=12.sp)
            IconButton({ setTranspose(transpose+1) }) { Icon(Icons.Outlined.Add,"Transpose up") }
        }
        LazyColumn(Modifier.weight(1f).widthIn(max=840.dp).fillMaxWidth().align(Alignment.CenterHorizontally).padding(horizontal = prefs.sheetMargin.dp).testTag("reader-scroll").semantics { contentDescription="Song sheet"; stateDescription = "${scroll.firstVisibleItemIndex}:${scroll.firstVisibleItemScrollOffset}" }, state = scroll, contentPadding = PaddingValues(vertical = if (fullscreen) 16.dp else 8.dp)) {
            if (fullscreen) item { Text(song.title, fontFamily = Serif, fontSize = 25.sp); Spacer(Modifier.height(24.dp)) }
            canonical.sections.forEach { section ->
                item(key = "section-${section.id}", contentType = "section") { SectionBadge(section,prefs.sectionGap) }
                section.lines.forEach { line -> item(key = "line-${line.id}", contentType = "sheet-line") {
                    Box(Modifier.fillMaxWidth().background(sectionBackground(section.type).copy(alpha=.28f)).padding(horizontal=6.dp)) {
                        key(transpose,labelMode) { CanonicalSheetLine(line, font, label, sounding, { selectedChord = it }, SheetDisplay.BOTH,prefs.lineSpacing,readerChordColor(prefs.chordColor),prefs.chordLineGap,prefs.songLineGap) }
                    }
                } }
            }
            item {
            if (song.notes.isNotBlank()) { Spacer(Modifier.height(24.dp)); Eyebrow("Your notes"); Text(song.notes, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            if (song.source.startsWith("https://") || song.source.startsWith("http://")) TextButton({ runCatching { uriHandler.openUri(song.source) }.onFailure { message = "No browser is available." } }) { Text("Open source link"); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)) }
            Spacer(Modifier.height(50.dp)); Eyebrow("End of song")
            }
        }
        if (message.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), fontSize = 12.sp); TextButton({ message = "" }) { Text("Dismiss") } }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column {
                if (showSpeed) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(if (countdown > 0) "$countdown…" else "Speed", fontSize = 12.sp); Slider(speed.requested, { speed.requested = it }, Modifier.weight(1f).padding(horizontal = 8.dp).testTag("scroll-speed"), valueRange = 8f..100f); Text("${speed.requested.toInt()} dp/s", fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    key(scrolling,countdown) {
                        IconButton({ toggleScroll() }) { Icon(if (scrolling || countdown > 0) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (scrolling || countdown > 0) "Pause auto-scroll" else "Start auto-scroll") }
                        Text(if (scrolling) "Playing" else "Paused", fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton({ savePreference { it.copy(fontSize=(it.fontSize-1).coerceAtLeast(14)) } }, enabled = font > 14) { Text("A−", fontSize = 14.sp) }
                    IconButton({ savePreference { it.copy(fontSize=(it.fontSize+1).coerceAtMost(30)) } }, enabled = font < 30) { Text("A+", fontSize = 19.sp) }
                    IconButton({ fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fullscreen) "Exit fullscreen" else "Fullscreen reader") }
                }

            }
        }
    }
    if (linksOpen) SongLinksDialog(song, document?.links ?: MusicLinks(), { linksOpen = false }) { url -> if (!SongLinks.open(context, url)) message = "No app can open this link. Install a browser or the corresponding music app." }
    if (selectedChord != null) ChordDialog(selectedChord!!, currentContext, prefs.instrument, { selectedChord = null })
}
