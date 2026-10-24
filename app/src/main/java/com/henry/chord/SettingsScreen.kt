package com.henry.chord

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(prefs: Preferences, clearHistory: () -> Unit, database: ChordDatabase, save: ((Preferences)->Preferences) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmHistory by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)) {
        PageTitle("Make it yours.", "")
        @Composable fun Category(name: String, summary: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
            SettingsCategory(name,summary,icon,expanded==name,{ expanded=if (expanded==name) null else name },content)
        }
        Category("Appearance",prefs.theme.title,Icons.Outlined.Palette) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { theme ->
                FilterChip(prefs.theme==theme,{ save { it.copy(theme=theme) } },{ Text(theme.title) },modifier=Modifier.testTag("theme-${theme.name}"))
            } }
            SettingsExample(prefs)
        }
        Category("Song reader","${prefs.fontSize} sp · margins & spacing",Icons.Outlined.FormatSize) {
            Text("Text size · ${prefs.fontSize} sp")
            Slider(prefs.fontSize.toFloat(),{ value -> save { it.copy(fontSize=value.toInt()) } },valueRange=14f..30f,steps=15,modifier=Modifier.testTag("font-size"))
            Text("Line spacing · ${"%.2f".format(prefs.lineSpacing)}")
            Slider(prefs.lineSpacing,{ value -> save { it.copy(lineSpacing=value) } },valueRange=1f..2f,modifier=Modifier.testTag("line-spacing"))
            MarginSlider("Side margins",prefs.sheetMargin,4f..40f,"sheet-margin") { value -> save { it.copy(sheetMargin=value) } }
            MarginSlider("Between lyric lines",prefs.songLineGap,0f..24f,"song-line-gap") { value -> save { it.copy(songLineGap=value) } }
            MarginSlider("Chord to lyric gap",prefs.chordLineGap,0f..12f,"chord-line-gap") { value -> save { it.copy(chordLineGap=value) } }
            MarginSlider("Between sections",prefs.sectionGap,0f..32f,"section-gap") { value -> save { it.copy(sectionGap=value) } }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Theme","Blue","Amber").forEach { color -> FilterChip(prefs.chordColor==color,{ save { it.copy(chordColor=color) } },{ Text(color) }) } }
            SettingsExample(prefs)
        }
        Category("Chords & instrument","${prefs.instrument} · ${prefs.chordLabels.title}",Icons.Outlined.Piano) {
            Text("Instrument",fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { listOf("Piano","Guitar").forEach { instrument ->
                FilterChip(prefs.instrument==instrument,{ save { it.copy(instrument=instrument) } },{ Text(instrument) },modifier=Modifier.testTag("instrument-$instrument"))
            } }
            val chord=remember { ChordParser.parse("C")!! }
            if (prefs.instrument=="Piano") PianoKeyboard(chord,PianoVoicings.rootPosition(chord)!!)
            else GuitarVoicings.forChord(chord).firstOrNull()?.let { GuitarDiagram(it) }
            Text("Chord labels",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            Text("Roman numeral / scale-degree harmony",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LabelMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { save { it.copy(chordLabels=mode) } }.testTag("label-${mode.name}"),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(prefs.chordLabels==mode,null); Text(mode.title,Modifier.weight(1f))
                }
            }
            SettingsExample(prefs)
            Text("Degrees describe roots and qualities in the song key. C/E in C major is I/3; the number after / is the bass degree. Unknown key or mode keeps chord names visible.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Note spelling",fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Spelling.entries.forEach { value -> FilterChip(prefs.spelling==value,{ save { it.copy(spelling=value) } },{ Text(value.title) },modifier=Modifier.testTag("spelling-${value.name}")) } }
            Text("Example transposed up one semitone",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            // Symbols stay visible here so the spelling difference is clear even with Roman defaults.
            SettingsExample(prefs.copy(chordLabels=LabelMode.SYMBOLS),transpose=1,tag="settings-spelling-example")
        }
        Category("Performance","${prefs.scrollSpeed.toInt()} dp/s · screen & scrolling",Icons.Outlined.Speed) {
            val speed=rememberScrollSpeed(prefs.scrollSpeed) { value -> save { it.copy(scrollSpeed=value) } }
            Text("Speed · ${speed.requested.toInt()} dp / second")
            Slider(speed.requested,{ speed.requested=it },valueRange=8f..100f,modifier=Modifier.testTag("default-scroll-speed"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(0,3,5).forEach { seconds -> FilterChip(prefs.leadIn==seconds,{ save { it.copy(leadIn=seconds) } },{ Text(if (seconds==0) "No lead-in" else "$seconds sec") }) } }
            ScrollExample(prefs.copy(scrollSpeed=speed.applied))
            SettingSwitch("Keep screen awake","While a song is open",prefs.keepAwake) { value -> save { it.copy(keepAwake=value) } }
        }
        Category("Library & backup","History, export & restore",Icons.Outlined.FolderCopy) { BackupControls(database); TextButton({ confirmHistory=true }) { Text("Clear recent history") } }
        Spacer(Modifier.height(24.dp))
    }
    if (confirmHistory) AlertDialog(onDismissRequest={ confirmHistory=false },title={ Text("Clear recent history?") },text={ Text("Songs, favorites and notes stay in your library.") },confirmButton={ TextButton({ clearHistory(); confirmHistory=false }) { Text("Clear history") } },dismissButton={ TextButton({ confirmHistory=false }) { Text("Cancel") } })
}

@Composable
private fun SettingsCategory(name: String, summary: String, icon: ImageVector, expanded: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(bottom=10.dp),shape=RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp).clickable(role=Role.Button,onClick=toggle).padding(horizontal=16.dp,vertical=10.dp).testTag("category-$name").semantics { stateDescription=if (expanded) "Expanded" else "Collapsed" },verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.padding(end=14.dp).size(24.dp).testTag("category-icon-$name"),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(name,fontWeight=FontWeight.SemiBold); Text(summary,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(bottom=16.dp),content=content)
    }
}

