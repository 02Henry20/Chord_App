package com.henry.chord

import java.text.BreakIterator
import java.util.Locale

data class PositionedAnchor(val offset: Int, val anchor: ChordAnchor)
data class SheetRow(val start: Int, val end: Int, val anchors: List<PositionedAnchor>)

/** One width per composed line: scrolling remeasures without changing its wrapping inputs. */
internal class SheetRowCache {
    private var width = -1
    private var rows: List<SheetRow>? = null
    fun atWidth(width: Int, calculate: () -> List<SheetRow>): List<SheetRow> {
        if (this.width == width) rows?.let { return it }
        return calculate().also { this.width = width; rows = it }
    }
}

/** UTF-16 offsets only at grapheme boundaries. The source text is never padded or trimmed. */
object SheetLayout {
    fun anchors(line: SheetLine): List<PositionedAnchor> {
        var offset = 0
        return line.segments.mapNotNull { s -> s.anchor?.let { PositionedAnchor(offset, it) }.also { offset += s.text.length } }
    }
    fun boundaries(text: String): List<Int> {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        return buildList {
            var n = iterator.first()
            while (n != BreakIterator.DONE) {
                var safe=true
                if (n>0 && n<text.length) {
                    val left=text.codePointBefore(n); val right=text.codePointAt(n)
                    val type=Character.getType(right)
                    safe=left!=0x200D && right!=0x200D && right !in 0x1F3FB..0x1F3FF && right !in 0xE0020..0xE007F && right !in 0xFE00..0xFE0F && right !in 0xE0100..0xE01EF && type!=Character.NON_SPACING_MARK.toInt() && type!=Character.COMBINING_SPACING_MARK.toInt()
                    if (left in 0x1F1E6..0x1F1FF && right in 0x1F1E6..0x1F1FF) {
                        var i=n; var count=0
                        while (i>0 && text.codePointBefore(i) in 0x1F1E6..0x1F1FF) { i-=Character.charCount(text.codePointBefore(i)); count++ }
                        if (count%2==1) safe=false
                    }
                }
                if (safe) add(n)
                n=iterator.next()
            }
        }
    }
    fun wrap(text: String, anchors: List<PositionedAnchor>, width: Float, measure: (String) -> Float, chordWidth: (ChordAnchor) -> Float): List<SheetRow> {
        if (text.isEmpty()) return listOf(SheetRow(0,0,anchors))
        val boundaries = boundaries(text)
        val rows = mutableListOf<SheetRow>(); var start = 0
        while (start < text.length) {
            val newline = text.indexOfAny(charArrayOf('\r','\n'),start).let { if (it < 0) text.length else it }
            val afterBreak=if (newline<text.length && text[newline]=='\r' && text.getOrNull(newline+1)=='\n') newline+2 else newline+1
            if (newline == start) { rows.add(SheetRow(start,start,anchors.filter { it.offset == start })); start=afterBreak; continue }
            val candidates = boundaries.filter { it > start && it <= newline }
            var low = 0; var high = candidates.lastIndex; var chosen = candidates.first()
            while (low <= high) { val mid = (low + high) / 2; val end = candidates[mid]
                if (measure(text.substring(start,end)) <= width) { chosen = end; low = mid + 1 } else high = mid - 1
            }
            if (chosen < newline) {
                val wordEnd = candidates.lastOrNull { it <= chosen && text[it-1].isWhitespace() }
                if (wordEnd != null && wordEnd > start) chosen = wordEnd
            }
            // Start a new visual row at an anchor whose label cannot fit near the right edge.
            val overflow = anchors.firstOrNull { it.offset > start && it.offset < chosen &&
                measure(text.substring(start,it.offset)) + chordWidth(it.anchor).coerceAtMost(width) > width }
            if (overflow != null) chosen = boundaries.lastOrNull { it <= overflow.offset && it > start } ?: chosen
            val rowAnchors=anchors.filter { it.offset >= start && (it.offset < chosen || (chosen == text.length || chosen == newline) && it.offset == chosen) }
            val trailing=rowAnchors.filter { it.offset == chosen && measure(text.substring(start,chosen)) + chordWidth(it.anchor)>width }
            rows.add(SheetRow(start, chosen,rowAnchors-trailing.toSet()))
            if (trailing.isNotEmpty()) rows.add(SheetRow(chosen,chosen,trailing))
            start = if (chosen == newline && newline < text.length) afterBreak else chosen
        }
        if (text.endsWith('\n') || text.endsWith('\r')) rows.add(SheetRow(text.length,text.length,anchors.filter { it.offset == text.length }))
        return rows
    }
}

