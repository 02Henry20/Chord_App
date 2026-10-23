package com.henry.chord

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanonicalSheetLine(line: SheetLine, font: Int, label: (String) -> String, sounding: (String) -> String, click: (String) -> Unit,
    display: SheetDisplay = SheetDisplay.BOTH, spacing: Float = 1.35f, chordColor: Color = MaterialTheme.colorScheme.primary) {
    if (line.annotation != null) { Text(line.annotation,fontSize=(font-3).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=6.dp)); return }
    val anchors = remember(line) { SheetLayout.anchors(line) }
    if (display == SheetDisplay.LYRICS) { Text(line.lyric,fontSize=font.sp,lineHeight=(font*spacing).sp,modifier=Modifier.padding(bottom=8.dp)); return }
    if (display == SheetDisplay.CHORDS || line.lyric.isBlank() && anchors.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            anchors.forEach { a -> TextButton({ click(sounding(a.anchor.rawChord)) },modifier=Modifier.semantics { contentDescription="Chord ${label(a.anchor.rawChord)}. Sounding ${sounding(a.anchor.rawChord)}" }) { Text(label(a.anchor.rawChord),fontSize=(font-2).sp,color=chordColor) } }
        }
        if (anchors.isEmpty()) Spacer(Modifier.height(12.dp))
        return
    }
    val measurer = rememberTextMeasurer(cacheSize=64)
    val lyricStyle = TextStyle(fontSize=font.sp,lineHeight=(font*spacing).sp)
    val chordStyle = TextStyle(fontSize=(font-3).sp,fontWeight=FontWeight.Bold)
    val lyricColor = MaterialTheme.colorScheme.onSurface
    SubcomposeLayout(Modifier.fillMaxWidth().padding(bottom=8.dp)) { constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        fun measure(text: String) = measurer.measure(text,lyricStyle,softWrap=false).size.width.toFloat()
        fun chordWidth(a: ChordAnchor) = measurer.measure(label(a.rawChord),chordStyle,softWrap=false).size.width.toFloat()+4.dp.toPx()
        val rows = SheetLayout.wrap(line.lyric,anchors,width.toFloat(),::measure,::chordWidth)
        val placements = mutableListOf<Triple<androidx.compose.ui.layout.Placeable,Int,Int>>()
        var y = 0
        rows.forEachIndexed { ri,row ->
            val text = line.lyric.substring(row.start,row.end)
            val textLayout = measurer.measure(text,lyricStyle,softWrap=false)
            val laneEnds = mutableListOf<Int>(); val chords = mutableListOf<Triple<androidx.compose.ui.layout.Placeable,Int,Int>>()
            var laneHeight = 48.dp.roundToPx()
            row.anchors.forEach { a ->
                val relative = (a.offset-row.start).coerceIn(0,text.length)
                val x = if (text.isEmpty()) 0 else textLayout.getHorizontalPosition(relative,true).roundToInt().coerceIn(0,width-1)
                val p = subcompose("$ri-${a.anchor.id}") {
                    Text(label(a.anchor.rawChord),style=chordStyle,color=chordColor,modifier=Modifier.heightIn(min=48.dp).clickable { click(sounding(a.anchor.rawChord)) }.semantics { contentDescription="Chord ${label(a.anchor.rawChord)}. Sounding ${sounding(a.anchor.rawChord)}" })
                }.single().measure(Constraints(maxWidth=width-x))
                val lane = laneEnds.indexOfFirst { it <= x }.let { if (it < 0) { laneEnds.add(0); laneEnds.lastIndex } else it }
                laneEnds[lane] = x + p.width + 6.dp.roundToPx()
                laneHeight = maxOf(laneHeight,p.height)
                chords.add(Triple(p,x,lane))
            }
            val lanes = laneEnds.size
            chords.forEach { (p,x,lane) -> placements.add(Triple(p,x,y+(lanes-1-lane)*laneHeight)) }
            y += lanes*laneHeight
            val lyric = subcompose("lyric-$ri") { Text(text,style=lyricStyle,color=lyricColor,softWrap=false) }.single().measure(Constraints(maxWidth=width))
            placements.add(Triple(lyric,0,y)); y += maxOf(lyric.height,ceil(font.sp.toPx()*spacing).toInt())
        }
        layout(width,y.coerceAtLeast(16.dp.roundToPx())) { placements.forEach { (p,x,top) -> p.place(x,top) } }
    }
}
