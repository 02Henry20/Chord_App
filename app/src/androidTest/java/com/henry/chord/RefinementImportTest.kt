package com.henry.chord

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.*

class RefinementImportTest {
    @Test fun installedIconLoadsTheSuppliedArtworkAsAnAdaptiveIcon() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val icon=context.packageManager.getApplicationIcon(context.packageName)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        context.resources.openRawResource(com.henry.chord.R.drawable.ic_launcher_art).use { input ->
            val bytes=ByteArray(32768); while (true) { val n=input.read(bytes); if (n<0) break; digest.update(bytes,0,n) }
        }
        assertEquals("568ba1571624d3fd3193301a691b7a03652e85b9754f286f910c8f711ceb3f1d",digest.digest().joinToString("") { "%02x".format(it) })
        val bitmap=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888)
        icon.setBounds(0,0,108,108); icon.draw(android.graphics.Canvas(bitmap))
        val center=bitmap.getPixel(54,54); assertTrue(android.graphics.Color.red(center)>180 && android.graphics.Color.blue(center)>180)
        bitmap.recycle()
    }
    private fun isolated(): ContextWrapper {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"refinement-${Canonical.uuid()}")
        return object: ContextWrapper(base) {
            override fun getFilesDir()=File(root,"files").apply { mkdirs() }
            override fun getCacheDir()=File(root,"cache").apply { mkdirs() }
        }
    }
    private fun valid()=SheetParser.parse("{title: Strict original}\n{artist: Original QA}\n{key: C}\n{start_of_solo: Solo}\n[C] [F] [G]","strict.cho").document
    @Test fun mixedZipRejectsInvalidFilesAndCopiesValidInstrumentalArtwork(): Unit=runBlocking {
        val context=isolated(); val db=Room.inMemoryDatabaseBuilder(context,ChordDatabase::class.java).build()
        try {
            val cover=File(context.cacheDir,"cover.png")
            val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888); bitmap.eraseColor(android.graphics.Color.MAGENTA)
            cover.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            val good=valid().copy(artwork=ArtworkRef("images/cover.png"))
            val zip=File(context.cacheDir,"strict-pack.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                fun member(name: String,bytes: ByteArray) { out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() }
                member("nested/song.json",Canonical.encode(good).toByteArray())
                member("nested/images/cover.png",cover.readBytes())
                member("broken.json","{\"schemaVersion\":1,}".toByteArray())
                member("wrong-type.json",Canonical.json(valid()).put("title",17).toString().toByteArray())
                member("bad-chord.cho","{title: Bad chord}\n[H7]Unrecognized".toByteArray())
                member("missing-art.json",Canonical.encode(valid().copy(artwork=ArtworkRef("missing.png"))).toByteArray())
            }
            val manager=BulkImportManager(context,db.dao()); val stage=manager.discover(listOf(Uri.fromFile(zip)))
            assertEquals(1,stage.candidates.size); assertEquals(4,stage.issues.size)
            assertTrue(stage.issues.any { it.path=="strict-pack/broken.json" && it.message.contains("JSON line") })
            assertTrue(stage.issues.any { it.path.endsWith("wrong-type.json") && it.message.contains("$.title") })
            assertTrue(stage.issues.joinToString(),stage.issues.any { it.path.endsWith("bad-chord.cho") && it.message.contains("Line 2") })
            assertTrue(stage.issues.any { it.path.endsWith("missing-art.json") && it.message.contains("artwork.path") })
            val result=manager.commit(stage); assertEquals(1,result.successes); assertEquals(1,db.dao().count())
            zip.delete(); cover.delete()
            val row=db.dao().song(db.dao().arrangements().single().id)!!
            assertTrue(File(context.filesDir,row.artworkPath!!).isFile)
            val imported=Canonical.decode(row.documentJson).single().document
            assertEquals(listOf("C","F","G"),imported.arrangements.single().sections.single().lines.single().segments.mapNotNull { it.anchor?.rawChord })
            val again=File(context.cacheDir,"again.json").apply { writeText(Canonical.encode(valid())) }
            assertEquals(0,manager.discover(listOf(Uri.fromFile(again))).candidates.count { it.choice==ImportChoice.IMPORT })
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
    @Test fun stagedDraftRevalidationAndFailedReviewPreserveExistingData(): Unit=runBlocking {
        val context=isolated(); val db=Room.inMemoryDatabaseBuilder(context,ChordDatabase::class.java).build()
        try {
            val baseline=db.dao().store(valid()); val before=db.dao().song(baseline)!!.documentJson
            val manager=BulkImportManager(context,db.dao())
            val source=File(context.cacheDir,"changed.cho").apply { writeText("{title: New draft}\n[C]Keep me safe") }
            val stage=manager.discover(listOf(Uri.fromFile(source)))
            assertEquals(stage.issues.joinToString(),1,stage.candidates.size)
            File(stage.candidates.single().draft).writeText("{\"schemaVersion\":1,}")
            val result=manager.commit(stage); assertEquals(0,result.successes); assertTrue(result.issues.any { it.message.contains("JSON line") })
            assertEquals(1,db.dao().count()); assertEquals(before,db.dao().song(baseline)!!.documentJson)
            source.writeText("C       G\nMorning lights")
            val txt=File(context.cacheDir,"proposal.txt").apply { writeText(source.readText()) }
            val proposal=manager.discover(listOf(Uri.fromFile(txt))); val candidate=proposal.candidates.single()
            assertTrue(candidate.needsReview); assertFalse(candidate.reviewed)
            try { manager.review(proposal,candidate.id,"[H7]Bad edit"); fail("Invalid review accepted") } catch (_: IllegalArgumentException) {}
            assertEquals(1,db.dao().count()); assertEquals(before,db.dao().song(baseline)!!.documentJson)
        } finally { db.close(); context.filesDir.parentFile!!.deleteRecursively() }
    }
}
