package com.henry.chord

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import java.io.File

/** SavedState carries a tiny cache key, never a multi-megabyte sheet in Android's Binder bundle. */
object EditorDrafts {
    fun saver(context: Context): Saver<String,String> = Saver(
        save={ value -> runCatching { save(context,value) }.getOrNull() },
        restore={ name -> restore(context,name) }
    )
    fun save(context: Context,value: String): String {
        val dir=File(context.cacheDir,"editor-state").apply { mkdirs() }
        val name="${Canonical.uuid()}.txt"
        File(dir,name).writeText(value,Charsets.UTF_8)
        // Cache entries are only state snapshots; saved songs always remain in Room.
        dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>7L*24*60*60*1000 }?.forEach { it.delete() }
        return name
    }
    fun restore(context: Context,name: String): String? {
        if (!name.matches(Regex("[0-9a-f-]{36}\\.txt"))) return null
        return runCatching { File(File(context.cacheDir,"editor-state"),name).takeIf { it.isFile && it.length()<=16L*1024*1024 }?.readText(Charsets.UTF_8) }.getOrNull()
    }
}
