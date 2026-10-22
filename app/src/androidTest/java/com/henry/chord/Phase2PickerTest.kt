package com.henry.chord

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/** Requires tools/generate_demo_packs.py + adb push docs/demo-packs /sdcard/Documents/ChordroomPhase2. */
class Phase2PickerTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private fun await(text: String, timeout: Long = 30_000) { rule.waitUntil(timeout) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String) {
        device.waitForIdle(); Thread.sleep(300); rule.waitForIdle(); Thread.sleep(200)
        val dir = File(instrumentation.targetContext.filesDir, "phase2-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        device.dumpWindowHierarchy(File(dir, "$name.xml"))
    }
    private fun nativeFile(name: String): UiObject {
        device.waitForIdle(); Thread.sleep(200)
        val result = device.findObject(UiSelector().resourceId("android:id/title").text(name))
        if (!result.waitForExists(5_000)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name))
        check(result.waitForExists(5_000)) { "System picker did not show $name" }
        return result
    }
    private fun demoRoot() {
        val roots = device.findObject(UiSelector().description("Show roots")); check(roots.waitForExists(10_000)); roots.click(); device.waitForIdle(); Thread.sleep(300)
        val storage = device.findObject(UiSelector().resourceId("android:id/title").text("sdk_gphone_x86")); check(storage.waitForExists(5_000)); storage.click()
        device.waitForIdle(); Thread.sleep(300)
        nativeFile("Documents").click(); device.waitForIdle()
        nativeFile("ChordroomPhase2").click(); device.waitForIdle()
    }
    private fun freshImport() {
        rule.onNodeWithText("Import").performClick(); await("Bring your songs.")
        if (rule.onAllNodesWithText("New import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("New import").performClick()
        if (rule.onAllNodesWithText("Discard staged import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Discard staged import").performClick()
        await("Choose files or ZIP")
    }
    private fun count(): Int = runBlocking { Room.databaseBuilder(instrumentation.targetContext, ChordDatabase::class.java, "chord.db").addMigrations(MIGRATION_1_2).build().let { db -> try { db.dao().count() } finally { db.close() } } }
    private fun removeOnlyFixturesCreatedByThisTest(): Unit = runBlocking {
        val db = Room.databaseBuilder(instrumentation.targetContext, ChordDatabase::class.java, "chord.db").addMigrations(MIGRATION_1_2).build()
        try {
            val dao = db.dao()
            for (record in dao.arrangements()) {
                val doc = Canonical.decode(record.documentJson).single().document
                val owned = record.sourceId?.startsWith("chordroom-original:") == true || doc.title in setOf("Folder bridge original", "River file original") && doc.artist == "Chordroom Originals" || doc.title == "Review-positions" && doc.artist == "Imported" && record.body.contains("Hello")
                if (owned) dao.song(record.id)?.let { dao.delete(it) }
            }
        } finally { db.close() }
    }

    @Test fun realPickersImportMultipleFolderZipAndRepeatThenReopen() {
        try {
            await("Make room\nfor music.")
            removeOnlyFixturesCreatedByThisTest(); rule.activityRule.scenario.recreate(); await("Make room\nfor music.")
            val before = count()
            freshImport(); rule.onNodeWithText("Choose files or ZIP").performClick(); demoRoot()
            nativeFile("Evening-single.json").longClick(); nativeFile("River-single.cho").click()
            screenshot("01-system-multiple-selection")
            val confirm = device.findObject(UiSelector().textMatches("(?i)(select|open)")); check(confirm.waitForExists(5_000)); confirm.click()
            await("Review your import"); await("Import selected (2)"); screenshot("02-multiple-preview")
            rule.onNodeWithText("Import selected (2)").performClick(); await("Import complete"); assertEquals(before + 2, count())

            freshImport(); rule.onNodeWithText("Choose a folder").performClick(); demoRoot(); nativeFile("ChordroomFolder").click(); device.waitForIdle()
            screenshot("03-system-folder-selection")
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)use this folder"))), 5_000)!!.click()
            device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))), 5_000)?.click()
            await("Import selected (2)"); screenshot("04-recursive-folder-preview")
            rule.onNodeWithContentDescription("Preview Folder original 001").performClick(); await("Looks good"); screenshot("05-artwork-and-anchors-preview"); rule.onNodeWithText("Looks good").performClick()
            rule.onNodeWithText("Import selected (2)").performClick(); await("Import complete"); assertEquals(before + 4, count())

            freshImport(); rule.onNodeWithText("Choose files or ZIP").performClick(); demoRoot(); nativeFile("Chordroom-320.zip").click()
            await("Import selected (320)", 120_000); screenshot("06-bulk-320-preview")
            rule.onNodeWithText("Review 2 file errors").performClick(); await("File errors"); screenshot("07-per-file-errors"); rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("Import selected (320)").performClick(); await("Import complete", 120_000); assertEquals(before + 324, count()); screenshot("08-bulk-summary")
            freshImport(); rule.onNodeWithText("Choose files or ZIP").performClick(); demoRoot(); nativeFile("Chordroom-320.zip").click()
            await("Import selected (0)", 120_000); screenshot("09-repeat-import-skips")
            rule.onNodeWithText("Import selected (0)").assertIsNotEnabled(); assertEquals(before + 324, count())
            rule.onNodeWithContentDescription("Preview Pack original 000").performClick(); await("Existing arrangement"); screenshot("09c-duplicate-comparison"); rule.onNodeWithText("Looks good").performClick()
            rule.onNodeWithText("Import selected (0)").assertIsNotEnabled()
            rule.onNodeWithText("Finish without importing").performClick(); await("Import complete", 60_000)
            await("0 imported · 320 skipped"); screenshot("09b-repeat-summary")

            freshImport(); rule.onNodeWithText("Choose files or ZIP").performClick(); demoRoot(); nativeFile("Review-positions.txt").click()
            await("Review your import"); rule.onNodeWithContentDescription("Preview Review-positions").performClick(); await("Accept reviewed positions"); screenshot("10-txt-explicit-review"); rule.onNodeWithText("Accept reviewed positions").performClick()
            await("Import selected (1)"); rule.onNodeWithText("Import selected (1)").performClick(); await("Import complete"); assertEquals(before + 325, count())

            rule.onNodeWithText("Library").performClick(); await("Your library")
            rule.onNodeWithText("Find a song, artist or lyric").performTextInput("Pack original 000")
            rule.waitUntil(15_000) { rule.onAllNodes(hasText("Pack original 000") and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasText("Pack original 000") and !hasSetTextAction()).performClick(); await("KEY G"); screenshot("11-canonical-reader")
            rule.activityRule.scenario.recreate(); await("KEY G"); screenshot("12-reopened-offline-reader")
            val report = File(instrumentation.targetContext.filesDir, "phase2-evidence/picker-report.json")
            report.writeText("""{"baseline":$before,"after":${count()},"multiFileImported":2,"recursiveFolderImported":2,"zipImported":320,"txtReviewedImported":1,"repeatAdded":0,"recreatedReader":true}""")
        } catch (error: Throwable) { screenshot("failure"); throw error }
    }
}
