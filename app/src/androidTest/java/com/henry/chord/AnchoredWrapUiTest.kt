package com.henry.chord

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File

/** Verifies pixel positions against Android's actual shaped text after wrapping/reflow. */
class AnchoredWrapUiTest {
    @get:Rule val rule=createComposeRule()
    @Test fun wrappedMidWordAdjacentUnicodeChordsTrackActualGlyphPositions() {
        val line=SheetLine("wrap",listOf(TextSegment("s0","Un"),TextSegment("s1","der écho 日本 ",ChordAnchor("a","F#m7b5")),TextSegment("s2","",ChordAnchor("b","C/E")),TextSegment("s3","e\u0301lan 👩🏽‍🎤 with a longer ending",ChordAnchor("c","Bbmaj7"))))
        var width by mutableIntStateOf(210); var font by mutableIntStateOf(19); var both by mutableStateOf(false)
        rule.setContent { MaterialTheme { Box(Modifier.width(width.dp)) { CanonicalSheetLine(line,font,{ if (both) "$it (bVIImaj7)" else it },{ it },{}) } } }
        listOf(210 to 19,160 to 27,300 to 19).forEachIndexed { iteration,(w,f) ->
            rule.runOnIdle { width=w; font=f; both=iteration==1 }
            rule.waitForIdle()
            val nodes=rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes()
            val lyrics=nodes.filter { !it.config.contains(SemanticsActions.OnClick) }.sortedBy { it.boundsInRoot.top }
            assertEquals("Wrapping must preserve exact source whitespace/Unicode",line.lyric,lyrics.joinToString("") { it.config[SemanticsProperties.Text].single().text })
            SheetLayout.anchors(line).forEach { anchor ->
                var offset=0
                val lyricNode=lyrics.first { node ->
                    val length=node.config[SemanticsProperties.Text].single().text.length
                    if (anchor.offset>=offset && anchor.offset<offset+length) true else { offset+=length; false }
                }
                val layouts=mutableListOf<TextLayoutResult>()
                lyricNode.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                val expected=lyricNode.boundsInRoot.left+layouts.single().getHorizontalPosition(anchor.offset-offset,true)
                val label=if (both) "${anchor.anchor.rawChord} (bVIImaj7)" else anchor.anchor.rawChord
                val chord=rule.onNodeWithContentDescription("Chord $label. Sounding ${anchor.anchor.rawChord}").fetchSemanticsNode()
                assertTrue("${anchor.anchor.rawChord} must sit above its exact shaped character",abs(expected-chord.boundsInRoot.left)<=2f)
                assertTrue("Chord must be above its lyric row",chord.boundsInRoot.bottom<=lyricNode.boundsInRoot.top+1f)
            }
            val inst=InstrumentationRegistry.getInstrumentation()
            val dir=File(inst.targetContext.filesDir,"rc3-evidence").apply { mkdirs() }
            File(dir,"wrap-${w}dp-${f}sp.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
