package com.henry.chord

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
    display: SheetDisplay = SheetDisplay.BOTH, spacing: Float = 1.35f, chordColor: Color = MaterialTheme.colorScheme.primary,
    chordGap: Float = 2f, lineGap: Float = 6f) {
    if (line.annotation != null) { Text(line.annotation,fontSize=(font-3).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=6.dp)); return }
    val anchors = remember(line) { SheetLayout.anchors(line) }
    val lyric = remember(line) { line.lyric }
    // Resolve in composition so state read by a stable callback still invalidates these labels.
    val labels = anchors.associate { it.anchor.rawChord to label(it.anchor.rawChord) }
    val soundingChords = anchors.associate { it.anchor.rawChord to sounding(it.anchor.rawChord) }
    if (display == SheetDisplay.LYRICS) { Text(lyric,fontSize=font.sp,lineHeight=(font*spacing).sp,modifier=Modifier.padding(bottom=8.dp)); return }
    if (display == SheetDisplay.CHORDS || lyric.isBlank() && anchors.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(top=chordGap.dp,bottom=lineGap.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            anchors.forEach { a ->
                val raw=a.anchor.rawChord; val text=labels.getValue(raw); val actual=soundingChords.getValue(raw)
                Text(text,fontSize=(font-2).sp,color=chordColor,fontWeight=FontWeight.Bold,modifier=Modifier.clickable { click(actual) }.semantics { contentDescription="Chord $text. Sounding $actual" })
            }
        }
        if (anchors.isEmpty()) Spacer(Modifier.height(12.dp))
        return
    }
    val measurer = rememberTextMeasurer(cacheSize=64)
    val lyricStyle = TextStyle(fontSize=font.sp,lineHeight=(font*spacing).sp)
    val chordStyle = TextStyle(fontSize=(font-3).sp,fontWeight=FontWeight.Bold)
    val lyricColor = MaterialTheme.colorScheme.onSurface
    // Default system fonts only. Recreate for all text/anchor/style/configuration changes;
    // the width changes independently during rotation or window resizing.
    val rowCache = remember(lyric,anchors,labels,lyricStyle,chordStyle,measurer,LocalDensity.current,LocalLayoutDirection.current) { SheetRowCache() }
    SubcomposeLayout(Modifier.fillMaxWidth().padding(bottom=lineGap.dp)) { constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        fun measure(text: String) = measurer.measure(text,lyricStyle,softWrap=false).size.width.toFloat()
        fun chordWidth(a: ChordAnchor) = measurer.measure(labels.getValue(a.rawChord),chordStyle,softWrap=false).size.width.toFloat()+4.dp.toPx()
        val rows = rowCache.atWidth(width) { SheetLayout.wrap(lyric,anchors,width.toFloat(),::measure,::chordWidth) }
        val placements = mutableListOf<Triple<androidx.compose.ui.layout.Placeable,Int,Int>>()
        var y = 0
        rows.forEachIndexed { ri,row ->
            val text = lyric.substring(row.start,row.end)
            val textLayout = measurer.measure(text,lyricStyle,softWrap=false)
            val laneEnds = mutableListOf<Int>(); val chords = mutableListOf<Triple<androidx.compose.ui.layout.Placeable,Int,Int>>()
            var laneHeight = 0
            row.anchors.forEach { a ->
                val chordText=labels.getValue(a.anchor.rawChord)
                val actual=soundingChords.getValue(a.anchor.rawChord)
                val relative = (a.offset-row.start).coerceIn(0,text.length)
                val x = if (text.isEmpty()) 0 else textLayout.getHorizontalPosition(relative,true).roundToInt().coerceIn(0,width-1)
                val p = subcompose("$ri-${a.anchor.id}") {
                    // Compose expands the native touch target; visual height stays compact above the lyric.
                    Text(chordText,style=chordStyle,color=chordColor,modifier=Modifier.clickable { click(actual) }.semantics { contentDescription="Chord $chordText. Sounding $actual" })
                }.single().measure(Constraints(maxWidth=width-x))
                val lane = laneEnds.indexOfFirst { it <= x }.let { if (it < 0) { laneEnds.add(0); laneEnds.lastIndex } else it }
                laneEnds[lane] = x + p.width + 6.dp.roundToPx()
                laneHeight = maxOf(laneHeight,p.height)
                chords.add(Triple(p,x,lane))
            }
            val lanes = laneEnds.size
            chords.forEach { (p,x,lane) -> placements.add(Triple(p,x,y+(lanes-1-lane)*laneHeight)) }
            y += lanes*laneHeight + if (lanes>0) chordGap.dp.roundToPx() else 0
            val lyric = subcompose("lyric-$ri") { Text(text,style=lyricStyle,color=lyricColor,softWrap=false) }.single().measure(Constraints(maxWidth=width))
            placements.add(Triple(lyric,0,y)); y += maxOf(lyric.height,ceil(font.sp.toPx()*spacing).toInt())
            if (ri<rows.lastIndex) y += lineGap.dp.roundToPx()
        }
        layout(width,y.coerceAtLeast(16.dp.roundToPx())) { placements.forEach { (p,x,top) -> p.place(x,top) } }
    }
}
