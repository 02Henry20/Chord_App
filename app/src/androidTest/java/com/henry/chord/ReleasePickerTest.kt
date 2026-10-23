package com.henry.chord

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.regex.Pattern

/** Uses only newly inserted fixtures; never deletes pre-existing demo packs or user songs. */
class ReleasePickerTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val inst=InstrumentationRegistry.getInstrumentation(); private val device=UiDevice.getInstance(inst)
    private fun await(text: String) { rule.waitUntil(30000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun native(name: String): UiObject {
        val item=device.findObject(UiSelector().resourceId("android:id/title").text(name))
        if (!item.waitForExists(2000)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name))
        check(item.waitForExists(5000)) { "Missing picker file: $name" }; return item
    }
    private fun root() {
        val roots=device.findObject(UiSelector().description("Show roots")); check(roots.waitForExists(10000)); roots.click(); native("sdk_gphone_x86").click(); native("Documents").click(); native("ChordroomRelease").click()
        // All fixture filenames are visible in list mode; grid tiles truncate long names.
        device.wait(Until.findObject(By.desc("List view")),600)?.click()
    }
    private fun fresh() {
        rule.onNodeWithText("Import").performClick(); await("Bring your songs.")
        if (rule.onAllNodesWithText("New import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("New import").performClick()
        if (rule.onAllNodesWithText("Discard staged import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Discard staged import").performClick()
        await("Choose files or ZIP")
    }
    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(350); val dir=File(inst.targetContext.filesDir,"release-evidence").apply { mkdirs() }; File(dir,"$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) } }
    @Test fun nativeMultipleRecursiveFolderZipDuplicateAndMalformed() {
        val db=Room.databaseBuilder(inst.targetContext,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build(); val dao=db.dao()
        val before=runBlocking { dao.arrangements() }.associateBy { it.id }
        var sourceMoved=false
        try {
            await("Make room\nfor music.")
            fresh(); rule.onNodeWithText("Choose files or ZIP").performClick(); root(); native("Release-single.json").click(); await("Import selected (1)")
            rule.onNodeWithText("Import selected (1)").performClick(); await("Import complete")
            fresh(); rule.onNodeWithText("Choose files or ZIP").performClick(); root(); native("Release-multiple.cho").longClick(); native("Release-second.pro").click()
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)(select|open)"))),5000)!!.click(); await("Import selected (2)"); shot("21-multiple-import-preview")
            rule.onNodeWithText("Import selected (2)").performClick(); await("Import complete")
            fresh(); rule.onNodeWithText("Choose a folder").performClick(); root(); native("ReleaseFolder").click()
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)use this folder"))),5000)!!.click(); device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))),5000)?.click()
            await("Import selected (1)"); rule.onNodeWithContentDescription("Preview Release folder original").performClick(); await("Looks good"); shot("22-folder-artwork-preview"); rule.onNodeWithText("Looks good").performClick()
            rule.onNodeWithText("Import selected (1)").performClick(); await("Import complete"); shot("23-folder-import-summary")
            fresh(); rule.onNodeWithText("Choose files or ZIP").performClick(); root(); native("Release-pack.zip").click(); await("Import selected (1)")
            rule.onNodeWithText("Review 1 file errors").performClick(); await("File errors"); shot("24-malformed-file-report"); rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("Import selected (1)").performClick(); await("Import complete")
            assertEquals(before.size+5,runBlocking { dao.count() })
            fresh(); rule.onNodeWithText("Choose files or ZIP").performClick(); root(); native("Release-pack.zip").click(); await("Import selected (0)"); rule.onNodeWithText("Import selected (0)").assertIsNotEnabled(); shot("25-duplicate-skip")
            fresh(); rule.onNodeWithText("Choose files or ZIP").performClick(); root(); native("Release-unsafe.zip").click(); await("Review your import")
            rule.onNodeWithText("Review 1 file errors").performClick(); await("File errors"); shot("26-path-defense"); rule.onNodeWithText("Done").performClick()
            assertEquals(before.size+5,runBlocking { dao.count() })
            check(device.executeShellCommand("mv /sdcard/Documents/ChordroomRelease /sdcard/Documents/ChordroomRelease-unavailable").trim().isEmpty()); sourceMoved=true
            rule.activityRule.scenario.recreate(); await("Bring your songs.")
            runBlocking { val folder=dao.arrangements().single { it.documentJson.contains("Release folder original") }; assertTrue(File(inst.targetContext.filesDir,folder.artworkPath!!).exists()) }
            rule.onNodeWithText("Library").performClick(); rule.onNodeWithTag("library-search").performTextReplacement("Release folder original")
            rule.waitUntil(10000) { rule.onAllNodes(hasText("Release folder original") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("Release folder original") and !hasSetTextAction()).performClick(); await("KEY C"); shot("27-reader-source-unavailable")
        } catch (e: Throwable) { shot("picker-failure"); device.dumpWindowHierarchy(File(inst.targetContext.filesDir,"release-evidence/picker-failure.xml")); throw e }
        finally {
            if (sourceMoved) device.executeShellCommand("mv /sdcard/Documents/ChordroomRelease-unavailable /sdcard/Documents/ChordroomRelease")
            runBlocking {
                dao.arrangements().filter { it.id !in before && it.documentJson.contains("Release original fixtures") }.forEach { dao.song(it.id)?.let { row -> dao.delete(row) } }
                assertEquals(before.size,dao.count()); before.forEach { (id,row) -> assertEquals(row.documentJson,dao.arrangement(id)!!.documentJson) }
            }; db.close()
        }
    }
}
