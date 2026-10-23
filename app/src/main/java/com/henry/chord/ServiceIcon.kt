package com.henry.chord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Compact native vector marks; no network artwork or external icon library. */
@Composable
fun ServiceIcon(service: LinkService) {
    Canvas(Modifier.size(24.dp).testTag("service-${service.name}")) {
        val w=size.width; val h=size.height
        if (service==LinkService.SPOTIFY) {
            drawCircle(Color(0xFF1ED760))
            repeat(3) { index ->
                val y=.34f+index*.16f
                val path=Path().apply { moveTo(w*.23f,h*y); quadraticTo(w*.52f,h*(y-.10f),w*(.79f-index*.04f),h*(y+.04f)) }
                drawPath(path,Color(0xFF121212),style=Stroke(w*(.085f-index*.012f),cap=StrokeCap.Round))
            }
        } else {
            drawRoundRect(Color(0xFFFF0033),topLeft=Offset(0f,h*.14f),size=Size(w,h*.72f),cornerRadius=CornerRadius(w*.18f))
            drawPath(Path().apply { moveTo(w*.42f,h*.32f); lineTo(w*.42f,h*.68f); lineTo(w*.7f,h*.5f); close() },Color.White)
        }
    }
}
