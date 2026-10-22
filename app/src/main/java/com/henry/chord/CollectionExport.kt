package com.henry.chord

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/** Portable content archive. Personal states remain separate and are reserved for the backup phase. */
class CollectionExport(private val context: Context, private val dao: ChordDao) {
    suspend fun write(uri: Uri) = withContext(Dispatchers.IO) {
        val temporary = File(context.cacheDir, "export-${Canonical.uuid()}.zip")
        try {
            val assets = linkedSetOf<String>(); val files = mutableListOf<String>()
            ZipOutputStream(temporary.outputStream().buffered()).use { zip ->
                for (id in dao.arrangementIds()) {
                    coroutineContext.ensureActive()
                    val record = dao.arrangement(id) ?: error("Collection changed during export; retry when editing is finished.")
                    val document = Canonical.decode(record.documentJson).single().document
                    val path = "songs/${record.songUuid}/${record.arrangementUuid}.json"
                    val art = record.artworkPath?.let { ArtworkStore.safePath(it); assets.add(it); document.artwork?.copy(path = "assets/${File(it).name}") }
                    val portable = document.copy(artwork = art ?: document.artwork?.copy(path = null))
                    zip.putNextEntry(ZipEntry(path)); zip.write(Canonical.encode(portable).toByteArray(Charsets.UTF_8)); zip.closeEntry(); files.add(path)
                }
                for (asset in assets) {
                    coroutineContext.ensureActive(); val file = File(context.filesDir, asset)
                    require(file.isFile) { "Managed artwork is missing: $asset" }
                    zip.putNextEntry(ZipEntry("assets/${file.name}")); file.inputStream().use { it.copyTo(zip, 32 * 1024) }; zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("collection.index"))
                zip.write(JSONObject().put("format", "chordroom-collection-index").put("schemaVersion", 1).put("files", JSONArray(files)).put("includesPersonalState", false).toString(2).toByteArray())
                zip.closeEntry()
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> temporary.inputStream().use { input -> val b = ByteArray(32 * 1024); while (true) { coroutineContext.ensureActive(); val count = input.read(b); if (count < 0) break; output.write(b, 0, count) } } } ?: error("Cannot open archive destination.")
        } finally { temporary.delete() }
    }
}
