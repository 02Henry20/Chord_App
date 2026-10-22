package com.henry.chord

import android.content.Context
import android.net.Uri

/** Legacy test adapter. Interactive imports must go through the staged review screen. */
class ImportManager(context: Context, dao: ChordDao) {
    private val manager = BulkImportManager(context, dao)
    suspend fun files(uris: List<Uri>): String { val result = manager.commit(manager.discover(uris)); return "${result.successes} songs imported · ${result.skips} skipped" }
    suspend fun folder(tree: Uri): String { val result = manager.commit(manager.discover(tree = tree)); return "${result.successes} songs imported · ${result.skips} skipped" }
}
