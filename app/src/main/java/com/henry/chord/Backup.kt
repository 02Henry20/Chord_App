package com.henry.chord

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.*
import kotlin.coroutines.coroutineContext

object PreferenceFormat {
    fun encode(p: Preferences): JSONObject = JSONObject().put("version",1).put("theme",p.theme.name).put("instrument",p.instrument).put("fontSize",p.fontSize)
        .put("keepAwake",p.keepAwake).put("chordLabels",p.chordLabels.name).put("spelling",p.spelling.name).put("scrollSpeed",p.scrollSpeed)
        .put("leadIn",p.leadIn).put("lineSpacing",p.lineSpacing).put("chordColor",p.chordColor).put("sheetDisplay",p.sheetDisplay.name)
    fun decode(o: JSONObject): Preferences {
        require(o.getInt("version") == 1) { "Unsupported settings version. Update Chordroom." }
        val theme=ThemeMode.valueOf(o.getString("theme")); val instrument=o.getString("instrument")
        require(instrument in listOf("Piano","Guitar"))
        return Preferences(theme=theme,dark=theme == ThemeMode.DARK,instrument=instrument,fontSize=o.getInt("fontSize").also { require(it in 14..30) },
            keepAwake=o.getBoolean("keepAwake"),chordLabels=LabelMode.valueOf(o.getString("chordLabels")),spelling=Spelling.valueOf(o.getString("spelling")),
            scrollSpeed=o.getDouble("scrollSpeed").toFloat().also { require(it.isFinite() && it in 8f..100f) },leadIn=o.getInt("leadIn").also { require(it in listOf(0,3,5)) },
            lineSpacing=o.getDouble("lineSpacing").toFloat().also { require(it.isFinite() && it in 1f..2f) },chordColor=o.getString("chordColor").also { require(it in listOf("Theme","Blue","Amber")) },sheetDisplay=SheetDisplay.valueOf(o.getString("sheetDisplay")))
    }
}
data class BackupPreview(val root: File,val manifest: JSONObject,val songs: Int,val versions: Int,val covers: Int,val sets: Int)
data class RestoreResult(val imported: Int,val skipped: Int,val setlists: Int,val settingsPending: Boolean = false)

