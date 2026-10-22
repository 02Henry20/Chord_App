package com.henry.chord

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LocalArtwork(path: String?, identity: String, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, path, identity) {
        val decoded = withContext(Dispatchers.IO) {
            val store = ArtworkStore(context)
            val file = if (path != null) File(path) else store.thumbnailPath(null, identity).also { store.placeholder(identity, it) }
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
        withContext(Dispatchers.Main.immediate) { value = decoded }
    }
    val current = image
    if (current != null) Image(current, description, modifier.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, description) }
}

@Composable
fun ImportScreen(dao: ChordDao, library: () -> Unit) {
    val context = LocalContext.current
    val controller: ImportController = viewModel(factory = remember(dao) { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ImportController(context.applicationContext, dao) as T
    } })
    val ui by controller.state.collectAsState(context = Dispatchers.Main.immediate)
    var selected by remember { mutableStateOf<ImportCandidate?>(null) }; var showErrors by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }; var exportMessage by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    val documents = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) controller.choose(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) controller.choose(tree = it) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) scope.launch(Dispatchers.Main.immediate) { exportMessage = "Preparing archive…"; exportMessage = try { CollectionExport(context, dao).write(uri); "Collection exported with all arrangements and local artwork." } catch (e: Exception) { "Export failed: ${e.message}" } } }
    val session = ui.session
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            PageTitle("Bring your songs.", "Chordroom · A library that stays with you.")
            if (session == null && !ui.busy) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(24.dp)) { Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(34.dp)); Spacer(Modifier.height(20.dp)); Text("A whole collection.\nOne local home.", fontFamily = Serif, fontSize = 29.sp); Spacer(Modifier.height(12.dp)); Text("Choose files, a recursive folder or a ZIP. Preview every song and resolve duplicates before anything is saved.", fontSize = 14.sp, lineHeight = 22.sp) } }
                Spacer(Modifier.height(20.dp))
                Button({ documents.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Choose files or ZIP") }
                OutlinedButton({ folder.launch(null) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose a folder") }
                Text("JSON · ChordPro · TXT · ZIP\nUp to 2,000 arrangements / 256 MB expanded per batch. Artwork is copied into private storage. No ongoing access to the source is needed.", Modifier.padding(vertical = 20.dp), fontSize = 13.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ export.launch("chordroom-collection.zip") }, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Archive, null); Spacer(Modifier.width(8.dp)); Text("Export collection") }
            }
            if (ui.busy) {
                val progress = ui.progress
                Text(progress?.phase ?: "Restoring import…", fontFamily = Serif, fontSize = 24.sp)
                if (progress != null && progress.total > 0) LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                Text(listOfNotNull(progress?.let { "${it.completed}${if (it.total > 0) " / ${it.total}" else ""}" }, progress?.path).joinToString(" · "), fontSize = 12.sp)
                TextButton(controller::cancel) { Text("Cancel / pause") }
            }
            if (ui.error != null) Text(ui.error!!, Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error)
            if (exportMessage.isNotEmpty()) Text(exportMessage, Modifier.padding(vertical = 12.dp))
            if (session != null && !ui.busy) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text(if (session.finished) "Import complete" else "Review your import", fontFamily = Serif, fontSize = 27.sp)
                    Text("${session.files} files · ${session.candidates.map { it.songUuid.ifEmpty { it.id } }.distinct().size} songs · ${session.candidates.size} arrangements · ${session.covers} covers", Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    Text("${session.candidates.count { it.warnings.isNotEmpty() }} with warnings · ${session.issues.size} errors", Modifier.padding(top = 4.dp), fontSize = 12.sp)
                    if (session.finished || session.successes + session.skips > 0) Text("${session.successes} imported · ${session.skips} skipped", Modifier.padding(top = 10.dp), fontWeight = FontWeight.SemiBold)
                } }
                if (session.issues.isNotEmpty()) TextButton({ showErrors = true }) { Text("Review ${session.issues.size} file errors") }
                if (!session.finished) {
                    val pending = session.candidates.count { !it.committed && it.choice != ImportChoice.SKIP && (!it.needsReview || it.reviewed) }
                    Button(controller::commit, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = pending > 0) { Text("Import selected ($pending)") }
                    if (pending == 0 && session.candidates.any { !it.committed }) TextButton(controller::commit) { Text("Finish without importing") }
                    Text("Duplicates default to Skip. TXT alignment proposals need explicit review. Replacing retains favorites, notes, key offsets and setlist references.", Modifier.padding(vertical = 12.dp), fontSize = 12.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Button(library, Modifier.fillMaxWidth()) { Text("View library") }
                TextButton(controller::reset) { Text(if (session.finished) "New import" else "Discard staged import") }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Warnings", "Duplicates").forEach { FilterChip(filter == it, { filter = it }, { Text(it) }) } }
            }
        }
        if (session != null && !ui.busy) items(session.candidates.filter { filter == "All" || filter == "Warnings" && it.warnings.isNotEmpty() || filter == "Duplicates" && it.duplicateReason != null }, key = { it.id }) { candidate ->
            ImportCandidateRow(candidate, { selected = candidate }, { controller.choice(candidate.id, it) }, session.finished)
        }
    }
    selected?.let { candidate -> ImportPreview(candidate, dao, { selected = null }) { text -> controller.review(candidate.id, text); selected = null } }
    if (showErrors && session != null) AlertDialog(onDismissRequest = { showErrors = false }, title = { Text("File errors", fontFamily = Serif) }, text = { LazyColumn { items(session.issues) { error -> Text(error.path, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(error.message, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp)) } } }, confirmButton = { TextButton({ showErrors = false }) { Text("Done") } })
}

