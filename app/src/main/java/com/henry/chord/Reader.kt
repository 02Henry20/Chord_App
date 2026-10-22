package com.henry.chord

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
    val scroll = rememberScrollState()
    var fullscreen by rememberSaveable { mutableStateOf(false) }; var scrolling by rememberSaveable { mutableStateOf(false) }; var speed by rememberSaveable { mutableFloatStateOf(22f) }; var font by rememberSaveable { mutableIntStateOf(prefs.fontSize) }; var roman by rememberSaveable { mutableStateOf(prefs.roman) }; var selectedChord by remember { mutableStateOf<String?>(null) }; var addToSet by remember { mutableStateOf(false) }; var controls by rememberSaveable { mutableStateOf(false) }; var message by remember { mutableStateOf("") }
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
            if (scroll.maxValue > 0 && scroll.value >= scroll.maxValue) { scrolling = false; break }
            scroll.dispatchRawDelta(speed * .032f)
        }
    }
    val flats = song.key.contains('b') || song.key in listOf("F", "Dm")
    val key = Music.transpose(song.key, song.transpose, flats)
    val chords = Music.chords(song.body).map { Music.transpose(it, song.transpose, flats) }
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
                Text(song.title, fontFamily = Serif, fontSize = 32.sp, modifier = Modifier.padding(top = 8.dp))
                Text(song.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, bottom = 18.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(transpose = song.transpose - 1)) } }) { Icon(Icons.Outlined.Remove, "Transpose down") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text("KEY $key", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("${if (song.transpose > 0) "+" else ""}${song.transpose} semitones", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(transpose = song.transpose + 1)) } }) { Icon(Icons.Outlined.Add, "Transpose up") }
                        VerticalDivider(Modifier.height(30.dp))
                        TextButton({ controls = !controls }) { Text("Capo ${song.capo}", fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp)) }
                    }
                }
                if (controls) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Capo", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo - 1).coerceAtLeast(0))) } }, enabled = song.capo > 0) { Icon(Icons.Outlined.Remove, "Lower capo") }; Text("${song.capo}", fontSize = 12.sp); IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = (song.capo + 1).coerceAtMost(12))) } }, enabled = song.capo < 12) { Icon(Icons.Outlined.Add, "Raise capo") }
                        Spacer(Modifier.weight(1f)); TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.update(song.copy(capo = 0, transpose = 0)) } }) { Text("Reset") }
                    }
                    Text("Sounding key: ${Music.transpose(song.key, song.transpose + song.capo, flats)} · chords show the shapes you play", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chords.forEach { chord -> AssistChip({ selectedChord = chord }, { Text(if (roman) Music.roman(chord, key) else chord, fontWeight = FontWeight.SemiBold) }) }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = if (fullscreen) 28.dp else 10.dp)) {
            if (fullscreen) { Text(song.title, fontFamily = Serif, fontSize = 25.sp); Spacer(Modifier.height(24.dp)) }
            song.body.lines().forEach { line ->
                val heading = Regex("\\{(?:comment|c|start_of_chorus|soc|start_of_verse|sov)(?::\\s*([^}]*))?\\}", RegexOption.IGNORE_CASE).matchEntire(line.trim())
                when {
                    line.isBlank() -> Spacer(Modifier.height(16.dp))
                    heading != null -> { Spacer(Modifier.height(14.dp)); Text(heading.groupValues[1].ifBlank { if (line.contains("chorus") || line.contains("soc")) "Chorus" else "Verse" }.uppercase(), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp)) }
                    line.trim().startsWith("{") -> Unit
                    else -> ChordLine(line, song.transpose, flats, roman, key, font, { selectedChord = it })
                }
            }
            if (song.notes.isNotBlank()) { Spacer(Modifier.height(24.dp)); Eyebrow("Your notes"); Text(song.notes, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp)) }
            if (song.source.startsWith("https://") || song.source.startsWith("http://")) TextButton({ runCatching { uriHandler.openUri(song.source) }.onFailure { message = "No browser is available." } }) { Text("Open source link"); Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)) }
            Spacer(Modifier.height(50.dp)); Eyebrow("End of song")
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
                    IconButton({ roman = !roman }) { Text(if (roman) "I–V" else "C–G", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
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
    if (selectedChord != null) ChordDialog(selectedChord!!, prefs.instrument, { selectedChord = null })
    if (addToSet) AlertDialog(onDismissRequest = { addToSet = false }, title = { Text("Add to setlist", fontFamily = Serif) }, text = { Column { if (sets.isEmpty()) Text("Create a setlist from the Setlists tab first."); sets.forEach { set -> val exists = entries.any { it.setlistId == set.id && it.songId == song.id }; TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.addEntry(SetlistEntry(set.id, song.id, (entries.filter { it.setlistId == set.id }.maxOfOrNull { it.position } ?: -1) + 1)) }; addToSet = false; message = "Added to ${set.name}" }, enabled = !exists) { Text(set.name + if (exists) " · Added" else "") } } } }, confirmButton = { TextButton({ addToSet = false }) { Text("Done") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordLine(line: String, transpose: Int, flats: Boolean, roman: Boolean, key: String, font: Int, click: (String) -> Unit) {
    val tokens = Regex("(?:\\[([^]]+)])?([^\\[]*)").findAll(line).filter { it.value.isNotEmpty() }.toList()
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        tokens.forEach { token ->
            val chord = token.groupValues[1].takeIf { it.isNotEmpty() }?.let { Music.transpose(it, transpose, flats) }
            val lyric = token.groupValues[2]
            Column(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).then(if (chord != null) Modifier.clickable { click(chord) } else Modifier)) {
                if (chord != null) Text(if (roman) Music.roman(chord, key) else chord, color = MaterialTheme.colorScheme.primary, fontSize = (font - 3).sp, fontWeight = FontWeight.Bold, modifier = Modifier.heightIn(min = 28.dp).padding(end = 6.dp))
                else Spacer(Modifier.height(28.dp))
                Text(lyric.ifEmpty { " " }, fontSize = font.sp, lineHeight = (font + 8).sp)
            }
        }
    }
}

