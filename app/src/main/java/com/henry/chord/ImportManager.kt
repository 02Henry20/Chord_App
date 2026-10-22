package com.henry.chord

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ImportManager(private val context: Context, private val dao: ChordDao) {
    private val extensions = setOf("cho", "chopro", "chordpro", "pro", "txt")
    private fun name(uri: Uri): String = if (uri.scheme == "file") uri.lastPathSegment ?: "Imported song.txt" else context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Imported song.txt"
    private fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= SongImport.MAX_BYTES) { "A song exceeds the 2 MB limit." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }
    suspend fun files(uris: List<Uri>): String = withContext(Dispatchers.IO) {
        var added = 0; var skipped = 0; var total = 0
        val candidates = mutableListOf<Song>()
        fun accept(text: String, filename: String) {
            total += text.toByteArray(Charsets.UTF_8).size
            require(total <= 16 * 1024 * 1024) { "Import is limited to 16 MB at a time." }
            require(candidates.size < 100) { "Import is limited to 100 songs at a time." }
            candidates.add(SongImport.parse(text, filename))
        }
        for (uri in uris.take(100)) {
            val filename = name(uri)
            context.contentResolver.openInputStream(uri)?.use { input ->
                if (filename.endsWith(".zip", true)) {
                    ZipInputStream(input).use { zip ->
                        var entry = zip.nextEntry
                        var count = 0
                        while (entry != null) {
                            require(++count <= 500) { "ZIP contains too many entries." }
                            if (!entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in extensions) accept(read(zip), entry.name.substringAfterLast('/'))
                            else if (!entry.isDirectory) skipped++
                            zip.closeEntry(); entry = zip.nextEntry
                        }
                    }
                } else if (filename.substringAfterLast('.').lowercase() in extensions) accept(read(input), filename) else skipped++
            } ?: error("Could not read $filename")
        }
        for (song in candidates) {
            if (dao.duplicateCount(song.title, song.artist, song.body) == 0) { dao.insert(song); added++ } else skipped++
        }
        "$added song${if (added == 1) "" else "s"} imported · $skipped skipped"
    }
    suspend fun folder(tree: Uri): String = withContext(Dispatchers.IO) {
        val files = mutableListOf<Uri>()
        fun walk(id: String, depth: Int) {
            require(depth <= 8) { "Folder nesting exceeds 8 levels." }
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val child = cursor.getString(0)
                    if (cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) walk(child, depth + 1)
                    else if (cursor.getString(2).substringAfterLast('.').lowercase() in extensions) {
                        require(files.size < 100) { "Select a folder with 100 songs or fewer." }
                        files.add(DocumentsContract.buildDocumentUriUsingTree(tree, child))
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        files(files)
    }
}
