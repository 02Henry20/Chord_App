package com.henry.chord

import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** No Compose test clock: frame timing and gestures here run against the real Android display clock. */
class ReleasePerformanceTest {
    private val inst=InstrumentationRegistry.getInstrumentation(); private val context=inst.targetContext
    private val device=UiDevice.getInstance(inst)
    private fun click(text: String,description: Boolean=false) {
        val selector=if (description) By.desc(text) else By.text(text)
        val item=device.wait(Until.findObject(selector),10000) ?: error("Missing UI: $text")
        val candidates=device.findObjects(selector).filter { it.className != "android.widget.EditText" }; (candidates.lastOrNull() ?: item).click(); Thread.sleep(250)
    }
    private fun shot(name: String) {
        Thread.sleep(350); val dir=File(context.filesDir,"release-evidence").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun position(): Pair<Int,Int> {
        fun find(node: AccessibilityNodeInfo?): String? {
            if (node==null) return null
            if (node.contentDescription?.toString()=="Song sheet") return node.stateDescription?.toString()
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        val value=find(inst.uiAutomation.rootInActiveWindow) ?: error("Reader position is not exposed")
        return value.split(':').let { it[0].toInt() to it[1].toInt() }
    }
    private fun distance(a: Pair<Int,Int>,b: Pair<Int,Int>) = (b.first-a.first)*10000+b.second-a.second
    private fun slider(fraction: Float) {
        val seek=device.wait(Until.findObject(By.clazz("android.widget.SeekBar")),5000) ?: error("Speed slider missing")
        val r=seek.visibleBounds; device.click((r.left+r.width()*fraction).toInt(),r.centerY()); Thread.sleep(250)
    }
    @Test fun actualScrollSpeedManualPauseFullscreenAndChordSheet(): Unit = runBlocking {
        val db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
        val dao=db.dao(); val prefs=Settings(context).flow.first(); val count=dao.count(); var id=0L
        try {
            Settings(context).save(prefs.copy(theme=ThemeMode.LIGHT,chordLabels=LabelMode.SYMBOLS,instrument="Piano",scrollSpeed=8f,leadIn=0,fontSize=19,sheetDisplay=SheetDisplay.BOTH,lineSpacing=1.35f,keepAwake=true))
            id=dao.insert(Song(title="Release scroll original",artist="QA original",key="C",body="{start_of_chorus: Chorus}\n"+(0 until 100).joinToString("\n") { "[C]Un[G]der écho 日本 [F]line $it [F#m7b5]again" }))
            val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            click("Library")
            val search=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!; search.text="Release scroll original"; Thread.sleep(600); if (device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) { device.pressBack(); Thread.sleep(250) }
            click("Release scroll original"); check(device.wait(Until.hasObject(By.text("KEY C")),10000)); shot("01-reader-symbols")
            fun awake(): Boolean { var value=false; inst.runOnMainSync { value=activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }; return value }
            assertTrue("Keep-awake flag enabled",awake())
            Settings(context).save(Settings(context).flow.first().copy(keepAwake=false)); Thread.sleep(350); assertFalse("Keep-awake option is applied",awake())
            Settings(context).save(Settings(context).flow.first().copy(keepAwake=true))
            click("Chord labels: Chord symbols. Change for this reader",true); shot("02-reader-roman")
            click("Transpose up",true); click("Transpose up",true)
            assertTrue(device.wait(Until.hasObject(By.text("KEY D")),5000)); click("Chord I. Sounding D",true)
            assertTrue(device.wait(Until.hasObject(By.text("D4  F#4  A4")),5000)); shot("03-roman-tap-piano-D")
            click("Guitar"); assertTrue(device.wait(Until.hasObject(By.textContains("Voicing")),5000)); shot("04-guitar-D")
            click("Done"); click("Chord labels: Roman numerals. Change for this reader",true); shot("05-reader-both")
            click("Fullscreen reader",true); device.wait(Until.findObject(By.text("Got it")),1500)?.click(); Thread.sleep(350); shot("06-fullscreen-paused")
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_SPACE); assertTrue(device.wait(Until.hasObject(By.desc("Pause auto-scroll")),5000))
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_SPACE); assertTrue(device.wait(Until.hasObject(By.desc("Start auto-scroll")),5000))
            val before=position(); click("Start auto-scroll",true); Thread.sleep(1100); click("Pause auto-scroll",true); val slow=position()
            val paused=position(); Thread.sleep(500); assertEquals("Pause holds position",paused,position())
            slider(.9f)
            assertTrue("Speed persists",Settings(context).flow.first().scrollSpeed>80f)
            val fastStart=position(); click("Start auto-scroll",true); Thread.sleep(1100); click("Pause auto-scroll",true); val fast=position()
            assertTrue("Slow speed moves",distance(before,slow)>10)
            assertTrue("Speed control changes actual motion",distance(fastStart,fast)>distance(before,slow)*3)
            click("Start auto-scroll",true)
            val sheet=device.wait(Until.findObject(By.desc("Song sheet")),5000)!!.visibleBounds
            device.swipe(sheet.centerX(),sheet.bottom-80,sheet.centerX(),sheet.top+100,25)
            assertTrue("Manual drag pauses",device.wait(Until.hasObject(By.desc("Start auto-scroll")),5000))
            click("Start auto-scroll",true); Thread.sleep(450); shot("07-fullscreen-playing")
            device.pressHome(); Thread.sleep(400)
            inst.runOnMainSync { context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            assertTrue("Background pauses",device.wait(Until.hasObject(By.desc("Start auto-scroll")),5000))
            click("Reader options",true); click("Return to top"); Thread.sleep(300); assertEquals(0,position().first)
            click("Start auto-scroll",true); click("Chord D (I). Sounding D",true); click("Piano")
            assertTrue(device.wait(Until.hasObject(By.text("D4  F#4  A4")),5000)); click("Done")
            assertTrue("Chord sheet pauses",device.wait(Until.hasObject(By.desc("Start auto-scroll")),5000))
            device.setOrientationLeft(); Thread.sleep(1800); check(device.wait(Until.hasObject(By.desc("Reader options")),10000)); Thread.sleep(600); shot("08-landscape-reader")
            device.setOrientationNatural(); Thread.sleep(1800)
            device.executeShellCommand("settings put system font_scale 1.5"); Thread.sleep(2000); check(device.wait(Until.hasObject(By.desc("Reader options")),10000)); Thread.sleep(600); shot("09-large-text-reader")
            device.executeShellCommand("settings put system font_scale 1.0"); Thread.sleep(1800)
            click("Reader options",true); click("Lyrics only"); click("Done"); shot("10-lyrics-only")
            click("Reader options",true); click("Chords only"); click("Done"); shot("11-chords-only")
            click("Reader options",true); click("Chords and lyrics"); click("Done")
            // Jump to the last section/end using manual accessibility scrolling, then start and stop at the bottom.
            val finalBounds=device.wait(Until.findObject(By.desc("Song sheet")),5000)!!.visibleBounds
            var attempts=0
            while (!device.hasObject(By.text("END OF SONG")) && attempts++<60) {
                device.swipe(finalBounds.centerX(),finalBounds.bottom-100,finalBounds.centerX(),finalBounds.top+100,10); Thread.sleep(100)
            }
            assertTrue("Reached final line",device.wait(Until.hasObject(By.text("END OF SONG")),5000))
            Thread.sleep(400); click("Start auto-scroll",true)
            assertTrue("Stops at the bottom",device.wait(Until.hasObject(By.desc("Start auto-scroll")),15000))
            device.pressBack(); assertTrue("Back exits fullscreen first",device.wait(Until.hasObject(By.desc("Fullscreen reader")),5000))
            device.pressBack(); assertTrue("Back returns to library",device.wait(Until.hasObject(By.text("Your library")),5000))
            File(context.filesDir,"release-evidence/scroll-results.json").writeText(JSONObject().put("before",before.toString()).put("slow",slow.toString()).put("fastStart",fastStart.toString()).put("fast",fast.toString()).put("manualPause",true).put("backgroundPause",true).put("chordPause",true).put("stopsAtBottom",true).toString(2))
            File(context.filesDir,"release-evidence/reader-gfxinfo.txt").writeText(device.executeShellCommand("dumpsys gfxinfo com.henry.chord framestats"))
            File(context.filesDir,"release-evidence/reader-meminfo.txt").writeText(device.executeShellCommand("dumpsys meminfo com.henry.chord"))
        } catch (e: Throwable) { shot("performance-failure"); device.dumpWindowHierarchy(File(context.filesDir,"release-evidence/performance-failure.xml")); throw e }
        finally {
            device.executeShellCommand("settings put system font_scale 1.0"); device.setOrientationNatural(); device.unfreezeRotation()
            if (id != 0L) dao.song(id)?.let { dao.delete(it) }; Settings(context).save(prefs); assertEquals(count,dao.count()); db.close()
        }
    }
}
