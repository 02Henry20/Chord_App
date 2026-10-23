package com.henry.chord

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

/** All occurrences of a section type use the same color family. */
fun sectionFamily(type: String) = when (type) {
    "verse" -> "teal"
    "chorus", "postchorus" -> "violet"
    "intro", "outro" -> "amber"
    "prechorus" -> "blue"
    "bridge" -> "rose"
    "solo", "instrumental" -> "blue"
    else -> "neutral"
}

@Composable
fun sectionBackground(type: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    return Color(when (sectionFamily(type)) {
        "teal" -> if (dark) 0xFF163B36 else 0xFFD9F2EB
        "violet" -> if (dark) 0xFF38294D else 0xFFEDE0FF
        "amber" -> if (dark) 0xFF45331E else 0xFFFFE8C9
        "blue" -> if (dark) 0xFF20364B else 0xFFDEEDFD
        "rose" -> if (dark) 0xFF472A3B else 0xFFF9DFEC
        else -> if (dark) 0xFF2D283C else 0xFFF0EAF8
    })
}

@Composable
fun SectionBadge(section: SheetSection) {
    Surface(Modifier.fillMaxWidth().padding(top=14.dp, bottom=8.dp), color=sectionBackground(section.type), shape=RoundedCornerShape(10.dp)) {
        Text(section.label.ifBlank { section.type.replaceFirstChar { it.uppercase() } },
            Modifier.padding(horizontal=14.dp,vertical=12.dp).semantics { heading() },
            fontSize=13.sp,fontWeight=FontWeight.Bold,letterSpacing=.7.sp,color=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun readerChordColor(value: String): Color {
    val dark=MaterialTheme.colorScheme.background.luminance()<.3f
    return when (value) {
        "Blue" -> if (dark) Color(0xFFA8C7FA) else Color(0xFF174EA6)
        "Amber" -> if (dark) Color(0xFFFFD180) else Color(0xFF795000)
        else -> MaterialTheme.colorScheme.primary
    }
}
