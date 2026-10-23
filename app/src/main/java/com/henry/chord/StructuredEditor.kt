package com.henry.chord

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*

@Composable
fun StructuredSheetEditor(arrangement: ArrangementDoc, change: (ArrangementDoc) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(arrangement.sections.firstOrNull()?.id) }
    var editing by remember { mutableStateOf<Pair<String,SheetLine>?>(null) }
    fun section(value: SheetSection) { change(arrangement.copy(sections=arrangement.sections.map { if (it.id == value.id) value else it })) }
    SectionTitle("Sections & chord anchors")
    Text("Edit a line, place the cursor on a syllable, then insert or move a chord. Lyrics keep their exact whitespace.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    arrangement.sections.forEachIndexed { index,s -> key(s.id) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            TextButton({ expanded=if (expanded == s.id) null else s.id },Modifier.weight(1f)) { Text(s.label.ifBlank { s.type }) }
            IconButton({ val list=arrangement.sections.toMutableList(); java.util.Collections.swap(list,index,index-1); change(arrangement.copy(sections=list)) },enabled=index>0) { Icon(Icons.Outlined.ArrowUpward,"Move section up") }
            IconButton({ val list=arrangement.sections.toMutableList(); java.util.Collections.swap(list,index,index+1); change(arrangement.copy(sections=list)) },enabled=index<arrangement.sections.lastIndex) { Icon(Icons.Outlined.ArrowDownward,"Move section down") }
            IconButton({ change(arrangement.copy(sections=arrangement.sections.filter { it.id != s.id })) }) { Icon(Icons.Outlined.DeleteOutline,"Delete section ${s.label}") }
        }
        if (expanded == s.id) {
            OutlinedTextField(s.label,{ section(s.copy(label=it)) },label={ Text("Section heading") },modifier=Modifier.fillMaxWidth())
            var types by remember { mutableStateOf(false) }
            Box { TextButton({ types=true }) { Text("Type: ${s.type}") }; DropdownMenu(types,{ types=false }) { Canonical.sectionTypes.forEach { type -> DropdownMenuItem(text={ Text(type) },onClick={ section(s.copy(type=type)); types=false }) } } }
            s.lines.forEachIndexed { li,line -> key(line.id) {
                OutlinedCard(onClick={ editing=s.id to line },modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Column(Modifier.padding(horizontal=12.dp,vertical=6.dp)) {
                        CanonicalSheetLine(line,17,{ it },{ it },{ editing=s.id to line })
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            TextButton({ editing=s.id to line },Modifier.weight(1f)) { Text("Edit line ${li+1}") }
                            IconButton({ val lines=s.lines.toMutableList(); java.util.Collections.swap(lines,li,li-1); section(s.copy(lines=lines)) },enabled=li>0) { Icon(Icons.Outlined.ArrowUpward,"Move line up") }
                            IconButton({ val lines=s.lines.toMutableList(); java.util.Collections.swap(lines,li,li+1); section(s.copy(lines=lines)) },enabled=li<s.lines.lastIndex) { Icon(Icons.Outlined.ArrowDownward,"Move line down") }
                            IconButton({ section(s.copy(lines=s.lines.filter { it.id != line.id })) }) { Icon(Icons.Outlined.DeleteOutline,"Delete line ${li+1}") }
                        }
                    }
                }
            } }
            TextButton({ val line=SheetLine(Canonical.uuid(),listOf(TextSegment(Canonical.uuid(),""))); section(s.copy(lines=s.lines+line)); editing=s.id to line }) { Text("Add lyric / chord-only line") }
            TextButton({ section(s.copy(lines=s.lines+SheetLine(Canonical.uuid(),listOf(TextSegment(Canonical.uuid(),""))))) }) { Text("Add blank line") }
        }
        HorizontalDivider()
    } }
    TextButton({ val s=SheetSection(Canonical.uuid(),"verse","Verse",emptyList()); change(arrangement.copy(sections=arrangement.sections+s)); expanded=s.id }) { Icon(Icons.Outlined.Add,null); Text("Add section") }
    editing?.let { (sectionId,line) -> LineEditorDialog(line,{ editing=null }) { edited ->
        val current=arrangement.sections.firstOrNull { it.id == sectionId }
        if (current != null) section(current.copy(lines=current.lines.flatMap { if (it.id == edited.id) AnchorEditor.splitLines(edited) else listOf(it) }))
        editing=null
    } }
}

