package com.henry.chord

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Phase2EngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule val migration = MigrationTestHelper(instrumentation, ChordDatabase::class.java)
    private fun isolated(): Context {
        val root = File(instrumentation.targetContext.cacheDir, "phase2-${Canonical.uuid()}").apply { mkdirs() }
        return object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir() = File(root, "files").apply { mkdirs() }
            override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        }
    }
    @Test fun migrationKeepsOriginalRowsAndReferences() {
        val name = "migration-phase2-test"
        migration.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO songs VALUES (42,'Preserve me','Original Artist','F#m','[F#m]é  東京',1,'My note',-3,2,123456,'https://example.com/song')")
            db.execSQL("INSERT INTO setlists VALUES (8,'My session')")
            db.execSQL("INSERT INTO setlist_entries VALUES (8,42,7)")
        }
        migration.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT a.id,a.originalKey,u.favorite,u.notes,p.transpose,p.capo,u.lastOpened FROM arrangements a JOIN song_user_state u ON a.songUuid=u.songUuid JOIN arrangement_user_state p ON a.id=p.arrangementId").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(42, c.getInt(0)); assertEquals("F#m", c.getString(1)); assertEquals(1, c.getInt(2)); assertEquals("My note", c.getString(3)); assertEquals(-3, c.getInt(4)); assertEquals(2, c.getInt(5)); assertEquals(123456, c.getInt(6))
            }
            db.query("SELECT songId,position FROM setlist_entries").use { assertTrue(it.moveToFirst()); assertEquals(42, it.getInt(0)); assertEquals(7, it.getInt(1)) }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
    private fun makeZip(context: Context, count: Int, extras: Boolean = false): File {
        val file = File(context.cacheDir, "batch-${Canonical.uuid()}.zip")
        ZipOutputStream(file.outputStream()).use { out ->
            repeat(count) { i ->
                val doc = SheetParser.parse("{title: Batch original $i}\n{artist: Chordroom Tests}\n{key: G}\n{start_of_verse: Verse 1}\n[G]  é 👩🏽‍🎤 東京  [D/F#]remain\n\n[Em7][C]", "batch.cho").document
                out.putNextEntry(ZipEntry("songs/Title$i/song.json")); out.write(Canonical.encode(doc).toByteArray()); out.closeEntry()
            }
            if (extras) {
                out.putNextEntry(ZipEntry("bad.json")); out.write("{broken".toByteArray()); out.closeEntry()
                out.putNextEntry(ZipEntry("invalid-utf8.cho")); out.write(byteArrayOf(0xC3.toByte(), 0x28)); out.closeEntry()
            }
        }
        return file
    }
    @Test fun imports320UnicodeArrangementsAndRepeatSkipsAll(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        try {
            val manager = BulkImportManager(context, db.dao()); val zip = makeZip(context, 320, true)
            val stage = manager.discover(listOf(Uri.fromFile(zip)))
            assertEquals(320, stage.candidates.size); assertEquals(2, stage.issues.size)
            assertTrue(stage.issues.any { "UTF-8" in it.message })
            val result = manager.commit(stage); assertEquals(320, result.successes); assertEquals(320, db.dao().count())
            val row = db.dao().songs().first().first(); val doc = Canonical.decode(row.documentJson).single().document
            val summary = db.dao().librarySummaries().first().first()
            assertTrue(summary.body.isEmpty()); assertTrue(summary.documentJson.isEmpty()); assertTrue(summary.notes.isEmpty())
            val originalFingerprint = Canonical.fingerprint(doc)
            db.dao().markPlayed(summary.id); db.dao().favorite(summary.id, true)
            assertEquals(originalFingerprint, Canonical.fingerprint(Canonical.decode(db.dao().song(summary.id)!!.documentJson).single().document))
            assertTrue(db.dao().song(summary.id)!!.favorite)
            assertEquals("  é 👩🏽‍🎤 東京  remain", doc.arrangements.single().sections.single().lines.first().lyric)
            val again = manager.discover(listOf(Uri.fromFile(zip)))
            assertTrue(again.candidates.all { it.choice == ImportChoice.SKIP && it.duplicateId != null })
            assertEquals(320, manager.commit(again).skips); assertEquals(320, db.dao().count())
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun replaceKeepsPersonalStateAndNewVersionSharesSongIdentity(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build(); val dao = db.dao()
        try {
            val id = dao.insert(Song(title = "Versioned", artist = "Tests", body = "[C]Old lyric", favorite = true, notes = "Keep notes", transpose = 4, capo = 2))
            val set = dao.insertSetlist(Setlist(name = "Keep queue")); dao.addEntry(SetlistEntry(set, id, 5))
            val old = dao.song(id)!!; val d = Canonical.decode(old.documentJson).single().document
            val changed = SheetParser.parse("[G]Changed exact lyric", "new.cho", d).document
            val file = File(context.cacheDir, "version.json").apply { writeText(Canonical.encode(changed)) }
            val manager = BulkImportManager(context, dao); val stage = manager.discover(listOf(Uri.fromFile(file)))
            val replaced = manager.commit(stage.copy(candidates = stage.candidates.map { it.copy(choice = ImportChoice.REPLACE) }))
            assertEquals(1, replaced.successes); val current = dao.song(id)!!
            assertEquals(old.songUuid, current.songUuid); assertEquals(old.arrangementUuid, current.arrangementUuid)
            assertTrue(current.favorite); assertEquals("Keep notes", current.notes); assertEquals(4, current.transpose); assertEquals(2, current.capo)
            assertEquals(5, dao.entries().first().single().position)
            val version = manager.discover(listOf(Uri.fromFile(file)))
            val explicitVersion = version.copy(candidates = version.candidates.map { it.copy(choice = ImportChoice.NEW_VERSION) })
            assertEquals(1, manager.commit(explicitVersion).successes)
            val all = dao.songs().first(); assertEquals(2, all.size); assertEquals(1, all.map { it.songUuid }.distinct().size); assertEquals(2, all.map { it.arrangementUuid }.distinct().size)
            manager.commit(explicitVersion) // replay an old journal after a simulated COMMIT/journal-write crash gap
            assertEquals(2, dao.count())
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun cancelsAndResumesWithoutDuplicatingCommittedSongs(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        try {
            val manager = BulkImportManager(context, db.dao()); val pack = makeZip(context, 30)
            try { manager.discover(listOf(Uri.fromFile(pack))) { if (it.phase == "Validating songs" && it.completed == 3) throw CancellationException("Cancel discovery") }; fail("Discovery cancellation expected") } catch (_: CancellationException) { }
            assertTrue(File(context.filesDir, "import_sessions").listFiles().orEmpty().isEmpty())
            val stage = manager.discover(listOf(Uri.fromFile(pack)))
            try { manager.commit(stage) { if (it.completed == 10) throw CancellationException("Test interruption") }; fail("Cancellation expected") } catch (_: CancellationException) { }
            assertEquals(10, db.dao().count()); val resumed = manager.recover()!!
            assertFalse(resumed.finished); assertEquals(10, resumed.successes)
            val result = manager.commit(resumed); assertEquals(30, result.successes); assertEquals(30, db.dao().count())
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun zipTraversalAndOversizedEntriesAreRejected(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        try {
            val zip = File(context.cacheDir, "unsafe.zip")
            ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("../escape.cho")); out.write("[C]Unsafe".toByteArray()); out.closeEntry() }
            val manager = BulkImportManager(context, db.dao()); val stage = manager.discover(listOf(Uri.fromFile(zip)))
            assertTrue(stage.candidates.isEmpty()); assertTrue(stage.issues.any { "Unsafe" in it.message })
            assertFalse(File(context.filesDir, "escape.cho").exists())
            val bomb = File(context.cacheDir, "bomb.zip")
            ZipOutputStream(bomb.outputStream()).use { out -> out.putNextEntry(ZipEntry("huge.cho")); repeat(5) { out.write(ByteArray(1024 * 1024) { 'a'.code.toByte() }) }; out.closeEntry() }
            val rejected = manager.discover(listOf(Uri.fromFile(bomb))); assertTrue(rejected.candidates.isEmpty()); assertTrue(rejected.issues.any { "4 MB" in it.message })
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun batchDuplicatesAndMismatchedSongIdsNeverMergeSilently(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        try {
            val original = SheetParser.parse("{title: Same name}\n{artist: Tests}\n{source_id: shared}\n[C]Original line", "one.cho").document
            db.dao().store(original)
            val other = original.copy(title = "Unrelated title", arrangements = listOf(original.arrangements.single().copy(id = Canonical.uuid())))
            val file = File(context.cacheDir, "collision.json").apply { writeText(Canonical.encode(other)) }
            val manager = BulkImportManager(context, db.dao()); val stage = manager.discover(listOf(Uri.fromFile(file)))
            assertEquals(ImportChoice.SKIP, stage.candidates.single().choice)
            assertTrue(stage.candidates.single().warnings.any { "Identity collision" in it })
            assertEquals(0, manager.commit(stage).successes); assertEquals(1, db.dao().count())
            val zip = File(context.cacheDir, "within-batch.zip")
            ZipOutputStream(zip.outputStream()).use { out -> repeat(2) { index -> out.putNextEntry(ZipEntry("copy$index.cho")); out.write("{title: Batch duplicate}\n{artist: Tests}\n[C]Same exact text".toByteArray()); out.closeEntry() } }
            val batch = manager.discover(listOf(Uri.fromFile(zip)))
            assertEquals(ImportChoice.IMPORT, batch.candidates[0].choice); assertEquals(ImportChoice.SKIP, batch.candidates[1].choice)
            val committed = manager.commit(batch); assertEquals(1, committed.successes); assertEquals(1, committed.skips)
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun artworkCopiesOfflineAndCollectionRoundTrips(): Unit = runBlocking {
        val context = isolated(); val db = Room.inMemoryDatabaseBuilder(context, ChordDatabase::class.java).build()
        val secondContext = isolated(); val second = Room.inMemoryDatabaseBuilder(secondContext, ChordDatabase::class.java).build()
        try {
            val art = File(context.cacheDir, "cover.png"); val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888); bitmap.eraseColor(0xFF247C69.toInt()); art.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            val d = SheetParser.parse("{title: Artwork original}\n{key: G}\n[G]Art [D]stays", "art.cho").document.copy(artwork = ArtworkRef("assets/cover.png", credit = "Original test art"))
            val zip = File(context.cacheDir, "art.zip")
            ZipOutputStream(zip.outputStream()).use { out -> out.putNextEntry(ZipEntry("songs/Art/song.json")); out.write(Canonical.encode(d).toByteArray()); out.closeEntry(); out.putNextEntry(ZipEntry("assets/cover.png")); art.inputStream().use { it.copyTo(out) }; out.closeEntry() }
            val manager = BulkImportManager(context, db.dao()); val stage = manager.discover(listOf(Uri.fromFile(zip)))
            assertEquals(1, stage.covers); assertNotNull(stage.candidates.single().artworkSource); assertEquals(1, manager.commit(stage).successes)
            zip.delete(); art.delete(); manager.discard(stage)
            val row = db.dao().songs().first().single(); assertTrue(File(context.filesDir, row.artworkPath!!).isFile)
            val originalDocument = Canonical.decode(row.documentJson).single().document
            db.dao().store(originalDocument.copy(arrangements = listOf(originalDocument.arrangements.single().copy(id = Canonical.uuid()))))
            assertEquals(2, db.dao().count()) // two intentionally identical versions must both survive export
            val thumb = ArtworkStore(context).thumbnailPath(row.artworkPath, row.songUuid); val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }; android.graphics.BitmapFactory.decodeFile(thumb.path, options); assertEquals(256, options.outWidth); assertEquals(256, options.outHeight)
            val archive = File(context.cacheDir, "export.zip"); CollectionExport(context, db.dao()).write(Uri.fromFile(archive))
            val imported = BulkImportManager(secondContext, second.dao()); val result = imported.commit(imported.discover(listOf(Uri.fromFile(archive))))
            assertEquals(2, result.successes); val importedRows = second.dao().songs().first(); val again = importedRows.first { it.arrangementUuid == row.arrangementUuid }; assertEquals(row.songUuid, again.songUuid)
            assertEquals(db.dao().arrangements().map { it.arrangementUuid }.toSet(), importedRows.map { it.arrangementUuid }.toSet())
            assertEquals(Canonical.fingerprint(Canonical.decode(row.documentJson).single().document), Canonical.fingerprint(Canonical.decode(again.documentJson).single().document))
            assertTrue(File(secondContext.filesDir, again.artworkPath!!).exists())
            val repeat = imported.commit(imported.discover(listOf(Uri.fromFile(archive))))
            assertEquals(0, repeat.successes); assertEquals(2, repeat.skips); assertEquals(2, second.dao().count())
        } finally { db.close(); second.close(); context.filesDir.parentFile!!.deleteRecursively(); secondContext.filesDir.parentFile!!.deleteRecursively() }
    }
}