/** Editing a contiguous replacement relocates anchors by stable IDs; deleted anchors attach at the edit start. */
object AnchorEditor {
    fun splitLines(line: SheetLine): List<SheetLine> {
        if (line.annotation != null) return listOf(line)
        val text=line.lyric; val anchors=SheetLayout.anchors(line); var start=0
        val ranges=mutableListOf<Pair<Int,Int>>()
        Regex("\\r\\n|[\\r\\n]").findAll(text).forEach { match -> ranges.add(start to match.range.first); start=match.range.last+1 }
        ranges.add(start to text.length)
        return ranges.mapIndexed { index,(from,to) ->
            val base=if (index==0) line else SheetLine(Canonical.uuid(),emptyList())
            withAnchors(base,text.substring(from,to),anchors.filter { it.offset in from..to }.map { it.copy(offset=it.offset-from) })
        }
    }
    fun edit(line: SheetLine, replacement: String): SheetLine {
        val before = line.lyric
        val oldBounds = SheetLayout.boundaries(before); val newBounds = SheetLayout.boundaries(replacement)
        var prefix = 0
        while (prefix < minOf(before.length,replacement.length) && before[prefix] == replacement[prefix]) prefix++
        prefix = oldBounds.last { it <= prefix }.coerceAtMost(newBounds.last { it <= prefix })
        var suffix = 0
        while (suffix < minOf(before.length-prefix,replacement.length-prefix) && before[before.lastIndex-suffix] == replacement[replacement.lastIndex-suffix]) suffix++
        while (suffix > 0 && (before.length-suffix !in oldBounds || replacement.length-suffix !in newBounds)) suffix--
        val oldEnd = before.length-suffix; val newEnd = replacement.length-suffix
        val relocated = SheetLayout.anchors(line).map { a ->
            val offset = when { a.offset < prefix -> a.offset; a.offset >= oldEnd -> a.offset + newEnd-oldEnd; else -> prefix }
            a.copy(offset = newBounds.lastOrNull { it <= offset.coerceIn(0,replacement.length) } ?: 0)
        }
        return withAnchors(line,replacement,relocated)
    }
    fun withAnchors(line: SheetLine, text: String, anchors: List<PositionedAnchor>): SheetLine {
        require(anchors.all { it.offset in SheetLayout.boundaries(text) }) { "Choose a complete character boundary." }
        val sorted = anchors.sortedBy { it.offset }
        val segments = mutableListOf<TextSegment>()
        if (sorted.isEmpty()) segments.add(TextSegment(line.segments.firstOrNull()?.id ?: Canonical.uuid(),text))
        else {
            if (sorted.first().offset > 0) segments.add(TextSegment(line.segments.firstOrNull { it.anchor == null }?.id ?: Canonical.uuid(), text.substring(0,sorted.first().offset)))
            sorted.forEachIndexed { i,a ->
                val end = sorted.getOrNull(i+1)?.offset ?: text.length
                val oldId = line.segments.firstOrNull { it.anchor?.id == a.anchor.id }?.id
                segments.add(TextSegment(oldId ?: Canonical.uuid(),text.substring(a.offset,end),a.anchor))
            }
        }
        return line.copy(segments = segments)
    }
}
