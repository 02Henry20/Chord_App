package com.henry.chord

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

@Composable
fun SetlistsScreen(sets: List<Setlist>, entries: List<SetlistEntry>, open: (Long) -> Unit, create: (String) -> Unit) {
    var dialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { PageTitle("Set the mood.", "A place for every session.") { FilledTonalIconButton({ dialog = true }) { Icon(Icons.Outlined.Add, "New setlist") } }; Eyebrow("Your setlists · ${sets.size}"); Spacer(Modifier.height(18.dp)) }
        items(sets, key = { it.id }) { set ->
            OutlinedCard(onClick = { open(set.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                Column(Modifier.padding(22.dp)) { Icon(Icons.Outlined.QueueMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.height(24.dp)); Text(set.name, fontFamily = Serif, fontSize = 25.sp); Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("${entries.count { it.setlistId == set.id }} songs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary) } }
            }
        }
        if (sets.isEmpty()) item { EmptyState("Bring your songs together", "Order a queue for practice or the stage.", "Create a setlist", { dialog = true }) }
    }
    if (dialog) NameDialog("New setlist", "", { dialog = false }) { name -> dialog = false; create(name) }
}
@Composable
fun NameDialog(title: String, initial: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title, fontFamily = Serif) }, text = { OutlinedTextField(name, { name = it }, label = { Text("Setlist name") }, singleLine = true) }, confirmButton = { TextButton({ save(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
@Composable
fun SettingsScreen(prefs: Preferences, clearHistory: () -> Unit, database: ChordDatabase, save: (Preferences) -> Unit) {
    var confirmHistory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Make it yours.", "Small adjustments. Better sessions.")
        Eyebrow("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { theme -> FilterChip(prefs.theme == theme, { save(prefs.copy(theme = theme)) }, { Text(theme.title) }, modifier = Modifier.testTag("theme-${theme.name}")) } }
        Text("System follows your device appearance. Violet, teal and amber accents stay readable in both themes.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Your instrument")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("Guitar", "Piano").forEach { instrument -> FilterChip(prefs.instrument == instrument, { save(prefs.copy(instrument = instrument)) }, { Text(instrument) }, leadingIcon = { Icon(if (instrument == "Guitar") Icons.Outlined.MusicNote else Icons.Outlined.Piano, null, Modifier.size(18.dp)) }) } }
        SectionTitle("Song reader")
        Text("Text size · ${prefs.fontSize} sp", fontSize = 14.sp)
        Slider(prefs.fontSize.toFloat(), { save(prefs.copy(fontSize = it.toInt())) }, valueRange = 14f..30f, steps = 15)
        Text("Line spacing · ${"%.2f".format(prefs.lineSpacing)}",fontSize=14.sp)
        Slider(prefs.lineSpacing,{ save(prefs.copy(lineSpacing=it)) },valueRange=1f..2f)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Theme","Blue","Amber").forEach { color -> FilterChip(prefs.chordColor == color,{ save(prefs.copy(chordColor=color)) },{ Text(color) }) } }
        Text("Auto-scroll · ${prefs.scrollSpeed.toInt()} dp / second",fontSize=14.sp)
        Slider(prefs.scrollSpeed,{ save(prefs.copy(scrollSpeed=it)) },valueRange=8f..100f)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(0,3,5).forEach { seconds -> FilterChip(prefs.leadIn == seconds,{ save(prefs.copy(leadIn=seconds)) },{ Text(if (seconds == 0) "No lead-in" else "$seconds sec") }) } }
        SheetDisplay.entries.forEach { display -> TextButton({ save(prefs.copy(sheetDisplay=display)) }) { Text((if (prefs.sheetDisplay == display) "✓ " else "") + display.title) } }
        SettingSwitch("Keep screen awake", "While a song is open", prefs.keepAwake) { save(prefs.copy(keepAwake = it)) }
        SectionTitle("Chord labels")
        Text("Roman numeral / scale-degree harmony", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabelMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { save(prefs.copy(chordLabels = mode)) }.testTag("label-${mode.name}"), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(prefs.chordLabels == mode, null)
                Text(mode.title, Modifier.weight(1f), fontSize = 14.sp)
                Text(when (mode) { LabelMode.SYMBOLS -> "C  G  Am"; LabelMode.ROMAN -> "I  V  vi"; LabelMode.BOTH -> "C (I)" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("Root degrees and chord qualities, not inferred harmonic functions. Minor uses the natural-minor scale; raised notes carry #. Slash numbers show the bass scale degree: C/E in C major is I/3. b = flat, # = sharp, ° = diminished, ø7 = half-diminished. An unknown key or mode keeps chord symbols visible.", fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Note spelling")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Spelling.entries.forEach { value -> FilterChip(prefs.spelling == value, { save(prefs.copy(spelling = value)) }, { Text(value.title) }) } }
        Text("Auto uses common keys. Chords retain their scale-degree spelling, so some keys need double accidentals.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("On this device")
        TextButton({ confirmHistory = true }) { Text("Clear recent history") }
        Text("Favorites, songs and setlists are kept when history is cleared.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BackupControls(database)
        SectionTitle("A simpler songbook")
        Text("Chordroom · Native offline songbook\n\nYour library, favorites, notes, setlists and reader adjustments are saved locally. Six original demo songs are included.", fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
    if (confirmHistory) AlertDialog(onDismissRequest = { confirmHistory = false }, title = { Text("Clear recent history?") }, text = { Text("This removes viewing history on this device. Songs and favorites stay in your library.") }, confirmButton = { TextButton({ clearHistory(); confirmHistory = false }) { Text("Clear history") } }, dismissButton = { TextButton({ confirmHistory = false }) { Text("Cancel") } })
}
@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontSize = 15.sp); Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }; Switch(checked, change, modifier = Modifier.testTag(title)) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EditorScreen(song: Song?, dao: ChordDao, back: () -> Unit, deleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val draftSaver=remember(context) { EditorDrafts.saver(context) }
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title ?: "") }; var artist by rememberSaveable(song?.id) { mutableStateOf(song?.artist ?: "") }; var key by rememberSaveable(song?.id) { mutableStateOf(song?.key ?: "C") }; var body by rememberSaveable(song?.id,stateSaver=draftSaver) { mutableStateOf(song?.body ?: "") }; var notes by rememberSaveable(song?.id,stateSaver=draftSaver) { mutableStateOf(song?.notes ?: "") }; var source by rememberSaveable(song?.id) { mutableStateOf(song?.source ?: "") }
    val doc = remember(song?.id) { song?.documentJson?.let { runCatching { Canonical.decode(it).single().document }.getOrNull() } }
    val initialSheet=remember(song?.id) { (doc ?: ChordDao.legacyDocument(song ?: Song(title="Untitled",body=""))).copy(title="Draft",artist="Draft") }
    var sheetJson by rememberSaveable(song?.id,stateSaver=draftSaver) { mutableStateOf(Canonical.encode(initialSheet)) }
    val sheet=remember(sheetJson) { Canonical.decode(sheetJson).single().document }
    var undo by remember(song?.id) { mutableStateOf<List<String>>(emptyList()) }
    fun changeSheet(arrangement: ArrangementDoc) {
        var history=(undo+sheetJson).takeLast(30)
        while (history.size>1 && history.sumOf { it.length.toLong() }>1_000_000) history=history.drop(1)
        undo=history; sheetJson=Canonical.encode(sheet.copy(arrangements=listOf(arrangement))); body=arrangement.editorText()
    }
    var mode by rememberSaveable(song?.id) { mutableStateOf(initialSheet.arrangements.single().tonalMode ?: "unknown") }
    var bpm by rememberSaveable(song?.id) { mutableStateOf(initialSheet.arrangements.single().tempoBpm?.toString().orEmpty()) }
    var signature by rememberSaveable(song?.id) { mutableStateOf(initialSheet.arrangements.single().timeSignature.orEmpty()) }
    var rawEditor by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    var album by rememberSaveable(song?.id) { mutableStateOf(doc?.album.orEmpty()) }
    var year by rememberSaveable(song?.id) { mutableStateOf(doc?.year?.toString().orEmpty()) }
    var tags by rememberSaveable(song?.id) { mutableStateOf(doc?.tags?.joinToString(", ").orEmpty()) }
    var spotify by rememberSaveable(song?.id) { mutableStateOf(doc?.links?.spotify.orEmpty()) }
    var youtube by rememberSaveable(song?.id) { mutableStateOf(doc?.links?.youtube.orEmpty()) }
    var details by rememberSaveable { mutableStateOf(false) }
    var pendingArtwork by rememberSaveable { mutableStateOf<String?>(null) }
    var artMessage by remember { mutableStateOf("") }
    var pickingArtwork by remember { mutableStateOf(false) }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) {
        pickingArtwork = true
        scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ArtworkStore(context).stage(uri) } }.onSuccess { pendingArtwork?.let { java.io.File(it).delete() }; pendingArtwork = it.path; artMessage = "New artwork ready. Save to apply." }.onFailure { artMessage = it.message ?: "Image could not be read" }
            pickingArtwork = false
        }
    } }
    var error by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    val dirty=title != (song?.title ?: "") || artist != (song?.artist ?: "") || key != (song?.key ?: "C") || notes != (song?.notes ?: "") || source != (song?.source ?: "") || sheetJson != Canonical.encode(initialSheet) || album != doc?.album.orEmpty() || year != doc?.year?.toString().orEmpty() || tags != doc?.tags?.joinToString(", ").orEmpty() || spotify != doc?.links?.spotify.orEmpty() || youtube != doc?.links?.youtube.orEmpty() || pendingArtwork != null || bpm != initialSheet.arrangements.single().tempoBpm?.toString().orEmpty() || signature != initialSheet.arrangements.single().timeSignature.orEmpty() || mode != (initialSheet.arrangements.single().tonalMode ?: "unknown")
    fun leave() { if (dirty) discard=true else back() }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton({ leave() }) { Icon(Icons.Outlined.Close, "Cancel editing") }; Text(if (song == null) "New song" else "Edit song", Modifier.weight(1f), fontFamily = Serif, fontSize = 24.sp); Button({
            if (key.isNotBlank() && !Canonical.keyValid(key.trim())) error = "Enter a key such as C, F# or Am, or leave it unknown."
            else if (source.isNotBlank() && !source.startsWith("https://") && !source.startsWith("http://")) error = "Source links must begin with https:// or http://."
            else if (year.isNotBlank() && year.toIntOrNull() !in 1000..9999) error = "Enter a four-digit year, or leave it blank."
            else if (spotify.isNotBlank() && SongLinks.valid(spotify, LinkService.SPOTIFY) == null) error = "Enter a Spotify track URL, not a playlist or search link."
            else if (youtube.isNotBlank() && SongLinks.valid(youtube, LinkService.YOUTUBE) == null) error = "Enter a YouTube video URL (watch, shorts or youtu.be)."
            else if (bpm.isNotBlank() && (bpm.toDoubleOrNull()?.let { it.isFinite() && it in 10.0..500.0 } != true)) error="Enter a tempo from 10 to 500 BPM, or leave it blank."
            else if (signature.isNotBlank() && !signature.matches(Regex("(?:[1-9]|[1-2][0-9]|3[0-2])/(1|2|4|8|16|32)"))) error="Use a time signature such as 4/4 or 6/8."
            else if (key.trim().endsWith("m") && mode == "major") error="A key ending in m needs minor mode."
            else { saving = true; scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { runCatching { LibraryFiles.mutex.withLock {
                val updated = (song ?: Song(title = "", body = "",songUuid=sheet.songId,arrangementUuid=sheet.arrangements.single().id)).copy(title = title.trim(), artist = artist.trim().ifBlank { "Unknown artist" }, key = key.trim(), body = body, notes = notes, source = source.trim())
                val art = pendingArtwork?.let { path -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ArtworkRef(ArtworkStore(context).commit(java.io.File(path), song?.songUuid ?: Canonical.uuid()).path) } }
                val arrangement=sheet.arrangements.single().copy(originalKey=key.trim().ifBlank { null },tonalMode=mode.takeIf { it != "unknown" && key.isNotBlank() },tempoBpm=bpm.toDoubleOrNull(),timeSignature=signature.trim().ifBlank { null },provenance=source.trim().ifBlank { null })
                dao.saveEditor(updated, album.trim().ifBlank { null }, year.toIntOrNull(), tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(), MusicLinks(spotify.trim().ifBlank { null }, youtube.trim().ifBlank { null }), art,arrangement)
            } }.onSuccess { pendingArtwork?.let { java.io.File(it).delete() }; back() }.onFailure { error = it.message ?: "Save failed"; saving = false } } }
        }, enabled = title.isNotBlank() && sheet.arrangements.single().sections.isNotEmpty() && !saving && !pickingArtwork) { Text("Save") } }
        Text("A song starts with a few words.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Song title") }, singleLine = true)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(artist, { artist = it }, Modifier.weight(1f), label = { Text("Artist") }, singleLine = true); OutlinedTextField(key, { key = it }, Modifier.width(90.dp), label = { Text("Key") }, singleLine = true) }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("major","minor","unknown").forEach { value -> FilterChip(mode == value,{ mode=value },{ Text(value.replaceFirstChar { it.uppercase() }) }) } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(bpm,{ bpm=it },Modifier.weight(1f),label={ Text("Tempo (BPM)") },singleLine=true)
            OutlinedTextField(signature,{ signature=it },Modifier.weight(1f),label={ Text("Time signature") },singleLine=true)
        }
        TextButton({ if (undo.isNotEmpty()) { sheetJson=undo.last(); undo=undo.dropLast(1); body=Canonical.decode(sheetJson).single().document.arrangements.single().editorText() } },enabled=undo.isNotEmpty()) { Text("Undo sheet edit") }
        TextButton({
            title=song?.title.orEmpty(); artist=song?.artist.orEmpty(); key=song?.key ?: "C"; notes=song?.notes.orEmpty(); source=song?.source.orEmpty()
            sheetJson=Canonical.encode(initialSheet); body=song?.body.orEmpty(); undo=emptyList()
            album=doc?.album.orEmpty(); year=doc?.year?.toString().orEmpty(); tags=doc?.tags?.joinToString(", ").orEmpty(); spotify=doc?.links?.spotify.orEmpty(); youtube=doc?.links?.youtube.orEmpty()
            mode=initialSheet.arrangements.single().tonalMode ?: "unknown"; bpm=initialSheet.arrangements.single().tempoBpm?.toString().orEmpty(); signature=initialSheet.arrangements.single().timeSignature.orEmpty()
            pendingArtwork?.let { java.io.File(it).delete() }; pendingArtwork=null; error=""; artMessage=""
        },enabled=dirty) { Text("Revert all unsaved changes") }
        StructuredSheetEditor(sheet.arrangements.single(),::changeSheet)
        TextButton({ rawEditor=!rawEditor }) { Text(if (rawEditor) "Hide ChordPro editor" else "Advanced: ChordPro editor") }
        if (rawEditor) OutlinedTextField(body, { value -> body=value; runCatching { SheetParser.parse(value,"song.cho",sheet).document }.onSuccess { changeSheet(it.arrangements.single()) } }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 160.dp), label = { Text("Chords & lyrics") }, supportingText = { Text("Use [C] for chords and {comment:Chorus} for sections.") })
        val unsupported = remember(body) { Music.chords(body).filter { ChordParser.parse(it) == null } }
        if (unsupported.isNotEmpty()) Text("Unrecognized chords kept as written: ${unsupported.joinToString()}. Check their spelling; diagrams and degrees are unavailable.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Your notes") }, minLines = 3)
        OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Source link (optional)") }, singleLine = true, supportingText = { Text("Opens externally from the reader.") })
        TextButton({ details = !details }) { Text(if (details) "Hide song details" else "Album, tags, artwork & links") }
        if (details) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pendingArtwork != null) LocalArtwork(pendingArtwork, song?.songUuid ?: "new", "New artwork preview", Modifier.size(56.dp)) else song?.let { SongArtwork(it, Modifier.size(56.dp)) }
                TextButton({ artworkPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !pickingArtwork) { Text("Change artwork") }
            }
            if (artMessage.isNotEmpty()) Text(artMessage, fontSize = 12.sp)
            OutlinedTextField(album, { album = it }, Modifier.fillMaxWidth(), label = { Text("Album (optional)") }, singleLine = true)
            OutlinedTextField(year, { year = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Year (optional)") }, singleLine = true)
            OutlinedTextField(tags, { tags = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Tags, comma separated") })
            OutlinedTextField(spotify, { spotify = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Spotify track URL") }, supportingText = { Text("Your chosen track link. No account required.") })
            OutlinedTextField(youtube, { youtube = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("YouTube video URL") }, supportingText = { Text("Use an official video/audio link you have checked.") })
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
        if (song != null) TextButton({ deleting = true }, Modifier.padding(top = 20.dp)) { Icon(Icons.Outlined.DeleteOutline, null); Text("Delete song") }
        Spacer(Modifier.height(32.dp))
    }
    if (discard) AlertDialog(onDismissRequest={ discard=false },title={ Text("Discard unsaved changes?") },text={ Text("Saved songs stay unchanged. Your current edits will be lost.") },confirmButton={ TextButton({ pendingArtwork?.let { java.io.File(it).delete() }; back() }) { Text("Discard changes") } },dismissButton={ TextButton({ discard=false }) { Text("Keep editing") } })
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${song.title}?") }, text = { Text("This removes the song and its notes from your library and all setlists.") }, confirmButton = { TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.delete(song); deleted() } }) { Text("Delete") } }, dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
}