private val exampleLines=listOf(
    SheetLine("preview-1",listOf(TextSegment("p1","Under the "),TextSegment("p2","evening lights, ",ChordAnchor("a1","C")),TextSegment("p3","we sing",ChordAnchor("a2","G")))),
    SheetLine("preview-2",listOf(TextSegment("p4","Let our voices ",ChordAnchor("a3","Am")),TextSegment("p5","ring",ChordAnchor("a4","F"))))
)

@Composable
private fun SettingsExample(prefs: Preferences, transpose: Int=0,tag: String="settings-live-example") {
    val context=remember { KeyContext.from("C","major")!! }
    Surface(Modifier.fillMaxWidth().padding(vertical=10.dp).testTag(tag),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal=prefs.sheetMargin.dp,vertical=12.dp)) {
            Text("Live example",fontSize=12.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(bottom=8.dp))
            exampleLines.forEach { line -> CanonicalSheetLine(line,prefs.fontSize,{ Theory.label(it,transpose,context,prefs.chordLabels,prefs.spelling) },{ Theory.transpose(it,transpose,context,prefs.spelling) },{},SheetDisplay.BOTH,prefs.lineSpacing,readerChordColor(prefs.chordColor),prefs.chordLineGap,prefs.songLineGap) }
            Spacer(Modifier.height(prefs.sectionGap.dp))
        }
    }
}

@Composable
private fun ScrollExample(prefs: Preferences) {
    val scroll=rememberScrollState(); var playing by remember { mutableStateOf(false) }; val density=LocalDensity.current.density
    val scope=rememberCoroutineScope()
    val currentSpeed by rememberUpdatedState(prefs.scrollSpeed)
    LaunchedEffect(playing,density) {
        var last=withFrameNanos { it }
        while (playing) {
            val now=withFrameNanos { it }; val delta=((now-last)/1_000_000_000.0).coerceIn(0.0,.1); last=now
            if (!scroll.canScrollForward) { playing=false; break }
            scroll.scrollBy((currentSpeed*density*delta).toFloat())
        }
    }
    OutlinedButton({ scope.launch { if (!scroll.canScrollForward) scroll.scrollTo(0); playing=!playing } }) { Text(if (playing) "Pause example" else "Play example") }
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().height(150.dp).verticalScroll(scroll).padding(12.dp)) { repeat(12) { Text("Evening lights · line ${it+1}",fontSize=prefs.fontSize.sp,lineHeight=(prefs.fontSize*prefs.lineSpacing).sp,modifier=Modifier.padding(vertical=8.dp)) } }
    }
}

@Composable
private fun MarginSlider(title: String,value: Float,range: ClosedFloatingPointRange<Float>,tag: String,save: (Float)->Unit) {
    Text("$title · ${value.toInt()} dp",fontSize=13.sp)
    Slider(value,save,valueRange=range,modifier=Modifier.testTag(tag))
}