@Composable
private fun ImportCandidateRow(candidate: ImportCandidate, preview: () -> Unit, choice: (ImportChoice) -> Unit, finished: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LocalArtwork(candidate.preview, candidate.id, "Cover of ${candidate.title}", Modifier.size(56.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(candidate.title, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text("${candidate.artist} · ${candidate.arrangement} · ${candidate.originalKey ?: "Key unknown"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(candidate.source, fontSize = 10.sp, maxLines = 1) }
            IconButton(preview) { Icon(Icons.Outlined.Visibility, "Preview ${candidate.title}") }
        }
        candidate.duplicateReason?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 8.dp)) }
        if (candidate.warnings.isNotEmpty()) Text("${candidate.warnings.size} warning(s)${if (candidate.needsReview && !candidate.reviewed) " · Review required" else ""}", fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
        if (!finished && !candidate.committed) Box {
            TextButton({ menu = true }) { Text(when (candidate.choice) { ImportChoice.IMPORT -> "Import"; ImportChoice.SKIP -> "Skip"; ImportChoice.REPLACE -> "Replace existing arrangement"; ImportChoice.NEW_VERSION -> "Import as new version" }); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
            DropdownMenu(menu, { menu = false }) {
                listOf(ImportChoice.SKIP to "Skip", ImportChoice.IMPORT to "Import", ImportChoice.REPLACE to "Replace existing arrangement", ImportChoice.NEW_VERSION to "Import as new version").filter { candidate.duplicateId != null || it.first != ImportChoice.REPLACE }.forEach { (option, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { choice(option); menu = false }, enabled = option == ImportChoice.SKIP || !candidate.needsReview || candidate.reviewed) }
            }
        } else Text(if (candidate.committed) "Processed" else "Staged", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ImportPreview(candidate: ImportCandidate, dao: ChordDao, dismiss: () -> Unit, accept: (String?) -> Unit) {
    val document by produceState<SongDocument?>(null, candidate.id) { val parsed = withContext(Dispatchers.IO) { Canonical.decode(File(candidate.draft).readText()).single().document }; withContext(Dispatchers.Main.immediate) { value = parsed } }
    val doc = document
    val existing by produceState<Song?>(null, candidate.duplicateId) { val row = candidate.duplicateId?.let { withContext(Dispatchers.IO) { dao.song(it) } }; withContext(Dispatchers.Main.immediate) { value = row } }
    var editor by remember(candidate.id) { mutableStateOf<String?>(null) }; var original by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(candidate.title, fontFamily = Serif) }, text = {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            LocalArtwork(candidate.preview, candidate.id, "Cover preview", Modifier.size(100.dp))
            if (doc != null) {
                val a = doc.arrangements.single()
                Text("${doc.artist}\n${a.name} · ${a.originalKey ?: "Key unknown"} · ${a.tonalMode ?: "Mode unknown"}\n${a.tempoBpm?.let { "$it BPM" } ?: "Tempo unknown"} · ${a.timeSignature ?: "Meter unknown"}", Modifier.padding(vertical = 14.dp), fontSize = 13.sp)
                if (doc.album != null) Text("Album: ${doc.album}", fontSize = 12.sp)
                doc.artwork?.credit?.let { Text("Artwork: $it", fontSize = 12.sp) }
                candidate.warnings.forEach { Text("• $it", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                existing?.let { match ->
                    Text("Existing arrangement", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text("${match.title}\n${match.artist} · ${match.arrangementName} · ${match.key.ifBlank { "Unknown key" }}", fontSize = 13.sp)
                    Text("Replacing keeps its personal state and setlist references. Compare this sheet before choosing Replace.", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(vertical = 8.dp))
                    val old = remember(match.documentJson) { Canonical.decode(match.documentJson).single().document.arrangements.single() }
                    old.sections.firstOrNull()?.lines?.take(2)?.forEach { CanonicalSheetLine(it, 13, { raw -> raw }, { raw -> raw }) {} }
                    Text("Incoming arrangement", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
                if (candidate.needsReview) {
                    Text("Check every bracketed chord against the original. Edit bracket locations to correct anchors. Accepting confirms this positioning.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    val raw = File(candidate.draft).resolveSibling("${candidate.id}.original.txt")
                    if (raw.exists()) TextButton({ original = !original }) { Text(if (original) "Show proposed positions" else "Show original text") }
                    if (original) SelectionContainerCompat(raw.readText())
                    else OutlinedTextField(editor ?: a.editorText(), { editor = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp), label = { Text("Review chords & lyrics") })
                } else {
                    a.sections.take(3).forEach { section -> Text(section.label, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)); section.lines.take(5).forEach { CanonicalSheetLine(it, 15, { raw -> raw }, { raw -> raw }) {} } }
                }
            }
        }
    }, confirmButton = { TextButton({ accept(if (candidate.needsReview) editor ?: doc?.arrangements?.single()?.editorText() else null) }, enabled = doc != null) { Text(if (candidate.needsReview) "Accept reviewed positions" else "Looks good") } }, dismissButton = { TextButton(dismiss) { Text("Close") } })
}
@Composable private fun SelectionContainerCompat(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) } }
