package com.henry.chord

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

enum class ImportChoice { IMPORT, SKIP, REPLACE, NEW_VERSION }
data class ImportIssue(val path: String, val message: String)
data class ImportCandidate(
    val id: String, val source: String, val draft: String, val title: String, val artist: String, val arrangement: String,
    val originalKey: String?, val preview: String, val artworkSource: String?, val warnings: List<String>,
    val needsReview: Boolean, val reviewed: Boolean = false, val duplicateId: Long? = null, val duplicateReason: String? = null,
    val choice: ImportChoice = ImportChoice.IMPORT, val committed: Boolean = false, val songUuid: String = "", val preserveIdentity: Boolean = false
)
data class ImportSession(val id: String, val directory: String, val files: Int, val covers: Int, val candidates: List<ImportCandidate>, val issues: List<ImportIssue>, val finished: Boolean = false, val successes: Int = 0, val skips: Int = 0)
data class ImportProgress(val phase: String, val completed: Int, val total: Int = 0, val path: String = "")

class BulkImportManager(private val context: Context, private val dao: ChordDao) {
    companion object {
        const val MAX_CANDIDATES = 2000
        const val MAX_ENTRIES = 10000
        const val MAX_EXPANDED = 256L * 1024 * 1024
        const val MAX_SONG = 4L * 1024 * 1024
        private val songExtensions = setOf("json", "cho", "chopro", "chordpro", "pro", "txt")
        private val covers = setOf("jpg", "jpeg", "png", "webp")
        fun utf8(file: File): String {
            require(file.length() <= MAX_SONG) { "Song exceeds the 4 MB limit." }
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.readBytes())).toString().removePrefix("\uFEFF")
        }
    }
    private val sessions = File(context.filesDir, "import_sessions").apply { mkdirs() }
    private val artwork = ArtworkStore(context)
    private fun name(uri: Uri): String = if (uri.scheme == "file") uri.lastPathSegment ?: "song.txt" else context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: error("Provider did not return a filename.")

    suspend fun discover(uris: List<Uri> = emptyList(), tree: Uri? = null, progress: (ImportProgress) -> Unit = {}): ImportSession = withContext(Dispatchers.IO) {
        val sessionDir = File(sessions, Canonical.uuid()).apply { mkdirs() }; val raw = File(sessionDir, "raw").apply { mkdirs() }
        val issues = mutableListOf<ImportIssue>(); var files = 0; var expanded = 0L; val used = mutableSetOf<String>()
        suspend fun copy(input: InputStream, path: String, cap: Long, output: Boolean = true) {
            val target = if (output) File(raw, ArtworkStore.safePath(path)).also { require(it.canonicalPath.startsWith(raw.canonicalPath + File.separator)) } else null
            if (target != null) { require(used.add(path.lowercase())) { "Duplicate/case-colliding path: $path" }; target.parentFile?.mkdirs() }
            var size = 0L
            try {
                val sink = target?.outputStream()
                try { val buffer = ByteArray(32 * 1024); while (true) {
                    coroutineContext.ensureActive(); val count = input.read(buffer); if (count < 0) break
                    size += count; expanded += count
                    require(size <= cap) { "File exceeds ${cap / 1024 / 1024} MB: $path" }
                    require(expanded <= MAX_EXPANDED) { "Expanded import exceeds 256 MB. Split this pack into smaller ZIPs/folders." }
                    sink?.write(buffer, 0, count)
                } } finally { sink?.close() }
            } catch (e: Exception) { target?.delete(); throw e }
        }
        suspend fun zip(input: InputStream, prefix: String) {
            ZipInputStream(input).use { stream ->
                while (true) {
                    coroutineContext.ensureActive(); val entry = stream.nextEntry ?: break
                    require(++files <= MAX_ENTRIES) { "Import exceeds 10,000 file entries." }
                    val path = "$prefix/${entry.name.removeSuffix("/")}"
                    progress(ImportProgress("Discovering ZIP", files, path = entry.name))
                    // Stop this archive on unsafe entries; closeEntry alone could inflate an unbounded bomb.
                    ArtworkStore.safePath(entry.name.removeSuffix("/"))
                    if (!entry.isDirectory) {
                        val ext = entry.name.substringAfterLast('.').lowercase(); val accepted = ext in songExtensions || ext in covers || entry.name.substringAfterLast('/') == "collection.index"
                        copy(stream, path, if (ext in covers) ArtworkStore.MAX_IMAGE_BYTES else MAX_SONG, accepted)
                        if (entry.compressedSize > 0 && entry.size >= 1024 * 1024 && entry.size.toDouble() / entry.compressedSize > 200) { File(raw, path).delete(); error("ZIP compression ratio exceeds 200:1: ${entry.name}") }
                    }
                    stream.closeEntry()
                }
            }
        }
        suspend fun ingest(uri: Uri, path: String) {
            val ext = path.substringAfterLast('.').lowercase()
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (ext == "zip") zip(input, path.substringBeforeLast('.'))
                else { require(++files <= MAX_ENTRIES) { "Import exceeds 10,000 file entries." }; progress(ImportProgress("Copying files", files, path = path)); copy(input, path, if (ext in covers) ArtworkStore.MAX_IMAGE_BYTES else MAX_SONG, ext in songExtensions || ext in covers || path.substringAfterLast('/') == "collection.index") }
            } ?: error("Provider could not open $path")
        }
        suspend fun walk(folder: DocumentFile, prefix: String, depth: Int) {
            require(depth <= 16) { "Folder nesting exceeds 16 levels. Choose a deeper folder." }; coroutineContext.ensureActive()
            val children = folder.listFiles(); require(children.size <= MAX_ENTRIES) { "Folder has more than 10,000 entries." }
            children.forEach { child ->
                coroutineContext.ensureActive(); val filename = child.name ?: return@forEach
                val path = if (prefix.isEmpty()) filename else "$prefix/$filename"
                try { ArtworkStore.safePath(path); if (child.isDirectory) walk(child, path, depth + 1) else ingest(child.uri, path) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { issues.add(ImportIssue(path, e.message ?: "Read failed")); if (expanded > MAX_EXPANDED || files > MAX_ENTRIES) throw e }
            }
        }
        try {
            if (tree != null) walk(DocumentFile.fromTreeUri(context, tree) ?: error("Cannot access selected folder."), "", 0)
            else for (uri in uris) {
                coroutineContext.ensureActive()
                val filename = try { name(uri) } catch (e: Exception) { issues.add(ImportIssue(uri.toString(), e.message ?: "Missing filename")); continue }
                try { ArtworkStore.safePath(filename); ingest(uri, filename) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { issues.add(ImportIssue(filename, e.message ?: "Read failed")); if (expanded > MAX_EXPANDED || files > MAX_ENTRIES) break }
            }
        } catch (e: CancellationException) { sessionDir.deleteRecursively(); throw e }
        catch (e: Exception) { issues.add(ImportIssue("Discovery", e.message ?: "Discovery stopped")) }
        try {
        val identityFiles = mutableSetOf<String>()
        raw.walkTopDown().filter { it.isFile && it.name == "collection.index" }.forEach { index ->
            try {
                val manifest = JSONObject(utf8(index))
                require(manifest.getString("format") == "chordroom-collection-index" && manifest.getInt("schemaVersion") == 1) { "Unsupported collection index." }
                val entries = manifest.getJSONArray("files"); require(entries.length() <= MAX_CANDIDATES) { "Collection has more than 2000 arrangements; split into smaller archives." }
                val validated = (0 until entries.length()).map { i ->
                    val path = ArtworkStore.safePath(entries.getString(i)); val target = File(index.parentFile, path).canonicalFile
                    require(target.path.startsWith(raw.canonicalPath + File.separator) && target.isFile && target.extension.lowercase() == "json") { "Missing/unsafe indexed document: $path" }
                    target.path
                }
                require(validated.distinct().size == validated.size) { "Collection index repeats document paths." }
                identityFiles.addAll(validated)
            } catch (e: Exception) { issues.add(ImportIssue(index.relativeTo(raw).invariantSeparatorsPath, e.message ?: "Invalid collection index")) }
        }
        val candidates = mutableListOf<ImportCandidate>(); val batchFingerprints = mutableSetOf<String>(); val batchIdentities = mutableSetOf<String>(); val batchSources = mutableSetOf<String>(); val batchUuids = mutableSetOf<String>(); var coverCount = 0; var parsedCount = 0
        raw.walkTopDown().filter { it.isFile }.forEach { file ->
            coroutineContext.ensureActive(); val relative = file.relativeTo(raw).invariantSeparatorsPath; val ext = file.extension.lowercase()
            if (ext in covers) { runCatching { artwork.validate(file) }.onSuccess { coverCount++ }.onFailure { issues.add(ImportIssue(relative, it.message ?: "Invalid artwork")) }; return@forEach }
            if (ext !in songExtensions) return@forEach
            try {
                val text = utf8(file)
                val documents = if (ext == "json") Canonical.decode(text) else listOf(if (ext == "txt") SheetParser.plainText(text, file.name) else SheetParser.parse(text, file.name))
                for (parsed in documents) for (arrangement in parsed.document.arrangements) {
                    require(candidates.size < MAX_CANDIDATES) { "Pack exceeds 2000 arrangements. Split the collection." }
                    val doc = parsed.document.single(arrangement); val warnings = parsed.warnings.toMutableList()
                    val (art, preview) = artwork.preview(raw, relative, doc, sessionDir, warnings); val fingerprint = Canonical.fingerprint(doc)
                    val catalog = dao.catalog(doc.songId)
                    val songCollision = catalog != null && (Canonical.normalized(catalog.title) != Canonical.normalized(doc.title) || Canonical.normalized(catalog.artist) != Canonical.normalized(doc.artist))
                    val preserveIdentity = file.canonicalPath in identityFiles
                    val duplicates = dao.potentialDuplicates(fingerprint, arrangement.sourceId, ChordDao.identity(doc), arrangement.id).filter { !preserveIdentity || it.arrangementUuid == arrangement.id } + if (songCollision) dao.duplicateSummaries(doc.songId) else emptyList()
                    val exact = duplicates.firstOrNull { it.fingerprint == fingerprint }
                    val duplicate = exact ?: duplicates.firstOrNull { it.arrangementUuid == arrangement.id } ?: duplicates.firstOrNull { it.sourceId != null && it.sourceId == arrangement.sourceId } ?: duplicates.firstOrNull()
                    val reason = when { duplicate == null -> null; exact != null -> "Identical content already in your library"; duplicate.arrangementUuid == arrangement.id -> "Same arrangement UUID; content differs"; duplicate.sourceId != null && duplicate.sourceId == arrangement.sourceId -> "Same source ID; verify metadata"; else -> "Similar title + artist + arrangement; verify before replacing" }
                    if (duplicate != null && duplicate.normalizedIdentity != ChordDao.identity(doc)) warnings.add("Identity collision: source/UUID matches different metadata. Skip by default; review the target carefully.")
                    val batchExact = !batchFingerprints.add(fingerprint)
                    val batchIdentity = !batchIdentities.add(ChordDao.identity(doc))
                    val batchSource = arrangement.sourceId?.let { !batchSources.add(it) } ?: false
                    val batchUuid = !batchUuids.add(arrangement.id)
                    val batchDuplicate = batchUuid || !preserveIdentity && (batchExact || batchIdentity || batchSource)
                    if (batchDuplicate) warnings.add("Potential duplicate also appears earlier in this pack. Skipped by default; use New version after review if intentional.")
                    val id = Canonical.uuid(); val draft = File(sessionDir, "drafts/$id.json").apply { parentFile?.mkdirs(); writeText(Canonical.encode(doc)) }
                    parsed.rawText?.let { File(sessionDir, "drafts/$id.original.txt").writeText(it) }
                    candidates.add(ImportCandidate(id, relative, draft.path, doc.title, doc.artist, arrangement.name, arrangement.originalKey, preview, art?.path, warnings.distinct(), parsed.needsReview,
                        duplicateId = duplicate?.id, duplicateReason = reason ?: if (batchDuplicate) "Potential duplicate within this pack" else null, choice = if (duplicate != null || parsed.needsReview || batchDuplicate) ImportChoice.SKIP else ImportChoice.IMPORT, songUuid = doc.songId, preserveIdentity = preserveIdentity))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { issues.add(ImportIssue(relative, if (e is java.nio.charset.CharacterCodingException) "Malformed UTF-8. Save as UTF-8 and retry." else e.message ?: "Parse failed")) }
            progress(ImportProgress("Validating songs", ++parsedCount, path = relative))
        }
        ImportSession(sessionDir.name, sessionDir.path, files, coverCount, candidates, issues).also { save(it) }
        } catch (e: CancellationException) { sessionDir.deleteRecursively(); throw e }
        catch (e: Exception) { sessionDir.deleteRecursively(); throw e }
    }

    suspend fun review(session: ImportSession, id: String, editorText: String?): ImportSession = withContext(Dispatchers.IO) {
        val c = session.candidates.first { it.id == id }
        if (editorText != null) { val previous = Canonical.decode(File(c.draft).readText()).single().document; File(c.draft).writeText(Canonical.encode(SheetParser.parse(editorText, "review.cho", previous).document)) }
        session.copy(candidates = session.candidates.map { if (it.id == id) it.copy(reviewed = true, choice = if (it.needsReview && it.duplicateReason == null) ImportChoice.IMPORT else it.choice) else it }).also { save(it) }
    }
    suspend fun commit(start: ImportSession, progress: (ImportProgress) -> Unit = {}): ImportSession = withContext(Dispatchers.IO) {
        var session = start.copy(finished = false); val issues = start.issues.toMutableList()
        try {
            for ((index, c) in start.candidates.withIndex()) {
                coroutineContext.ensureActive(); if (c.committed) continue
                progress(ImportProgress("Importing", index, start.candidates.size, c.title))
                if (c.choice == ImportChoice.SKIP || c.needsReview && !c.reviewed) {
                    session = session.copy(skips = session.skips + 1, candidates = session.candidates.map { if (it.id == c.id) it.copy(committed = true) else it }); save(session); continue
                }
                try {
                    var doc = Canonical.decode(File(c.draft).readText()).single().document; val a = doc.arrangements.single()
                    val now = dao.potentialDuplicates(Canonical.fingerprint(doc), a.sourceId, ChordDao.identity(doc), a.id)
                    if (c.choice == ImportChoice.IMPORT && now.any { it.fingerprint == Canonical.fingerprint(doc) && (!c.preserveIdentity || it.arrangementUuid == a.id) }) session = session.copy(skips = session.skips + 1)
                    else {
                        var replace: Long? = null
                        if (c.choice == ImportChoice.REPLACE) { replace = c.duplicateId ?: error("No target selected."); require(dao.arrangement(replace) != null) { "Target removed. Review duplicates again." } }
                        if (c.choice == ImportChoice.NEW_VERSION) {
                            val target = c.duplicateId?.let { targetId -> now.firstOrNull { it.id == targetId } } ?: now.firstOrNull { it.normalizedIdentity.substringBeforeLast('\u001F') == ChordDao.identity(doc).substringBeforeLast('\u001F') }
                            val same = target != null && target.normalizedIdentity.substringBeforeLast('\u001F') == ChordDao.identity(doc).substringBeforeLast('\u001F')
                            doc = doc.copy(songId = if (same) target!!.songUuid else Canonical.stable("${start.id}/${c.id}/song"), arrangements = listOf(a.copy(id = Canonical.stable("${start.id}/${c.id}/version"))))
                        } else if (c.choice == ImportChoice.IMPORT && now.any { it.arrangementUuid == a.id }) error("UUID exists with different content. Choose Skip, Replace or New version.")
                        val managed = artwork.commit(c.artworkSource?.let(::File), doc.songId)
                        doc = doc.copy(artwork = ArtworkRef(managed.path, doc.artwork?.sourceUrl, doc.artwork?.credit)); coroutineContext.ensureActive()
                        val finalArrangement = doc.arrangements.single()
                        val alreadyCommitted = dao.potentialDuplicates(Canonical.fingerprint(doc), finalArrangement.sourceId, ChordDao.identity(doc), finalArrangement.id).firstOrNull { it.arrangementUuid == finalArrangement.id && it.fingerprint == Canonical.fingerprint(doc) }
                        // A process death after COMMIT but before the journal write must be idempotent, including New version.
                        if (replace == null && alreadyCommitted != null) session = session.copy(skips = session.skips + 1)
                        else { dao.store(doc, replace); session = session.copy(successes = session.successes + 1) }
                    }
                    session = session.copy(candidates = session.candidates.map { if (it.id == c.id) it.copy(committed = true) else it })
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { issues.add(ImportIssue(c.source, "Import failed: ${e.message}")); session = session.copy(candidates = session.candidates.map { if (it.id == c.id) it.copy(committed = true) else it }) }
                session = session.copy(issues = issues.toList()); save(session)
            }
            session = session.copy(finished = true, issues = issues.toList()); save(session); artwork.cleanUnreferenced(dao.artworkUsage())
            progress(ImportProgress("Finished", start.candidates.size, start.candidates.size)); session
        } catch (e: CancellationException) { withContext(NonCancellable) { save(session.copy(issues = issues.toList())) }; throw e }
    }
    fun save(session: ImportSession) {
        val json = JSONObject().put("id", session.id).put("directory", session.directory).put("files", session.files).put("covers", session.covers).put("finished", session.finished).put("successes", session.successes).put("skips", session.skips)
            .put("issues", JSONArray(session.issues.map { JSONObject().put("path", it.path).put("message", it.message) }))
            .put("candidates", JSONArray(session.candidates.map { c -> JSONObject().put("id", c.id).put("source", c.source).put("draft", c.draft).put("title", c.title).put("artist", c.artist).put("arrangement", c.arrangement).put("key", c.originalKey).put("preview", c.preview).put("artworkSource", c.artworkSource).put("warnings", JSONArray(c.warnings)).put("needsReview", c.needsReview).put("reviewed", c.reviewed).put("duplicateId", c.duplicateId).put("duplicateReason", c.duplicateReason).put("choice", c.choice.name).put("committed", c.committed).put("songUuid", c.songUuid).put("preserveIdentity", c.preserveIdentity) }))
        val file = AtomicFile(File(session.directory, "session.json")); val output = file.startWrite()
        try { output.write(json.toString().toByteArray()); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
    fun recover(): ImportSession? {
        val latest = sessions.listFiles()?.filter { File(it, "session.json").exists() }?.maxByOrNull { File(it, "session.json").lastModified() } ?: return null
        return runCatching {
            val j = JSONObject(AtomicFile(File(latest, "session.json")).readFully().toString(Charsets.UTF_8))
            ImportSession(j.getString("id"), latest.path, j.getInt("files"), j.getInt("covers"), j.getJSONArray("candidates").objects().map { c -> ImportCandidate(c.getString("id"), c.getString("source"), c.getString("draft"), c.getString("title"), c.getString("artist"), c.getString("arrangement"), c.string("key"), c.getString("preview"), c.string("artworkSource"), c.getJSONArray("warnings").let { w -> (0 until w.length()).map { w.getString(it) } }, c.getBoolean("needsReview"), c.getBoolean("reviewed"), if (c.isNull("duplicateId") || !c.has("duplicateId")) null else c.getLong("duplicateId"), c.string("duplicateReason"), ImportChoice.valueOf(c.getString("choice")), c.getBoolean("committed"), c.string("songUuid") ?: "", c.optBoolean("preserveIdentity", false)) }, j.getJSONArray("issues").objects().map { ImportIssue(it.getString("path"), it.getString("message")) }, j.getBoolean("finished"), j.getInt("successes"), j.getInt("skips"))
        }.getOrNull()
    }
    fun discard(session: ImportSession) { val dir = File(session.directory).canonicalFile; require(dir.parentFile == sessions.canonicalFile); dir.deleteRecursively() }
}