@Composable
fun ChordDialog(chord: String, initial: String, dismiss: () -> Unit) {
    var instrument by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(chord, fontFamily = Serif, fontSize = 34.sp) }, text = { Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Guitar", "Piano").forEach { FilterChip(instrument == it, { instrument = it }, { Text(it) }) } }
        Spacer(Modifier.height(18.dp))
        if (instrument == "Piano") PianoDiagram(chord) else GuitarDiagram(chord)
        Text(if (instrument == "Piano") "Highlighted keys show chord tones across one octave." else "Standard tuning · E A D G B e\n○ open string · × muted string", fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 18.dp))
    } }, confirmButton = { TextButton(dismiss) { Text("Done") } })
}
@Composable
fun PianoDiagram(chord: String) {
    val tones = Music.piano(chord); val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width / 7
        listOf(0,2,4,5,7,9,11).forEachIndexed { i, pitch -> drawRect(if (pitch in tones) accent else Color.White, Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w - 2, size.height)); drawRect(Color.Gray, Offset(i * w, size.height - 2), androidx.compose.ui.geometry.Size(w - 2, 2f)) }
        listOf(0 to 1,1 to 3,3 to 6,4 to 8,5 to 10).forEach { (i, pitch) -> drawRect(if (pitch in tones) Color(0xFFB77A4B) else Color(0xFF252E29), Offset((i + 1) * w - w * .28f, 0f), androidx.compose.ui.geometry.Size(w * .56f, size.height * .61f)) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { listOf("C", "D", "E", "F", "G", "A", "B").forEach { Text(it, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
}
@Composable
fun GuitarDiagram(chord: String) {
    val shape = Music.guitar[chord]
    if (shape == null) { Text("No guitar voicing stored for $chord. Switch to Piano to see its chord tones.", lineHeight = 22.sp); return }
    val start = (shape.filter { it > 0 }.minOrNull() ?: 1).let { if ((shape.maxOrNull() ?: 0) <= 4) 1 else it }
    val accent = MaterialTheme.colorScheme.primary; val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(174.dp).padding(horizontal = 20.dp)) {
        val top = 24.dp.toPx(); val dx = size.width / 5; val dy = (size.height - top) / 4
        repeat(6) { i -> drawLine(line, Offset(i * dx, top), Offset(i * dx, size.height), 2f) }
        repeat(5) { i -> drawLine(line, Offset(0f, top + i * dy), Offset(size.width, top + i * dy), if (i == 0 && start == 1) 7f else 2f) }
        shape.forEachIndexed { i, fret ->
            val x = i * dx; val y = 10.dp.toPx(); val r = 3.dp.toPx()
            if (fret > 0) drawCircle(accent, 9.dp.toPx(), Offset(x, top + (fret - start + .5f) * dy))
            else if (fret == 0) drawCircle(line, r, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            else { drawLine(line, Offset(x-r,y-r), Offset(x+r,y+r), 2f); drawLine(line, Offset(x-r,y+r), Offset(x+r,y-r), 2f) }
        }
    }
    Text("Fret $start", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
}
