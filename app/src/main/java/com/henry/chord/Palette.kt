package com.henry.chord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

/** Offline, predictable accent colors with tested foreground contrast. */
object ChordPalette {
    const val LightBackground=0xFFFCF9FF; const val LightInk=0xFF252239; const val LightMuted=0xFF625D76
    const val LightPrimary=0xFF6343B5; const val LightSecondary=0xFF006B61; const val LightTertiary=0xFF8A4800
    const val DarkBackground=0xFF141320; const val DarkInk=0xFFEDE7FA; const val DarkMuted=0xFFC2BBD4
    const val DarkPrimary=0xFFD0BCFF; const val DarkSecondary=0xFF79DAC9; const val DarkTertiary=0xFFFFC278
    fun contrast(a: Long,b: Long): Double {
        fun luminance(c: Long): Double {
            fun component(shift: Int): Double { val s=((c shr shift) and 255)/255.0; return if (s<=.04045) s/12.92 else Math.pow((s+.055)/1.055,2.4) }
            return component(16)*.2126+component(8)*.7152+component(0)*.0722
        }
        val x=luminance(a); val y=luminance(b); return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
}
internal val light=lightColorScheme(
    primary=Color(ChordPalette.LightPrimary),onPrimary=Color.White,primaryContainer=Color(0xFFECE1FF),onPrimaryContainer=Color(0xFF392268),
    secondary=Color(ChordPalette.LightSecondary),secondaryContainer=Color(0xFFB8F2E6),onSecondaryContainer=Color(0xFF00382F),
    tertiary=Color(ChordPalette.LightTertiary),tertiaryContainer=Color(0xFFFFDDB5),onTertiaryContainer=Color(0xFF432200),
    background=Color(ChordPalette.LightBackground),onBackground=Color(ChordPalette.LightInk),surface=Color(ChordPalette.LightBackground),onSurface=Color(ChordPalette.LightInk),
    surfaceVariant=Color(0xFFF0EAF8),onSurfaceVariant=Color(ChordPalette.LightMuted),outline=Color(0xFF797188),outlineVariant=Color(0xFFDCD3E9),
    surfaceContainer=Color(0xFFF2ECFA),surfaceContainerHigh=Color(0xFFEDE5F7),surfaceContainerHighest=Color(0xFFE7DEF2),surfaceContainerLow=Color(0xFFF7F1FC),surfaceContainerLowest=Color.White)
internal val dark=darkColorScheme(
    primary=Color(ChordPalette.DarkPrimary),onPrimary=Color(0xFF302052),primaryContainer=Color(0xFF49327C),onPrimaryContainer=Color(0xFFEADDFF),
    secondary=Color(ChordPalette.DarkSecondary),secondaryContainer=Color(0xFF004D43),onSecondaryContainer=Color(0xFFB8F2E6),
    tertiary=Color(ChordPalette.DarkTertiary),tertiaryContainer=Color(0xFF653500),onTertiaryContainer=Color(0xFFFFDDB5),
    background=Color(ChordPalette.DarkBackground),onBackground=Color(ChordPalette.DarkInk),surface=Color(ChordPalette.DarkBackground),onSurface=Color(ChordPalette.DarkInk),
    surfaceVariant=Color(0xFF2D283C),onSurfaceVariant=Color(ChordPalette.DarkMuted),outline=Color(0xFF978CA8),outlineVariant=Color(0xFF4A405A),
    surfaceContainer=Color(0xFF201C2D),surfaceContainerHigh=Color(0xFF292336),surfaceContainerHighest=Color(0xFF342C42),surfaceContainerLow=Color(0xFF1B1727),surfaceContainerLowest=Color(0xFF0F0D17))

@Composable
fun RainbowAccent() {
    Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Color(0xFFDD6BA4),Color(0xFFF1A24C),Color(0xFFD5B84B),Color(0xFF4FBF9C),Color(0xFF5EA9E2),Color(0xFF9D7ADD)))))
}
