package com.henry.chord

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        rule.waitForIdle()
        Thread.sleep(500) // Allow SurfaceFlinger to present frames on the software-rendered AVD.
        rule.waitForIdle()
        Thread.sleep(150)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.filesDir, "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun awaitText(text: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun completeSessionOnDevice() {
        val testTitle = "Device test ${System.currentTimeMillis()}"
        val testSetName = "$testTitle session"
        val originalPreferences = runBlocking { Settings(rule.activity).flow.first() }
        try {
        awaitText("Make room\nfor music."); screenshot("01-home")
        rule.onNodeWithText("Library").performClick()
        awaitText("Your library"); screenshot("02-library")
        rule.onNodeWithText("Find a song, artist or lyric").performTextInput("Morning gathers")
        awaitText("A little closer")
        rule.onNodeWithText("A little closer").performClick()
        awaitText("KEY C"); screenshot("03-reader")
        rule.onNodeWithContentDescription("Transpose up").performClick()
        awaitText("KEY Db")
        rule.onNodeWithContentDescription("Transpose down").performClick()
        awaitText("KEY C")
        rule.onAllNodesWithText("C").onFirst().performClick()
        awaitText("Guitar"); screenshot("04-guitar")
        rule.onNodeWithText("Piano").performClick(); screenshot("05-piano")
        rule.onNodeWithText("Done").performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Start auto-scroll").performClick()
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithContentDescription("Pause auto-scroll").assertExists().performClick()
        rule.mainClock.advanceTimeBy(32)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithContentDescription("Fullscreen reader").performClick()
        rule.onNodeWithContentDescription("Exit fullscreen").assertExists(); screenshot("06-fullscreen")
        rule.onNodeWithContentDescription("Exit fullscreen").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithContentDescription("New song").performClick()
        rule.onNodeWithText("Song title").performTextInput(testTitle)
        rule.onNodeWithText("Artist").performTextInput("Test session")
        rule.onNodeWithText("Advanced: ChordPro editor").performScrollTo().performClick()
        rule.onNodeWithText("Chords & lyrics").performScrollTo().performTextInput("{comment:Verse}\n[C]An original [G]test line")
        rule.onNodeWithText("Your notes").performScrollTo().performTextInput("Remember a quiet intro")
        rule.onNodeWithText("Save").performScrollTo().performClick()
        awaitText("Your library")
        rule.onNodeWithText(testTitle).performClick()
        awaitText("KEY C")
        rule.activityRule.scenario.recreate()
        awaitText("KEY C")
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Setlists").performClick()
        awaitText("Set the mood."); screenshot("07-setlists")
        rule.onNodeWithContentDescription("New setlist").performClick()
        rule.onNodeWithText("Setlist name").performTextInput(testSetName)
        rule.onNodeWithText("Save").performClick()
        awaitText("Running order")
        rule.onNodeWithText("Add songs").performClick()
        rule.onNodeWithText(testTitle).performClick()
        rule.onNodeWithText("A little closer").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("Move A little closer up").performClick()
        rule.onNodeWithText("Play setlist").performClick()
        awaitText("A little closer")
        rule.onNodeWithContentDescription("Next song").performClick()
        awaitText(testTitle)
        rule.onNodeWithContentDescription("Previous song").assertIsEnabled()
        rule.onNodeWithContentDescription("Exit fullscreen").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithContentDescription("Delete setlist").performClick()
        rule.onNodeWithText("Delete", useUnmergedTree = false).performClick()
        awaitText("Set the mood.")
        rule.onNodeWithText("Import").performClick(); awaitText("Bring your songs."); screenshot("08-import")
        rule.onNodeWithText("Settings").performClick(); awaitText("Make it yours."); screenshot("09-settings")
        rule.onNodeWithTag("theme-DARK").performClick()
        rule.waitUntil(5_000) { runBlocking { Settings(rule.activity).flow.first().dark } }
        rule.onNodeWithTag("theme-DARK").assertIsSelected()
        screenshot("10-dark")
        rule.activityRule.scenario.recreate()
        awaitText("Make it yours.")
        rule.onNodeWithTag("theme-DARK").assertIsSelected()
        rule.onNodeWithTag("theme-LIGHT").performClick()
        rule.waitUntil(5_000) { runBlocking { !Settings(rule.activity).flow.first().dark } }
        rule.onNodeWithText("Library").performClick()
        rule.onNodeWithText(testTitle).performClick()
        rule.onNodeWithContentDescription("Edit song").performClick()
        rule.onNodeWithText("Delete song").performScrollTo().performClick()
        rule.onNodeWithText("Delete", useUnmergedTree = false).performClick()
        awaitText("Your library")
        rule.onNodeWithText("Home").performClick()
        } finally {
            rule.mainClock.autoAdvance = true
            runBlocking {
                Settings(rule.activity).save(originalPreferences)
                val db=Room.databaseBuilder(rule.activity,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
                try {
                    db.dao().snapshotSets().filter { it.name==testSetName }.forEach { db.dao().deleteSetlist(it) }
                    db.dao().songs().first().filter { it.title==testTitle && it.artist=="Test session" }.forEach { db.dao().delete(it) }
                } finally { db.close() }
            }
        }
    }

    @Test fun roomSearchImportAndPersistence(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testRoot = File(base.cacheDir, "legacy-test-${Canonical.uuid()}")
        val context = object : android.content.ContextWrapper(base) {
            override fun getFilesDir() = File(testRoot, "files").apply { mkdirs() }
            override fun getCacheDir() = File(testRoot, "cache").apply { mkdirs() }
        }
        val database = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        val dao = database.dao()
        try {
            val fixture = File(context.cacheDir, "test-fixture.cho")
            fixture.writeText("{title: Test river}\n{artist: Test artist}\n{key: D}\n[D]Silver river [G]flows")
            val manager = ImportManager(context, dao)
            assertTrue(manager.files(listOf(Uri.fromFile(fixture))).startsWith("1 song"))
            assertTrue(manager.files(listOf(Uri.fromFile(fixture))).startsWith("0 songs"))
            val song = dao.search(SongImport.searchQuery("river")).first().single()
            dao.update(song.copy(notes = "Saved note", favorite = true, transpose = 2, capo = 3))
            assertEquals(2, dao.songs().first().single().transpose)
            assertEquals("Saved note", dao.songs().first().single().notes)
            assertEquals(1, dao.search(SongImport.searchQuery("Silver")).first().size)
            val zip = File(context.cacheDir, "test-songs.zip")
            ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("nested/second.cho")); out.write("{title: ZIP original}\n[C]Zip lyric".toByteArray()); out.closeEntry() }
            assertTrue(manager.files(listOf(Uri.fromFile(zip))).startsWith("1 song"))
            val set = dao.insertSetlist(Setlist(name = "Test"))
            dao.addEntry(SetlistEntry(set, song.id, 0))
            dao.delete(song)
            assertTrue(dao.entries().first().isEmpty())
            assertTrue(dao.search(SongImport.searchQuery("Silver")).first().isEmpty())
            fixture.delete(); zip.delete()
        } finally { database.close(); testRoot.deleteRecursively() }
    }
}
