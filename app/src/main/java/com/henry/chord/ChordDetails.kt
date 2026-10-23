package com.henry.chord

import android.graphics.Paint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ChordDialog(chord: String, key: KeyContext?, instrument: String, capo: Int, dismiss: () -> Unit) {
    val parsed = remember(chord) { ChordParser.parse(chord) }
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * .85f).toDp() }
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(.94f).heightIn(max = maxHeight), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp)) {
                Eyebrow("Sounding chord")
                Text(chord, fontFamily = Serif, fontSize = 34.sp, modifier = Modifier.testTag("actual-chord"))
                Text(key?.let { "${it.description} · ${Theory.roman(chord, it) ?: "Unrecognized chord"}" } ?: "Key required · symbols retained", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (parsed == null) Text("Unrecognized chord. Its original text is preserved; check the spelling in Edit song.", modifier = Modifier.padding(vertical = 16.dp))
                    else if (parsed.noChord) Text("No chord · let the harmony rest.", modifier = Modifier.padding(vertical = 16.dp))
                    else {
                        Text("Chord tones · ${parsed.notes.joinToString("  ") { it.name }}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                        if (parsed.bass != null) Text("Slash bass · ${parsed.bass.name} is the lowest note", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        if (instrument == "Piano") PianoDetails(parsed) else GuitarDetails(parsed, capo)
                    }
                }
                TextButton(dismiss, Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun PianoDetails(chord: ParsedChord) {
    val voicing = remember(chord) { PianoVoicings.rootPosition(chord) } ?: return
    Text(voicing.notes.joinToString("  ") { it.label }, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp).testTag("voiced-notes"))
    PianoKeyboard(chord, voicing)
}

@Composable
fun PianoKeyboard(chord: ParsedChord, voicing: PianoVoicing) {
    val first = (voicing.notes.minOf { it.midi } / 12) * 12
    val octaves = maxOf(2, (voicing.notes.maxOf { it.midi } - first) / 12 + 1)
    val whites = (first until first + octaves * 12).filter { it % 12 in Note.naturals }
    val selected = voicing.notes.map { it.midi }.toSet()
    val voice = MaterialTheme.colorScheme.tertiaryContainer
    val selectedInk = MaterialTheme.colorScheme.onTertiaryContainer
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    val description = "Chromatic piano. Voiced notes: ${voicing.notes.joinToString { it.label }}"
    Canvas(Modifier.fillMaxWidth().height(166.dp).semantics { contentDescription = description }.testTag("piano-keyboard")) {
        val width = size.width / whites.size
        fun color(midi: Int, black: Boolean) = when { midi in selected -> voice; black -> Color(0xFF25312B); else -> Color.White }
        whites.forEachIndexed { i, midi ->
            drawRect(color(midi, false), Offset(i * width, 0f), Size(width - 1.5f, size.height - 1))
            drawRect(Color.Gray, Offset(i * width, 0f), Size(width - 1.5f, size.height - 1), style = Stroke(1f))
            if (midi in selected || midi % 12 == 0) {
                label.color = (if (midi in selected) selectedInk else Color(0xFF18251F)).toArgb(); label.textSize = 8.sp.toPx()
                val note = voicing.notes.find { it.midi == midi }?.label ?: "C${midi / 12 - 1}"
                drawContext.canvas.nativeCanvas.drawText(note, (i + .5f) * width, size.height - 10.dp.toPx(), label)
            }
        }
        whites.forEachIndexed { i, midi ->
            if (midi % 12 in listOf(0, 2, 5, 7, 9)) {
                val black = midi + 1
                drawRect(color(black, true), Offset((i + 1) * width - width * .3f, 0f), Size(width * .6f, size.height * .61f))
                if (black in selected) {
                    label.color = selectedInk.toArgb(); label.textSize = 7.sp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(voicing.notes.first { it.midi == black }.label, (i + 1) * width, size.height * .5f, label)
                }
            }
        }
    }
}

@Composable
private fun GuitarDetails(chord: ParsedChord, capo: Int) {
    // Capo changes shape only: both displayed and sounding pitches stay at the selected concert key.
    val shapeText = Theory.transpose(chord.raw, -capo)
    val shapeChord = remember(shapeText) { ChordParser.parse(shapeText)!! }
    val voicings = remember(shapeChord) { GuitarVoicings.forChord(shapeChord) }
    if (capo > 0) Text("Capo $capo · $shapeText",fontSize=12.sp)
    if (voicings.isEmpty()) { Text("Guitar diagram unavailable for this chord.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp)); return }
    val voicing = voicings.first()
    GuitarDiagram(voicing)
}

@Composable
fun GuitarDiagram(voicing: GuitarVoicing) {
    val accent = MaterialTheme.colorScheme.primary; val line = MaterialTheme.colorScheme.onSurfaceVariant
    val rows = maxOf(4, (voicing.frets.maxOrNull() ?: 0) - voicing.start + 1)
    Canvas(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 22.dp).semantics { contentDescription = "Six string guitar: ${voicing.frets.joinToString()}. Starting fret ${voicing.start}" }.testTag("guitar-fretboard")) {
        val top = 24.dp.toPx(); val dx = size.width / 5; val dy = (size.height - top) / rows
        repeat(6) { i -> drawLine(line, Offset(i * dx, top), Offset(i * dx, size.height), 2f) }
        repeat(rows + 1) { i -> drawLine(line, Offset(0f, top + i * dy), Offset(size.width, top + i * dy), if (i == 0 && voicing.start == 1) 7f else 2f) }
        voicing.frets.forEachIndexed { i, fret ->
            val x = i * dx; val y = 10.dp.toPx(); val r = 3.dp.toPx()
            if (fret > 0) drawCircle(accent, 8.dp.toPx(), Offset(x, top + (fret - voicing.start + .5f) * dy))
            else if (fret == 0) drawCircle(line, r, Offset(x, y), style = Stroke(2f))
            else { drawLine(line, Offset(x-r,y-r), Offset(x+r,y+r), 2f); drawLine(line, Offset(x-r,y+r), Offset(x+r,y-r), 2f) }
        }
    }
    Text("Starting fret ${voicing.start}", fontSize = 11.sp)
}
