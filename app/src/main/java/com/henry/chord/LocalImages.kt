package com.henry.chord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) { override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount }
    fun read(file: File): Bitmap? {
        if (!file.isFile) return null
        val key = "${file.path}:${file.lastModified()}:${file.length()}"
        synchronized(cache) { cache.get(key) }?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192 || bounds.outWidth.toLong() * bounds.outHeight > 32_000_000) return null
        val options = BitmapFactory.Options().apply { var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2; inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        synchronized(cache) { cache.put(key, bitmap) }
        return bitmap
    }
}

@Composable
fun SongArtwork(song: Song, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val path by produceState<String?>(null, song.artworkPath, song.songUuid) {
        val result = withContext(Dispatchers.IO) { runCatching {
            val relative = song.artworkPath ?: return@runCatching null
            val source = File(context.filesDir, ArtworkStore.safePath(relative))
            val store = ArtworkStore(context)
            val thumbnail = store.thumbnailPath(relative, song.songUuid)
            if (ThumbnailCache.read(thumbnail) == null) { store.validate(source); store.thumbnail(source, thumbnail) }
            thumbnail.path
        }.getOrNull() }
        withContext(Dispatchers.Main.immediate) { value = result }
    }
    LocalArtwork(path, song.songUuid.ifBlank { song.title }, "Artwork for ${song.title}", modifier)
}

@Composable
fun LocalArtwork(path: String?, identity: String, description: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, path, identity) {
        val result = withContext(Dispatchers.IO) { path?.let { runCatching { ThumbnailCache.read(File(it)) }.getOrNull() } }
        withContext(Dispatchers.Main.immediate) { value = result }
    }
    Box(modifier.clip(RoundedCornerShape(10.dp)).semantics { contentDescription = description; stateDescription = if (bitmap == null) "Generated artwork" else "Local artwork" }) {
        val image = bitmap
        if (image != null) Image(image.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else {
            val hue = remember(identity) { Canonical.hash(identity).take(6).toInt(16) % 360f }
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.linearGradient(listOf(Color.hsv(hue, .35f, .55f), Color.hsv((hue + 40) % 360, .5f, .3f))))
                val unit = size.minDimension
                drawCircle(Color.White.copy(alpha = .09f), unit * .55f, Offset(size.width * .1f, size.height * .95f))
                val ink = Color(0xFFF5F1E5)
                drawLine(ink, Offset(unit * .58f, unit * .27f), Offset(unit * .58f, unit * .65f), unit * .045f)
                drawLine(ink, Offset(unit * .58f, unit * .27f), Offset(unit * .77f, unit * .34f), unit * .045f)
                drawOval(ink, Offset(unit * .37f, unit * .59f), androidx.compose.ui.geometry.Size(unit * .23f, unit * .14f))
            }
        }
    }
}
