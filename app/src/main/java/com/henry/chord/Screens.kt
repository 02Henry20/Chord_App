package com.henry.chord

import androidx.activity.compose.rememberLauncherForActivityResult
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
fun SetlistScreen(set: Setlist, songs: List<Song>, entries: List<SetlistEntry>, back: () -> Unit, open: (Song) -> Unit, dao: ChordDao, deleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }; var rename by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    val ordered = entries.mapNotNull { e -> songs.find { it.id == e.songId } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Outlined.ArrowBack, "Back") }; Spacer(Modifier.weight(1f)); IconButton({ rename = true }) { Icon(Icons.Outlined.Edit, "Rename setlist") }; IconButton({ confirm = true }) { Icon(Icons.Outlined.DeleteOutline, "Delete setlist") } }; PageTitle(set.name, "${ordered.size} songs · Play them your way")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button({ ordered.firstOrNull()?.let(open) }, enabled = ordered.isNotEmpty()) { Icon(Icons.Outlined.PlayArrow, null); Text("Play setlist") }; OutlinedButton({ adding = true }) { Icon(Icons.Outlined.Add, null); Text("Add songs") } }; Spacer(Modifier.height(20.dp)); Eyebrow("Running order") }
        items(entries, key = { it.songId }) { e -> val song = songs.find { it.id == e.songId }; val index = entries.indexOf(e)
            if (song != null) {
                SongRow(song, { open(song) }, number = "%02d".format(index + 1))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { val list = entries.toMutableList(); java.util.Collections.swap(list, index, index - 1); dao.reorder(list) } }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Move ${song.title} up", Modifier.size(18.dp)) }
                    IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { val list = entries.toMutableList(); java.util.Collections.swap(list, index, index + 1); dao.reorder(list) } }, enabled = index < entries.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "Move ${song.title} down", Modifier.size(18.dp)) }
                    IconButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.removeEntry(set.id, song.id) } }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove ${song.title}", Modifier.size(18.dp)) }
                }
            }
        }
        if (ordered.isEmpty()) item { EmptyState("An open stage", "Add a few songs to get this session started.", "Choose first song", { adding = true }) }
    }
    if (rename) NameDialog("Rename setlist", set.name, { rename = false }) { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.updateSetlist(set.copy(name = it)) }; rename = false }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete this setlist?") }, text = { Text("The songs stay in your library.") }, confirmButton = { TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.deleteSetlist(set); deleted() } }) { Text("Delete") } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } })
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("Add songs", fontFamily = Serif) }, text = {
        LazyColumn { items(songs, key = { it.id }) { song -> val exists = entries.any { it.songId == song.id }
            Row(Modifier.fillMaxWidth().clickable(enabled = !exists) { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.addEntry(SetlistEntry(set.id, song.id, (entries.maxOfOrNull { it.position } ?: -1) + 1)) } }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(song.title, Modifier.weight(1f)); Icon(if (exists) Icons.Outlined.Check else Icons.Outlined.Add, if (exists) "Added" else "Add ${song.title}") }
        } }
    }, confirmButton = { TextButton({ adding = false }) { Text("Done") } })
}