@Composable
private fun LineEditorDialog(initial: SheetLine, dismiss: () -> Unit, save: (SheetLine) -> Unit) {
    var line by remember(initial.id) { mutableStateOf(initial) }
    var field by remember(initial.id) { mutableStateOf(TextFieldValue(initial.lyric)) }
    var chord by remember { mutableStateOf("C") }
    var selected by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf("") }
    fun cursor(): Int = SheetLayout.boundaries(field.text).lastOrNull { it <= field.selection.start } ?: 0
    AlertDialog(onDismissRequest=dismiss,title={ Text("Edit lyric & chord anchors") },text={ Column(Modifier.verticalScroll(rememberScrollState())) {
        if (initial.annotation != null) OutlinedTextField(line.annotation.orEmpty(),{ line=line.copy(annotation=it) },label={ Text("Comment") })
        else {
            OutlinedTextField(field,{ value -> line=AnchorEditor.edit(line,value.text); field=value },Modifier.fillMaxWidth(),label={ Text("Lyric text · tap to place cursor") },minLines=2)
            Text("Cursor: character ${SheetLayout.boundaries(field.text).indexOf(cursor())}. Chords apply before this character.",fontSize=12.sp)
            OutlinedTextField(chord,{ chord=it },label={ Text("Chord symbol") },singleLine=true)
            TextButton({ if (chord.isNotBlank() && chord.length <= 80 && !chord.contains('\n') && !chord.contains('[') && !chord.contains(']')) {
                val a=ChordAnchor(Canonical.uuid(),chord.trim()); line=AnchorEditor.withAnchors(line,field.text,SheetLayout.anchors(line)+PositionedAnchor(cursor(),a)); selected=a.id
                warning=if (ChordParser.parse(chord.trim()) == null) "Unsupported symbol saved exactly; degrees/diagrams unavailable." else ""
            } else warning="Enter one chord symbol, up to 80 characters." }) { Text("Insert chord at cursor") }
            SheetLayout.anchors(line).forEach { a ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected == a.anchor.id,{ selected=a.anchor.id; chord=a.anchor.rawChord })
                    Text("${a.anchor.rawChord} · character ${SheetLayout.boundaries(field.text).indexOf(a.offset)}",Modifier.weight(1f),fontSize=13.sp)
                    IconButton({ line=AnchorEditor.withAnchors(line,field.text,SheetLayout.anchors(line).filter { it.anchor.id != a.anchor.id }); if (selected == a.anchor.id) selected=null }) { Icon(Icons.Outlined.DeleteOutline,"Remove ${a.anchor.rawChord} anchor") }
                }
            }
            if (selected != null) {
                TextButton({ line=AnchorEditor.withAnchors(line,field.text,SheetLayout.anchors(line).map { if (it.anchor.id == selected) it.copy(offset=cursor()) else it }) }) { Text("Move selected chord to cursor") }
                TextButton({ if (chord.isNotBlank() && chord.length<=80 && !chord.contains('\n') && !chord.contains('\r') && !chord.contains('[') && !chord.contains(']')) line=AnchorEditor.withAnchors(line,field.text,SheetLayout.anchors(line).map { if (it.anchor.id == selected) it.copy(anchor=it.anchor.copy(rawChord=chord.trim())) else it }) }) { Text("Change selected chord") }
            }
            if (warning.isNotEmpty()) Text(warning,color=MaterialTheme.colorScheme.error,fontSize=12.sp)
            Text("Live preview",fontSize=12.sp)
            CanonicalSheetLine(line,18,{ it },{ it },{})
        }
    } },confirmButton={ TextButton({ save(line) }) { Text("Apply line") } },dismissButton={ TextButton(dismiss) { Text("Cancel") } })
}
