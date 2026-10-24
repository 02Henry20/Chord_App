package com.henry.chord

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.zip.*

class ReleaseEngineTest {
    @get:Rule val migration=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),ChordDatabase::class.java)
    private fun isolated(): ContextWrapper {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"release-engine-${Canonical.uuid()}")
        return object: ContextWrapper(base) {
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
        }
    }
    @Test fun largeEditorStateUsesCacheKeysInsteadOfBinderBundles() {
        val context=isolated()
        try {
            val text="Un[C]der écho 日本\n".repeat(120000)
            val key=EditorDrafts.save(context,text)
            assertTrue(key.length<100); assertEquals(text,EditorDrafts.restore(context,key))
            assertNull(EditorDrafts.restore(context,"../escape.txt"))
        } finally { context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun migrationPreservesSetlistOrderAndAddsRepeatableIdentity() {
        val name="release-migration-${Canonical.uuid()}"
        migration.createDatabase(name,3).apply {
            execSQL("INSERT INTO setlists VALUES (17,'Old session')")
            execSQL("INSERT INTO catalog_songs VALUES ('s','Old title','Artist')")
            execSQL("INSERT INTO arrangements VALUES (42,'a','s','Main','C','[C]Old','', '{}',NULL,'hash',NULL,'identity')")
            execSQL("INSERT INTO setlist_entries VALUES (17,42,6)")
            close()
        }
        val db=migration.runMigrationsAndValidate(name,4,true,MIGRATION_3_4)
        db.query("SELECT setlistId,songId,position,entryId,transposeOverride,notes FROM setlist_entries").use { c -> assertTrue(c.moveToFirst()); assertEquals(17L,c.getLong(0)); assertEquals(42L,c.getLong(1)); assertEquals(6,c.getInt(2)); assertTrue(c.getString(3).isNotBlank()); assertTrue(c.isNull(4)); assertEquals("",c.getString(5)) }
        db.query("SELECT body,fingerprint FROM arrangements").use { assertTrue(it.moveToFirst()); assertEquals("[C]Old",it.getString(0)); assertEquals("hash",it.getString(1)) }
        db.close(); InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }
    @Test fun fullBackupEmptyRestoreRepeatMergeAndTraversalDefense(): Unit = runBlocking {
        val source=isolated(); val target=isolated()
        val a=Room.inMemoryDatabaseBuilder(source,ChordDatabase::class.java).build(); val b=Room.inMemoryDatabaseBuilder(target,ChordDatabase::class.java).build()
        try {
            val cover=File(source.cacheDir,"cover.png"); val image=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888); image.eraseColor(0xFF365C9A.toInt()); cover.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
            val art=ArtworkStore(source).commit(cover,"test")
            val doc=SheetParser.parse("{title: Café 日本}\n{artist: Original}\n{key: C}\n{start_of_verse: Verse 1}\n[C]Un[G]der écho [F]夜\n\n[C/E]Stay","test.cho").document.copy(artwork=ArtworkRef(art.path,credit="Original fixture"),tags=listOf("été","日本"))
            val id=a.dao().store(doc); val second=a.dao().store(doc.copy(arrangements=listOf(doc.arrangements.single().copy(id=Canonical.uuid(),name="Acoustic",originalKey="Am",tonalMode="minor"))))
            a.dao().saveUserState(SongUserState(doc.songId,true,"Skip final verse",123456))
            a.dao().saveOffsets(ArrangementUserState(id,2,1)); a.dao().saveOffsets(ArrangementUserState(second,-2,0))
            val set=a.dao().insertSetlist(Setlist(name="Encore")); val first=SetlistEntry(set,id,0,transposeOverride=-3,notes="Soft intro"); val repeat=SetlistEntry(set,id,1,transposeOverride=4,notes="Repeat")
            a.dao().addEntry(first); a.dao().addEntry(repeat); a.dao().reorder(listOf(repeat,first))
            val prefs=Preferences(chordLabels=LabelMode.ROMAN,scrollSpeed=73f,leadIn=3,lineSpacing=1.7f,chordColor="Blue",sheetMargin=29f,songLineGap=17f,chordLineGap=8f,sectionGap=23f)
            val zip=File(source.cacheDir,"backup.zip"); BackupManager(source,a).write(zip,prefs)
            val manager=BackupManager(target,b); val preview=manager.stage(Uri.fromFile(zip)); assertEquals(1,preview.songs); assertEquals(2,preview.versions); assertEquals(1,preview.covers); assertEquals(1,preview.sets)
            assertEquals(prefs,PreferenceFormat.decode(preview.manifest.getJSONObject("settings")))
            val otherFile=File(target.cacheDir,"concurrent.cho").apply { writeText("{title: Concurrent original}\n[C]Keep the artwork safe") }
            val otherImport=BulkImportManager(target,b.dao()); val otherStage=otherImport.discover(listOf(Uri.fromFile(otherFile)))
            coroutineScope {
                val restore=async { manager.restore(preview,false,false) }
                val importing=async { otherImport.commit(otherStage) }
                assertEquals(2,restore.await().imported); assertEquals(1,importing.await().successes)
            }
            b.dao().songs().first().single { it.title=="Concurrent original" }.let { b.dao().delete(it) }
            val rows=b.dao().songs().first(); assertEquals(2,rows.size)
            val row=rows.single { it.arrangementUuid == doc.arrangements.single().id }
            assertTrue(row.favorite); assertEquals("Skip final verse",row.notes); assertEquals(2,row.transpose); assertEquals(1,row.capo); assertEquals(123456L,row.lastOpened)
            assertEquals(Canonical.fingerprint(doc),Canonical.fingerprint(Canonical.decode(row.documentJson).single().document))
            assertEquals(File(source.filesDir,art.path!!).readBytes().toList(),File(target.filesDir,row.artworkPath!!).readBytes().toList())
            val restored=b.dao().snapshotEntries(); assertEquals(listOf(repeat.entryId,first.entryId),restored.map { it.entryId }); assertEquals(listOf(4,-3),restored.map { it.transposeOverride }); assertEquals(listOf("Repeat","Soft intro"),restored.map { it.notes })
            assertEquals(0,manager.restore(preview,false,false).imported); assertEquals(2,b.dao().count()); assertEquals(2,b.dao().snapshotEntries().size)
            val duplicate=b.dao().duplicateSetlist(b.dao().snapshotSets().single()); assertEquals(2,b.dao().snapshotEntries().count { it.setlistId == duplicate })
            assertEquals(2,manager.restore(preview,true,false).imported); assertEquals(1,b.dao().snapshotSets().size)
            val bad=File(source.cacheDir,"bad.zip"); ZipOutputStream(bad.outputStream()).use { out -> out.putNextEntry(ZipEntry("../escape.txt")); out.write("bad".toByteArray()); out.closeEntry() }
            try { manager.stage(Uri.fromFile(bad)); fail("Traversal accepted") } catch (_: IllegalArgumentException) {}
            File(preview.root,"songs").listFiles()!!.first().appendText("damaged")
            try { manager.restore(preview,true,false); fail("Damaged backup accepted") } catch (_: IllegalArgumentException) {}
            assertEquals("Damaged restore preserves existing database",2,b.dao().count())
        } finally { a.close(); b.close(); source.filesDir.parentFile!!.deleteRecursively(); target.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun editorAnchorSaveReloadAndCanonicalTransposeIsolation(): Unit = runBlocking {
        val context=isolated(); val db=Room.inMemoryDatabaseBuilder(context,ChordDatabase::class.java).build()
        try {
            val id=db.dao().insert(Song(title="Anchor test",body="{comment:Verse}\nUn[C]der [G]écho"))
            val before=db.dao().song(id)!!; val doc=Canonical.decode(before.documentJson).single().document; val arr=doc.arrangements.single()
            val line=arr.sections.flatMap { it.lines }.first { it.lyric.isNotBlank() }
            val changed=AnchorEditor.edit(line,"One "+line.lyric)
            val a=arr.copy(originalKey="G",tonalMode="major",tempoBpm=96.0,timeSignature="3/4",sections=arr.sections.map { s -> s.copy(lines=s.lines.map { if (it.id==line.id) changed else it }) })
            db.dao().saveEditor(before.copy(key="G",transpose=2),"Album",2026,listOf("Test"),MusicLinks(),null,a)
            val after=db.dao().song(id)!!; val saved=Canonical.decode(after.documentJson).single().document.arrangements.single()
            assertEquals(2,after.transpose); assertEquals("G",saved.originalKey); assertEquals(96.0,saved.tempoBpm!!,0.0)
            val anchors=saved.sections.flatMap { it.lines }.first { it.id==line.id }.let(SheetLayout::anchors)
            assertEquals(listOf("C","G"),anchors.map { it.anchor.rawChord }); assertEquals(6,anchors.first().offset)
            assertEquals(SheetLayout.anchors(line).map { it.anchor.id },anchors.map { it.anchor.id })
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
}
