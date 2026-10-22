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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
fun ReaderScreen(song: Song, prefs: Preferences, sets: List<Setlist>, entries: List<SetlistEntry>, songs: List<Song>, setId: Long?, dao: ChordDao, back: () -> Unit, edit: () -> Unit, next: (Song) -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current; val uriHandler = LocalUriHandler.current
    val scroll = rememberLazyListState()
    val canonical = remember(song.documentJson, song.body) { runCatching { Canonical.decode(song.documentJson).single().document.arrangements.single() }.getOrElse { SheetParser.parse(song.body, "song.cho").document.arrangements.single() } }
    var fullscreen by rememberSaveable { mutableStateOf(false) }; var scrolling by rememberSaveable { mutableStateOf(false) }; var speed by rememberSaveable { mutableFloatStateOf(22f) }; var font by rememberSaveable { mutableIntStateOf(prefs.fontSize) }; var labelMode by rememberSaveable(song.id, prefs.chordLabels) { mutableStateOf(prefs.chordLabels) }; var selectedChord by remember { mutableStateOf<String?>(null) }; var addToSet by remember { mutableStateOf(false) }; var controls by rememberSaveable { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { message = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(SongImport.export(song).toByteArray()) } ?: error("Cannot write file"); "Song exported" }.getOrElse { "Export failed: ${it.message}" } }
    }
    val window = (context as? Activity)?.window
    DisposableEffect(prefs.keepAwake) {
        if (prefs.keepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(fullscreen) {
        window?.let { val controller = WindowCompat.getInsetsController(it, it.decorView); controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars()) }
        onDispose { window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) } }
    }
    BackHandler(fullscreen) { fullscreen = false }
    LaunchedEffect(scrolling, speed) {
        while (scrolling) {
            delay(32)
            if (!scroll.canScrollForward) { scrolling = false; break }
            scroll.scrollBy(speed * .032f)
        }
    }
    val keyContext = KeyContext.from(canonical.originalKey, canonical.tonalMode)
    val key = keyContext?.let { Theory.currentKey(it, song.transpose, prefs.spelling).name } ?: song.key.takeIf { it.isNotBlank() }?.let { Theory.transpose(it, song.transpose, spelling = prefs.spelling) } ?: "Unknown"
    val currentContext = keyContext?.let { Theory.currentKey(it, song.transpose, prefs.spelling) }
    val chords = canonical.sections.flatMap { it.lines }.flatMap { it.segments }.mapNotNull { it.anchor?.rawChord }.distinct()
    var setKey by remember { mutableStateOf(false) }
    fun sounding(raw: String) = Theory.transpose(raw, song.transpose, keyContext, prefs.spelling)
    fun label(raw: String) = Theory.label(raw, song.transpose, keyContext, labelMode, prefs.spelling)
    val versions = songs.filter { it.songUuid == song.songUuid }
    var versionMenu by remember { mutableStateOf(false) }
    val queue = entries.filter { it.setlistId == setId }.sortedBy { it.position }.mapNotNull { e -> songs.find { it.id == e.songId } }
    val queueIndex = queue.indexOfFirst { it.id == song.id }
    Column(Modifier.fillMaxSize()) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.Outlined.ArrowBack, "Back") }; Spacer(Modifier.weight(1f))
                IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(favorite = !song.favorite)) } }) { Icon(if (song.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Favorite song", tint = MaterialTheme.colorScheme.primary) }
                IconButton({ addToSet = true }) { Icon(Icons.Outlined.PlaylistAdd, "Add to setlist") }
                IconButton(edit) { Icon(Icons.Outlined.EditNote, "Edit song") }
                IconButton({ export.launch("${song.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "")}.cho") }) { Icon(Icons.Outlined.IosShare, "Export song") }
            }
            Column(Modifier.padding(horizontal = 24.dp)) {
                Eyebrow("Your songbook")
                Text(song.title, fontFamily = Serif, fontSize = 32.sp, lineHeight = 38.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(song.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
                if (versions.size > 1) Box { TextButton({ versionMenu = true }) { Text("${song.arrangementName} · ${versions.size} versions"); Icon(Icons.Outlined.ExpandMore, null) }; DropdownMenu(versionMenu, { versionMenu = false }) { versions.forEach { version -> DropdownMenuItem(text = { Text("${version.arrangementName} · ${version.key.ifBlank { "Unknown key" }} · ${version.arrangementUuid.take(6)}") }, onClick = { versionMenu = false; next(version) }) } } }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(transpose = song.transpose - 1)) } }) { Icon(Icons.Outlined.Remove, "Transpose down") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("KEY $key", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("${if (song.transpose > 0) "+" else ""}${song.transpose} semitones", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(transpose = song.transpose + 1)) } }) { Icon(Icons.Outlined.Add, "Transpose up") }
                        VerticalDivider(Modifier.height(30.dp))
                        TextButton({ controls = !controls }) { Text("More", fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                    }
                }
                if (controls) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Capo", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo - 1).coerceAtLeast(0))) } }, enabled = song.capo > 0) { Icon(Icons.Outlined.Remove, "Lower capo") }; Text("${song.capo}", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo + 1).coerceAtMost(12))) } }, enabled = song.capo < 12) { Icon(Icons.Outlined.Add, "Raise capo") }
                        Spacer(Modifier.weight(1f)); TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = 0, transpose = 0)) } }) { Text("Reset original key") }
                    }
                    Text("Concert key: $key · capo ${song.capo} is a guitar shape aid. Symbols and piano always show sounding pitches.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ setKey = true }) { Text("Set key / mode") }
                }
                if (keyContext == null) TextButton({ setKey = true }) { Text("Key required · Set key / mode") }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEach { raw -> AssistChip({ selectedChord = sounding(raw) }, { Text(label(raw), fontWeight = FontWeight.SemiBold) }, modifier = Modifier.heightIn(min = 48.dp)) }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), state = scroll, contentPadding = PaddingValues(vertical = if (fullscreen) 28.dp else 10.dp)) {
            if (fullscreen) item { Text(song.title, fontFamily = Serif, fontSize = 25.sp); Spacer(Modifier.height(24.dp)) }
            canonical.sections.forEach { section ->
                if (section.label.isNotEmpty()) item(key = "section-${section.id}") { Spacer(Modifier.height(14.dp)); Text(section.label.uppercase(), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
                section.lines.forEach { line -> item(key = "line-${line.id}") { CanonicalSheetLine(line, font, ::label, ::sounding) { selectedChord = it } } }
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
                if (scrolling) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Speed", fontSize = 12.sp); Slider(speed, { speed = it }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 8f..70f); Text("${speed.toInt()}", fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ scrolling = !scrolling }) { Icon(if (scrolling) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (scrolling) "Pause auto-scroll" else "Start auto-scroll") }
                    Text("Auto-scroll", fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton({ font = (font - 1).coerceAtLeast(14) }, enabled = font > 14) { Text("A−", fontSize = 14.sp) }
                    IconButton({ font = (font + 1).coerceAtMost(30) }, enabled = font < 30) { Text("A+", fontSize = 19.sp) }
                    IconButton({ labelMode = LabelMode.entries[(labelMode.ordinal + 1) % LabelMode.entries.size] }, Modifier.semantics { contentDescription = "Chord labels: ${labelMode.title}. Change for this reader" }) { Text(when (labelMode) { LabelMode.SYMBOLS -> "C–G"; LabelMode.ROMAN -> "I–V"; LabelMode.BOTH -> "C (I)" }, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    IconButton({ fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fullscreen) "Exit fullscreen" else "Fullscreen reader") }
                }
                if (queueIndex >= 0) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ scrolling = false; next(queue[queueIndex - 1]) }, enabled = queueIndex > 0) { Icon(Icons.Outlined.SkipPrevious, "Previous song") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(sets.find { it.id == setId }?.name ?: "Setlist", fontSize = 12.sp); Text("${queueIndex + 1} / ${queue.size}", fontSize = 10.sp) }
                    IconButton({ scrolling = false; next(queue[queueIndex + 1]) }, enabled = queueIndex < queue.lastIndex) { Icon(Icons.Outlined.SkipNext, "Next song") }
                }
            }
        }
    }
    if (selectedChord != null) ChordDialog(selectedChord!!, currentContext, prefs.instrument, song.capo, { instrument -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { Settings(context).save(prefs.copy(instrument = instrument)) } }, { selectedChord = null })
    if (setKey) SetKeyDialog(canonical.originalKey.orEmpty(), canonical.tonalMode, { setKey = false }) { original, mode ->
        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            runCatching {
                val document = Canonical.decode(song.documentJson).single().document
                dao.store(document.copy(arrangements = listOf(canonical.copy(originalKey = original, tonalMode = mode))), song.id)
            }.onSuccess { setKey = false }.onFailure { message = it.message ?: "Could not save key" }
        }
    }
    if (addToSet) AlertDialog(onDismissRequest = { addToSet = false }, title = { Text("Add to setlist", fontFamily = Serif) }, text = { Column { if (sets.isEmpty()) Text("Create a setlist from the Setlists tab first."); sets.forEach { set -> val exists = entries.any { it.setlistId == set.id && it.songId == song.id }; TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.addEntry(SetlistEntry(set.id, song.id, (entries.filter { it.setlistId == set.id }.maxOfOrNull { it.position } ?: -1) + 1)) }; addToSet = false; message = "Added to ${set.name}" }, enabled = !exists) { Text(set.name + if (exists) " · Added" else "") } } } }, confirmButton = { TextButton({ addToSet = false }) { Text("Done") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanonicalSheetLine(line: SheetLine, font: Int, label: (String) -> String, sounding: (String) -> String, click: (String) -> Unit) {
    if (line.annotation != null) { Text(line.annotation, fontSize = (font - 3).sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)); return }
    if (line.segments.all { it.text.isEmpty() && it.anchor == null }) { Spacer(Modifier.height(16.dp)); return }
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        line.segments.forEach { segment ->
            val raw = segment.anchor?.rawChord
            Column(Modifier.widthIn(min = if (raw != null) 48.dp else 0.dp).heightIn(min = 48.dp).then(if (raw != null) Modifier.clickable { click(sounding(raw)) } else Modifier)) {
                if (raw != null) Text(label(raw), color = MaterialTheme.colorScheme.primary, fontSize = (font - 3).sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 28.dp).padding(end = 6.dp))
                else Spacer(Modifier.height(28.dp))
                Text(segment.text, fontSize = font.sp, lineHeight = (font + 8).sp)
            }
        }
    }
}