/** Version 1 full backup. Streams bounded ZIP members; DB restore is a single cancellable transaction. */
class BackupManager(private val context: Context,private val database: ChordDatabase) {
    private val dao=database.dao()
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val file=File.createTempFile("backup-",".zip",context.cacheDir)
        try { write(file,Settings(context).flow.first()); context.contentResolver.openOutputStream(uri,"wt")?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("Cannot open backup destination.") }
        finally { file.delete() }
    }
    suspend fun write(file: File,prefs: Preferences) = LibraryFiles.mutex.withLock { withContext(Dispatchers.IO) {
        val manifest=JSONObject().put("format","chordroom-backup").put("schemaVersion",1).put("createdAt",System.currentTimeMillis()).put("settings",PreferenceFormat.encode(prefs))
        val documents=JSONArray(); val assets=linkedSetOf<String>(); val uuids=mutableMapOf<Long,String>()
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            var expanded=0L; var members=0
            fun reserve(size: Long) { expanded+=size; require(++members<=12000 && expanded<=512L*1024*1024) { "Backup exceeds 12,000 files / 512 MB. Export a smaller collection." } }
            fun entry(path: String,bytes: ByteArray) { require(bytes.size <= if (path=="backup.json") 16*1024*1024 else 4*1024*1024) { "Backup member is too large: $path" }; reserve(bytes.size.toLong()); zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
            database.withTransaction {
                val arrangementIds=dao.arrangementIds(); require(arrangementIds.size<=10000) { "At most 10,000 arrangements per backup." }
                for (id in arrangementIds) {
                    coroutineContext.ensureActive()
                    val record=dao.arrangement(id) ?: error("Library changed during backup.")
                    val doc=Canonical.decode(record.documentJson).single().document
                    val art=record.artworkPath?.let { path -> ArtworkStore.safePath(path); require(File(context.filesDir,path).isFile) { "Artwork is missing for ${doc.title}. Repair or change its artwork before backup." }; assets.add(path); doc.artwork?.copy(path="assets/${File(path).name}") }
                    val portable=doc.copy(artwork=art ?: doc.artwork?.copy(path=null))
                    val path="songs/${Canonical.hash(record.arrangementUuid)}.json"; val text=Canonical.encode(portable)
                    entry(path,text.toByteArray(Charsets.UTF_8)); documents.put(JSONObject().put("path",path).put("hash",Canonical.hash(text)))
                    uuids[id]=record.arrangementUuid
                }
                manifest.put("users",JSONArray(dao.snapshotUsers().map { JSONObject().put("songId",it.songUuid).put("favorite",it.favorite).put("notes",it.notes).put("lastOpened",it.lastOpened) }))
                manifest.put("offsets",JSONArray(dao.snapshotOffsets().map { JSONObject().put("arrangementId",uuids.getValue(it.arrangementId)).put("transpose",it.transpose).put("capo",it.capo) }))
                val entries=dao.snapshotEntries()
                manifest.put("setlists",JSONArray(dao.snapshotSets().map { set -> JSONObject().put("id",set.uuid).put("name",set.name).put("entries",JSONArray(entries.filter { it.setlistId == set.id }.sortedBy { it.position }.map { e ->
                    JSONObject().put("id",e.entryId).put("arrangementId",uuids.getValue(e.songId)).put("transposeOverride",e.transposeOverride ?: JSONObject.NULL).put("notes",e.notes)
                })) }))
            }
            for (path in assets) { coroutineContext.ensureActive(); val source=File(context.filesDir,path); ArtworkStore(context).validate(source); reserve(source.length()); zip.putNextEntry(ZipEntry("assets/${source.name}")); source.inputStream().use { it.copyTo(zip,32768) }; zip.closeEntry() }
            manifest.put("documents",documents)
            entry("backup.json",manifest.toString(2).toByteArray(Charsets.UTF_8))
        }
    } }
    suspend fun stage(uri: Uri): BackupPreview = withContext(Dispatchers.IO) {
        val root=File(context.cacheDir,"restore-${Canonical.uuid()}").apply { mkdirs() }
        try {
            var total=0L; var count=0; val names=hashSetOf<String>()
            context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    coroutineContext.ensureActive(); val entry=zip.nextEntry ?: break
                    require(++count<=12000) { "Backup has more than 12,000 files. Split the collection." }
                    val path=ArtworkStore.safePath(entry.name)
                    require(names.add(path)) { "Duplicate archive path: $path" }
                    if (!entry.isDirectory) {
                        val destination=File(root,path).canonicalFile
                        require(destination.path.startsWith(root.canonicalPath+File.separator)) { "Unsafe archive path." }
                        val limit=if (path=="backup.json") 16L*1024*1024 else if (path.startsWith("assets/")) ArtworkStore.MAX_IMAGE_BYTES else 4L*1024*1024
                        destination.parentFile!!.mkdirs(); var size=0L
                        destination.outputStream().use { out -> val bytes=ByteArray(32768); while (true) { coroutineContext.ensureActive(); val n=zip.read(bytes); if (n<0) break; size+=n; total+=n; require(size<=limit && total<=512L*1024*1024) { "Backup exceeds file or 512 MB expanded size limit." }; out.write(bytes,0,n) } }
                    }
                    zip.closeEntry()
                }
            } } ?: error("Cannot read selected backup.")
            validate(root)
        } catch (e: Exception) { root.deleteRecursively(); throw e }
    }
    private fun text(file: File): String = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.readBytes())).toString() }
        catch (e: java.nio.charset.CharacterCodingException) { throw IllegalArgumentException("Malformed UTF-8 in backup file ${file.name}",e) }
    private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
    fun validate(root: File): BackupPreview {
        val m=JSONObject(text(File(root,"backup.json")))
        require(m.getString("format")=="chordroom-backup" && m.getInt("schemaVersion")==1) { "Select a Chordroom backup v1. Newer formats require an app update." }
        PreferenceFormat.decode(m.getJSONObject("settings"))
        val docs=objects(m.getJSONArray("documents")); require(docs.size<=10000) { "At most 10,000 arrangements per backup." }
        val songIds=hashSetOf<String>(); val arrangementIds=hashSetOf<String>(); val covers=hashSetOf<String>(); val paths=hashSetOf<String>()
        for (item in docs) {
            val path=ArtworkStore.safePath(item.getString("path")); require(path.startsWith("songs/") && paths.add(path))
            val value=text(File(root,path)); require(Canonical.hash(value)==item.getString("hash")) { "Damaged song file: $path" }
            val doc=Canonical.decode(value).single().document; val a=doc.arrangements.single()
            require(arrangementIds.add(a.id)) { "Duplicate arrangement ID." }; songIds.add(doc.songId)
            doc.artwork?.path?.let { art -> ArtworkStore.safePath(art); require(art.startsWith("assets/")); ArtworkStore(context).validate(File(root,art)); covers.add(art) }
        }
        val users=objects(m.getJSONArray("users")); require(users.map { it.getString("songId") }.toSet()==songIds && users.size==songIds.size)
        users.forEach { require(it.getLong("lastOpened")>=0); it.getBoolean("favorite"); it.getString("notes") }
        val offsets=objects(m.getJSONArray("offsets")); require(offsets.map { it.getString("arrangementId") }.toSet()==arrangementIds && offsets.size==arrangementIds.size)
        offsets.forEach { require(it.getInt("transpose") in -120..120 && it.getInt("capo") in 0..12) }
        val sets=objects(m.getJSONArray("setlists")); require(sets.size<=10000 && sets.map { it.getString("id") }.distinct().size==sets.size)
        val entryIds=hashSetOf<String>()
        sets.forEach { s -> require(s.getString("id").isNotBlank() && s.getString("name").isNotBlank()); objects(s.getJSONArray("entries")).forEach { e ->
            require(entryIds.add(e.getString("id")) && e.getString("id").isNotBlank() && e.getString("arrangementId") in arrangementIds)
            require(e.isNull("transposeOverride") || e.getInt("transposeOverride") in -24..24); e.getString("notes")
        } }
        return BackupPreview(root,m,songIds.size,arrangementIds.size,covers.size,sets.size)
    }
    suspend fun restore(preview: BackupPreview,replace: Boolean,includeSettings: Boolean): RestoreResult = LibraryFiles.mutex.withLock { withContext(Dispatchers.IO) {
        val validated=validate(preview.root); val m=validated.manifest
        val copied=mutableMapOf<String,String>(); val ids=mutableMapOf<String,Long>(); val newSongs=hashSetOf<String>(); var imported=0; var skipped=0; var setsAdded=0
        val current=dao.arrangements().associateBy { it.arrangementUuid }
        // Prepare immutable managed files before the transaction. Existing artwork is never overwritten.
        objects(m.getJSONArray("documents")).forEach { item ->
            val doc=Canonical.decode(text(File(preview.root,item.getString("path")))).single().document
            doc.artwork?.path?.let { path -> if (path !in copied) copied[path]=ArtworkStore(context).commit(File(preview.root,path),doc.songId).path!! }
        }
        database.withTransaction {
            if (replace) dao.clearLibrary()
            for (item in objects(m.getJSONArray("documents"))) {
                coroutineContext.ensureActive()
                val doc=Canonical.decode(text(File(preview.root,item.getString("path")))).single().document
                val a=doc.arrangements.single(); val existing=if (replace) null else current[a.id]
                if (existing != null) { require(existing.songUuid == doc.songId) { "Arrangement ID belongs to a different song; merge cancelled." }; ids[a.id]=existing.id; skipped++; continue }
                val oldSong=dao.catalog(doc.songId)
                if (oldSong==null) newSongs.add(doc.songId)
                val portable=doc.copy(artwork=doc.artwork?.let { art -> art.copy(path=art.path?.let { copied.getValue(it) }) })
                ids[a.id]=dao.store(portable); imported++
            }
            objects(m.getJSONArray("users")).forEach { u -> if (replace || u.getString("songId") in newSongs) dao.saveUserState(SongUserState(u.getString("songId"),u.getBoolean("favorite"),u.getString("notes"),u.getLong("lastOpened"))) }
            objects(m.getJSONArray("offsets")).forEach { o -> val uuid=o.getString("arrangementId"); if (replace || uuid !in current) dao.saveOffsets(ArrangementUserState(ids.getValue(uuid),o.getInt("transpose"),o.getInt("capo"))) }
            val existingSets=dao.snapshotSets().associateBy { it.uuid }
            val existingEntries=dao.snapshotEntries().associateBy { it.entryId }
            objects(m.getJSONArray("setlists")).forEach { s ->
                val uuid=s.getString("id"); val existing=existingSets[uuid]
                // Merge leaves an existing setlist's edited order/notes intact and is idempotent.
                if (existing == null) {
                    val setId=dao.insertSetlist(Setlist(name=s.getString("name"),uuid=uuid)); setsAdded++
                    objects(s.getJSONArray("entries")).forEachIndexed { i,e ->
                        require(e.getString("id") !in existingEntries) { "Setlist entry ID conflict. Restore to an empty library or use Replace." }
                        dao.addEntry(SetlistEntry(setId,ids.getValue(e.getString("arrangementId")),i,e.getString("id"),if (e.isNull("transposeOverride")) null else e.getInt("transposeOverride"),e.getString("notes")))
                    }
                }
            }
            if (includeSettings) dao.saveMetadata(AppMetadata("pending-settings",m.getJSONObject("settings").toString()))
        }
        // Durable Room journal retries settings after process interruption between Room and DataStore commits.
        val pending=includeSettings && !recoverSettings()
        context.getSharedPreferences("first_run",0).edit().putBoolean("seeded",true).apply()
        RestoreResult(imported,skipped,setsAdded,pending)
    } }
    suspend fun recoverSettings(): Boolean = dao.metadata("pending-settings")?.let { value ->
        try { Settings(context).save(PreferenceFormat.decode(JSONObject(value))); dao.deleteMetadata("pending-settings"); true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false } // Keep the durable journal; a failed settings write cannot cause a startup loop.
    } ?: true
}
