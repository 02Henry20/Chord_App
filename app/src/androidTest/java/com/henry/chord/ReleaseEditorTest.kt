package com.henry.chord

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ReleaseEditorTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val inst=InstrumentationRegistry.getInstrumentation(); private val device=UiDevice.getInstance(inst)
    private fun await(text: String) { rule.waitUntil(20000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(350); val dir=File(inst.targetContext.filesDir,"release-evidence").apply { mkdirs() }; File(dir,"$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun native(name: String): UiObject {
        val item=device.findObject(UiSelector().resourceId("android:id/title").text(name))
        if (!item.waitForExists(1500)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name))
        check(item.waitForExists(5000)) { "Picker missing $name" }; return item
    }
    @Test fun structuredEditAndRealSafBackup() {
        val context=inst.targetContext; val db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build(); val dao=db.dao()
        val prefs=runBlocking { Settings(context).flow.first() }; val before=runBlocking { dao.count() }
        val backupName="Chordroom-release-backup-${System.currentTimeMillis()}.zip"; val name="Release editor original"; val setName="Release session ${System.currentTimeMillis()}"; var id=0L; var setId=0L
        try {
            runBlocking { Settings(context).save(prefs.copy(theme=ThemeMode.LIGHT,chordLabels=LabelMode.SYMBOLS,sheetDisplay=SheetDisplay.BOTH)) }
            await("Make room\nfor music."); rule.onNodeWithText("Library").performClick(); await("Your library")
            rule.onNodeWithContentDescription("New song").performClick(); rule.onNodeWithText("Song title").performTextInput(name); rule.onNodeWithText("Artist").performTextInput("Original QA")
            rule.onNodeWithText("Edit line 1").performScrollTo().performClick()
            rule.onNodeWithText("Lyric text · tap to place cursor").performTextInput("Under the écho 日本\nSoft ending")
            rule.onNodeWithText("Lyric text · tap to place cursor").performTextInputSelection(TextRange(2))
            rule.onNodeWithText("Insert chord at cursor").performScrollTo().performClick()
            rule.onNodeWithText("Chord symbol").performTextReplacement("G")
            rule.onNodeWithText("Lyric text · tap to place cursor").performScrollTo().performTextInputSelection(TextRange(10))
            rule.onNodeWithText("Insert chord at cursor").performScrollTo().performClick()
            rule.onNodeWithText("Lyric text · tap to place cursor").performScrollTo().performTextInputSelection(TextRange(15))
            rule.onNodeWithText("Move selected chord to cursor").performScrollTo().performClick(); shot("12-line-anchor-editor-light")
            rule.onNodeWithText("Apply line").performClick()
            rule.onNodeWithText("Tempo (BPM)").performScrollTo().performTextInput("96"); rule.onNodeWithText("Time signature").performTextInput("4/4")
            rule.onNodeWithText("Your notes").performScrollTo().performTextInput("Quiet intro")
            rule.onNodeWithText("Save").performScrollTo().performClick(); await("Your library")
            id=runBlocking { dao.songs().first().single { it.title==name }.id }
            val saved=runBlocking { dao.song(id)!! }; val arrangement=Canonical.decode(saved.documentJson).single().document.arrangements.single()
            assertEquals(listOf(2,15),SheetLayout.anchors(arrangement.sections.single().lines.first()).map { it.offset })
            assertEquals("Soft ending",arrangement.sections.single().lines.last().lyric); assertEquals("Quiet intro",saved.notes); assertEquals(96.0,arrangement.tempoBpm!!,0.0)
            rule.onNodeWithTag("library-search").performTextReplacement(name); rule.waitUntil(10000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("favorite-$id").performClick(); rule.onNodeWithTag("song-$id").performClick(); await("KEY C")
            rule.onNodeWithContentDescription("Edit song").performClick(); rule.onNodeWithText("Key").performTextReplacement("G"); shot("13-editor-light")
            rule.onNodeWithText("Save").performScrollTo().performClick(); await("KEY G")
            rule.onNodeWithContentDescription("Edit song").performClick(); rule.onNodeWithText("Song title").performTextReplacement("Unsaved title")
            rule.activityRule.scenario.recreate(); await("Edit song"); rule.onNodeWithText("Song title").assertTextContains("Unsaved title")
            rule.onNodeWithContentDescription("Cancel editing").performScrollTo().performClick(); await("Discard unsaved changes?"); rule.onNodeWithText("Discard changes").performClick(); await("KEY G")
            assertEquals(name,runBlocking { dao.song(id)!!.title })
            rule.onNodeWithContentDescription("Back").performClick(); rule.onNodeWithText("Settings").performClick()
            rule.onNodeWithTag("category-Chords & instrument").performScrollTo().performClick(); rule.onNodeWithTag("label-ROMAN").performScrollTo().performClick()
            rule.onNodeWithTag("category-Appearance").performScrollTo().performClick(); rule.onNodeWithTag("theme-DARK").performScrollTo().performClick(); shot("15-settings-dark")
            rule.onNodeWithTag("category-Library & backup").performScrollTo().performClick()
            rule.onNodeWithText("Export full backup ZIP").performScrollTo().performClick()
            val roots=device.findObject(UiSelector().description("Show roots")); check(roots.waitForExists(10000)); roots.click(); native("sdk_gphone_x86").click(); native("Documents").click()
            val filename=device.findObject(UiSelector().resourceId("android:id/title").className("android.widget.EditText"))
            if (filename.exists()) filename.setText(backupName) else {
                val edit=device.findObject(UiSelector().className("android.widget.EditText")); check(edit.waitForExists(5000)); edit.setText(backupName)
            }
            shot("16-native-backup-save-picker")
            val save=device.findObject(UiSelector().textMatches("(?i)save")); check(save.waitForExists(5000)); save.click()
            rule.waitUntil(45000) { rule.onAllNodesWithText("Backup saved:",substring=true).fetchSemanticsNodes().isNotEmpty() }
            runBlocking { Settings(context).save(Settings(context).flow.first().copy(chordLabels=LabelMode.BOTH)) }
            rule.onNodeWithText("Restore full backup").performScrollTo().performClick()
            val openRoots=device.findObject(UiSelector().description("Show roots")); check(openRoots.waitForExists(10000)); openRoots.click(); native("sdk_gphone_x86").click(); native("Documents").click(); native(backupName).click()
            await("Review backup"); shot("17-backup-merge-preview")
            rule.onNodeWithText("Replace this library").performClick(); rule.onNodeWithText("Restore backup").assertIsNotEnabled(); shot("17b-backup-replace-warning")
            rule.onNodeWithText("Merge into this library").performClick(); rule.onNodeWithText("Restore backup").performClick()
            rule.waitUntil(45000) { rule.onAllNodesWithText("Restored ",substring=true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(LabelMode.ROMAN,runBlocking { Settings(context).flow.first().chordLabels }); assertEquals(before+1,runBlocking { dao.count() }); assertTrue(runBlocking { dao.song(id)!!.favorite })
            rule.activityRule.scenario.recreate(); await("Make it yours."); rule.onNodeWithTag("category-Chords & instrument").performScrollTo().performClick(); rule.onNodeWithTag("label-ROMAN").performScrollTo().assertExists(); shot("18-settings-restored-dark")
            rule.onNodeWithText("Library").performClick(); rule.onNodeWithTag("library-search").performTextReplacement(name)
            rule.waitUntil(10000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("song-$id").performClick(); rule.onNodeWithContentDescription("Edit song").performClick(); shot("20-editor-dark")
        } catch (e: Throwable) { shot("editor-failure"); throw e }
        finally {
            runBlocking { dao.snapshotSets().filter { it.name==setName }.forEach { dao.deleteSetlist(it) }; if (id!=0L) dao.song(id)?.let { dao.delete(it) }; Settings(context).save(prefs); assertEquals(before,dao.count()) }; db.close(); rule.activityRule.scenario.recreate()
        }
    }
}
