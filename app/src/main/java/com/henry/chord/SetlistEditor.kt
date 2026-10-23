package com.henry.chord

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SetlistScreen(set: Setlist, songs: List<Song>, entries: List<SetlistEntry>, back: () -> Unit, open: (SetlistEntry) -> Unit, dao: ChordDao, deleted: () -> Unit) {
    val scope=rememberCoroutineScope(); val density=LocalDensity.current.density
    var adding by remember { mutableStateOf(false) }; var rename by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SetlistEntry?>(null) }
    var dragHint by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val latestEntries by rememberUpdatedState(entries)
    fun move(from: Int,to: Int) { scope.launch(Dispatchers.Main.immediate) { val list=latestEntries.toMutableList(); val item=list.removeAt(from); list.add(to.coerceIn(0,list.size),item); dao.reorder(list) } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.Outlined.ArrowBack,"Back") }; Spacer(Modifier.weight(1f))
                IconButton({ scope.launch(Dispatchers.Main.immediate) { dao.duplicateSetlist(set); message="Setlist duplicated. Find the copy in Setlists." } }) { Icon(Icons.Outlined.ContentCopy,"Duplicate setlist") }
                IconButton({ rename=true }) { Icon(Icons.Outlined.Edit,"Rename setlist") }; IconButton({ confirm=true }) { Icon(Icons.Outlined.DeleteOutline,"Delete setlist") }
            }
            PageTitle(set.name,"${entries.size} songs · Long press a handle to reorder")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button({ entries.firstOrNull()?.let(open) },enabled=entries.isNotEmpty()) { Text("Play setlist") }
                OutlinedButton({ adding=true }) { Text("Add songs") }
            }
            if (message.isNotEmpty()) Text(message,Modifier.padding(vertical=8.dp))
            if (dragHint.isNotEmpty()) Text(dragHint,Modifier.padding(vertical=8.dp))
            SectionTitle("Running order")
        }
        itemsIndexed(entries,key={ _,e -> e.entryId }) { index,e ->
            songs.find { it.id == e.songId }?.let { song ->
                SongRow(song.copy(transpose=e.transposeOverride ?: song.transpose),{ open(e) },{ scope.launch(Dispatchers.Main.immediate) { dao.favorite(song.id,!song.favorite) } },number="%02d".format(index+1))
                if (e.notes.isNotEmpty()) Text(e.notes,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    var delta by remember(e.entryId) { mutableFloatStateOf(0f) }
                    Box(Modifier.size(48.dp).semantics { contentDescription="Drag ${song.title} to reorder" }.pointerInput(e.entryId,index) {
                        detectDragGesturesAfterLongPress(onDragStart={ delta=0f },onDragCancel={ dragHint="" },onDragEnd={
                            val target=(index+(delta/(144f*density)).roundToInt()).coerceIn(0,latestEntries.lastIndex)
                            if (target != index) move(index,target); dragHint=""
                        }) { change,amount -> change.consume(); delta += amount.y; dragHint="Move ${song.title} to ${(index+(delta/(144f*density)).roundToInt()).coerceIn(0,latestEntries.lastIndex)+1}" }
                    },contentAlignment=Alignment.Center) { Icon(Icons.Outlined.DragHandle,null) }
                    TextButton({ editing=e },Modifier.weight(1f)) { Text(if (e.transposeOverride == null) "Key / notes" else "Offset ${e.transposeOverride} · Notes",fontSize=12.sp) }
                    IconButton({ move(index,index-1) },enabled=index>0) { Icon(Icons.Outlined.ArrowUpward,"Move ${song.title} up") }
                    IconButton({ move(index,index+1) },enabled=index<entries.lastIndex) { Icon(Icons.Outlined.ArrowDownward,"Move ${song.title} down") }
                    IconButton({ scope.launch(Dispatchers.Main.immediate) { dao.deleteEntry(e) } }) { Icon(Icons.Outlined.RemoveCircleOutline,"Remove ${song.title}") }
                }
                HorizontalDivider()
            }
        }
        if (entries.isEmpty()) item { EmptyState("An open stage","Add songs, including repeats for an encore.","Choose first song",{ adding=true }) }
    }
    if (rename) NameDialog("Rename setlist",set.name,{ rename=false }) { scope.launch(Dispatchers.Main.immediate) { dao.updateSetlist(set.copy(name=it)) }; rename=false }
    if (confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Delete this setlist?") },text={ Text("The songs stay in your library.") },confirmButton={ TextButton({ scope.launch(Dispatchers.Main.immediate) { dao.deleteSetlist(set); deleted() } }) { Text("Delete") } },dismissButton={ TextButton({ confirm=false }) { Text("Cancel") } })
    if (adding) AlertDialog(onDismissRequest={ adding=false },title={ Text("Add songs") },text={
        var query by remember { mutableStateOf("") }
        Column { OutlinedTextField(query,{ query=it },label={ Text("Find title or artist") }); LazyColumn { items(songs.filter { (it.title+" "+it.artist).contains(query,true) }.take(100),key={ it.id }) { song ->
            TextButton({ scope.launch(Dispatchers.Main.immediate) { dao.addEntry(SetlistEntry(set.id,song.id,(latestEntries.maxOfOrNull { it.position } ?: -1)+1)); message="Added ${song.title}" } },Modifier.fillMaxWidth()) { Text(song.title,Modifier.weight(1f)); Icon(Icons.Outlined.Add,"Add ${song.title}") }
        } } }
    },confirmButton={ TextButton({ adding=false }) { Text("Done") } })
    editing?.let { entry ->
        var offset by remember(entry.entryId) { mutableStateOf(entry.transposeOverride?.toString().orEmpty()) }
        var notes by remember(entry.entryId) { mutableStateOf(entry.notes) }
        val valid=offset.isBlank() || offset.toIntOrNull() in -24..24
        AlertDialog(onDismissRequest={ editing=null },title={ Text("Setlist key & notes") },text={ Column {
            Text("Blank uses the song's preferred transpose. An override affects only this occurrence.",fontSize=13.sp)
            OutlinedTextField(offset,{ offset=it },label={ Text("Semitone override (−24 to +24)") },isError=!valid)
            OutlinedTextField(notes,{ notes=it },label={ Text("Entry notes") })
        } },confirmButton={ TextButton({ scope.launch(Dispatchers.Main.immediate) { dao.updateEntry(entry.copy(transposeOverride=offset.toIntOrNull(),notes=notes)) }; editing=null },enabled=valid) { Text("Save") } },dismissButton={ TextButton({ editing=null }) { Text("Cancel") } })
    }
}
