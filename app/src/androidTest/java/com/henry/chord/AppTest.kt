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
