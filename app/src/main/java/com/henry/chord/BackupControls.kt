package com.henry.chord

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*

@Composable
fun BackupControls(database: ChordDatabase) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val manager=remember(database) { BackupManager(context,database) }
    var message by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var job by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var replace by remember { mutableStateOf(false) }; var acknowledged by remember { mutableStateOf(false) }; var includeSettings by remember { mutableStateOf(true) }
    fun work(action: suspend () -> Unit) { job=scope.launch(Dispatchers.Main.immediate) { busy=true; try { action() } catch (e: CancellationException) { message="Operation cancelled. Review the current library before retrying." } catch (e: Exception) { message="Backup failed: ${e.message}" } finally { busy=false } } }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) work { message="Writing complete backup…"; manager.export(uri); message="Backup saved: songs, versions, artwork, favorites, setlists, notes and preferences." } }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) work {
        message="Validating backup files and artwork…"; preview?.root?.deleteRecursively(); preview=manager.stage(uri); replace=false; acknowledged=false; message="Backup ready for review."
    } }
    SectionTitle("Backup & restore")
    Text("Versioned ZIP archives work offline. Merge preserves existing songs, personal edits and setlist orders when IDs match.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton({ export.launch("chordroom-backup.zip") },Modifier.fillMaxWidth(),enabled=!busy) { Text("Export full backup ZIP") }
    OutlinedButton({ restore.launch(arrayOf("application/zip","application/octet-stream")) },Modifier.fillMaxWidth(),enabled=!busy) { Text("Restore full backup") }
    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton({ job?.cancel() }) { Text("Cancel backup operation") } }
    if (message.isNotEmpty()) Text(message,Modifier.padding(vertical=8.dp),fontSize=13.sp)
    preview?.let { p -> AlertDialog(onDismissRequest={ if (!busy) { preview=null; p.root.deleteRecursively() } },title={ Text("Review backup") },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("${p.songs} songs · ${p.versions} arrangements\n${p.covers} covers · ${p.sets} setlists",fontSize=16.sp)
        Row(Modifier.fillMaxWidth().clickable(enabled=!busy) { replace=false },verticalAlignment=Alignment.CenterVertically) { RadioButton(!replace,{ replace=false },enabled=!busy); Text("Merge into this library") }
        Row(Modifier.fillMaxWidth().clickable(enabled=!busy) { replace=true },verticalAlignment=Alignment.CenterVertically) { RadioButton(replace,{ replace=true },enabled=!busy); Text("Replace this library") }
        if (replace) { Text("Replace deletes all current songs, favorites, notes and setlists, then restores this backup. Export a backup first if you need the current library.",color=MaterialTheme.colorScheme.error)
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(acknowledged,{ acknowledged=it },enabled=!busy); Text("I understand that current library data will be replaced.",fontSize=13.sp) }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(includeSettings,{ includeSettings=it },enabled=!busy); Text("Restore device preferences",fontSize=13.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    } },confirmButton={ TextButton({ work {
        message="Restoring backup…"; val result=manager.restore(p,replace,includeSettings)
        message="Restored ${result.imported} arrangements, skipped ${result.skipped} existing, added ${result.setlists} setlists." + if (result.settingsPending) " Device preferences are pending; reopen the app to retry." else ""
        preview=null; p.root.deleteRecursively()
    } },enabled=!busy && (!replace || acknowledged)) { Text("Restore backup") } },dismissButton={ TextButton({ preview=null; p.root.deleteRecursively() },enabled=!busy) { Text("Cancel") } }) }
}