@Composable
fun ImportScreen(dao: ChordDao, library: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val manager = remember { ImportManager(context, dao) }
    var status by rememberSaveable { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { busy = true; status = runCatching { manager.files(uris) }.getOrElse { "Import failed: ${it.message}" }; busy = false } }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { busy = true; status = runCatching { manager.folder(uri) }.getOrElse { "Import failed: ${it.message}" }; busy = false } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Bring your songs.", "Your collection belongs with you.")
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(24.dp)) { Icon(Icons.Outlined.LibraryAdd, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)); Spacer(Modifier.height(24.dp)); Text("From files to first chords.", fontFamily = Serif, fontSize = 28.sp); Text("Import ChordPro or plain text chord sheets. Everything stays on your device.", fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp)) } }
        Spacer(Modifier.height(24.dp))
        Button({ documents.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !busy) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Choose files or ZIP") }
        OutlinedButton({ folder.launch(null) }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp), enabled = !busy) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose a folder") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 20.dp))
        if (status.isNotBlank()) { Text(status, Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.primary); TextButton(library) { Text("View library") } }
        SectionTitle("A few useful notes")
        Text(".cho · .chopro · .chordpro · .pro · .txt\n\nZIP archives and nested folders are supported. Import up to 100 songs / 16 MB at a time, with a 2 MB limit per song. Exact duplicates are skipped.\n\nPlace chords in brackets, like [Am], before a lyric. Use {comment:Chorus} for section headings. Only import material you have the right to use.", fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun SettingsScreen(prefs: Preferences, save: (Preferences) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        PageTitle("Make it yours.", "Small adjustments. Better sessions.")
        Eyebrow("Appearance")
        SettingSwitch("Dark appearance", "A softer screen for late sessions", prefs.dark) { save(prefs.copy(dark = it)) }
        SectionTitle("Your instrument")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf("Guitar", "Piano").forEach { instrument -> FilterChip(prefs.instrument == instrument, { save(prefs.copy(instrument = instrument)) }, { Text(instrument) }, leadingIcon = { Icon(if (instrument == "Guitar") Icons.Outlined.MusicNote else Icons.Outlined.Piano, null, Modifier.size(18.dp)) }) } }
        SectionTitle("Song reader")
        Text("Text size · ${prefs.fontSize} sp", fontSize = 14.sp)
        Slider(prefs.fontSize.toFloat(), { save(prefs.copy(fontSize = it.toInt())) }, valueRange = 14f..30f, steps = 15)
        SettingSwitch("Keep screen awake", "While a song is open", prefs.keepAwake) { save(prefs.copy(keepAwake = it)) }
        SettingSwitch("Roman numerals", "Degrees relative to the song's tonic", prefs.roman) { save(prefs.copy(roman = it)) }
        SectionTitle("A simpler songbook")
        Text("Chord 1.0\nAn offline space for songs and sessions.\n\nSix original demo songs are included. Your library, favorites, notes, setlists and reader adjustments are saved locally. Export individual songs from the reader as ChordPro files.", fontSize = 13.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
}
@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { change(!checked) }, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontSize = 15.sp); Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }; Switch(checked, change, modifier = Modifier.testTag(title)) }; HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EditorScreen(song: Song?, dao: ChordDao, back: () -> Unit, deleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(song?.id) { mutableStateOf(song?.title ?: "") }; var artist by rememberSaveable(song?.id) { mutableStateOf(song?.artist ?: "") }; var key by rememberSaveable(song?.id) { mutableStateOf(song?.key ?: "C") }; var body by rememberSaveable(song?.id) { mutableStateOf(song?.body ?: "") }; var notes by rememberSaveable(song?.id) { mutableStateOf(song?.notes ?: "") }; var source by rememberSaveable(song?.id) { mutableStateOf(song?.source ?: "") }
    var error by remember { mutableStateOf("") }; var deleting by remember { mutableStateOf(false) }; var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Outlined.Close, "Cancel editing") }; Text(if (song == null) "New song" else "Edit song", Modifier.weight(1f), fontFamily = Serif, fontSize = 24.sp); Button({
            if (!Regex("[A-G][#b]?m?").matches(key.trim())) error = "Enter a key such as C, F# or Am."
            else if (source.isNotBlank() && !source.startsWith("https://") && !source.startsWith("http://")) error = "Source links must begin with https:// or http://."
            else { saving = true; scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { runCatching { val updated = (song ?: Song(title = "", body = "")).copy(title = title.trim(), artist = artist.trim().ifBlank { "Original" }, key = key.trim(), body = body.trim(), notes = notes, source = source.trim()); if (song == null) dao.insert(updated) else dao.update(updated) }.onSuccess { back() }.onFailure { error = it.message ?: "Save failed"; saving = false } } }
        }, enabled = title.isNotBlank() && body.isNotBlank() && !saving) { Text("Save") } }
        Text("A song starts with a few words.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp))
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Song title") }, singleLine = true)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(artist, { artist = it }, Modifier.weight(1f), label = { Text("Artist") }, singleLine = true); OutlinedTextField(key, { key = it }, Modifier.width(90.dp), label = { Text("Key") }, singleLine = true) }
        OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 260.dp), label = { Text("Chords & lyrics") }, placeholder = { Text("{comment:Verse}\n[C]Write your first [G]line…") }, supportingText = { Text("Use [C] for chords and {comment:Chorus} for sections.") })
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Your notes") }, minLines = 3)
        OutlinedTextField(source, { source = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Source link (optional)") }, singleLine = true, supportingText = { Text("Opens externally from the reader.") })
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp))
        if (song != null) TextButton({ deleting = true }, Modifier.padding(top = 20.dp)) { Icon(Icons.Outlined.DeleteOutline, null); Text("Delete song") }
        Spacer(Modifier.height(32.dp))
    }
    if (deleting && song != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete ${song.title}?") }, text = { Text("This removes the song and its notes from your library and all setlists.") }, confirmButton = { TextButton({ scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.delete(song); deleted() } }) { Text("Delete") } }, dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } })
}
