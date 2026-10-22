package com.henry.chord

import android.content.Context
import android.graphics.*
import java.io.File
import java.security.MessageDigest

data class ManagedArtwork(val path: String?, val thumbnail: String)

class ArtworkStore(private val context: Context) {
    companion object {
        const val MAX_IMAGE_BYTES = 12L * 1024 * 1024
        fun safePath(value: String): String {
            require(value.isNotBlank() && value.length <= 1024 && !value.startsWith('/') && !value.contains('\\') && !value.contains(':')) { "Unsafe absolute/platform path: $value" }
            val parts = value.removeSuffix("/").split('/')
            require(parts.size <= 20 && parts.all { p -> p.isNotEmpty() && p != "." && p != ".." && p.length <= 240 && p.none { it.code < 32 || it in "<>\"|?*" } && p.substringBefore('.').uppercase() !in setOf("CON", "PRN", "AUX", "NUL", "COM1", "LPT1") }) { "Unsafe path: $value" }
            return parts.joinToString("/")
        }
        fun resolve(root: File, manifest: String, reference: String): File? {
            val safe = safePath(reference); val parent = manifest.substringBeforeLast('/', "")
            val archiveRoot = manifest.substringBefore('/', "")
            return listOf(if (parent.isEmpty()) safe else "$parent/$safe", if (archiveRoot.isEmpty()) safe else "$archiveRoot/$safe", safe).distinct().map { path -> File(root, path).canonicalFile.also { require(it.path.startsWith(root.canonicalPath + File.separator)) { "Artwork escapes import root." } } }.firstOrNull { it.isFile }
        }
    }
    fun validate(file: File): String {
        require(file.length() in 1..MAX_IMAGE_BYTES) { "Artwork must be 1 byte–12 MB." }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, o)
        require(o.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "Artwork content must be JPEG, PNG or WebP." }
        require(o.outWidth in 1..8192 && o.outHeight in 1..8192 && o.outWidth.toLong() * o.outHeight <= 32_000_000L) { "Artwork exceeds 8192px or 32 megapixels." }
        return when (o.outMimeType) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
    }
    fun thumbnail(source: File, destination: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(source.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = (minOf(bounds.outWidth, bounds.outHeight) / 512).coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeFile(source.path, options) ?: error("Artwork cannot be decoded.")
        val thumb = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); val canvas = Canvas(thumb); canvas.drawColor(Color.rgb(244, 241, 229))
        val side = minOf(decoded.width, decoded.height); val x = (decoded.width - side) / 2; val y = (decoded.height - side) / 2
        canvas.drawBitmap(decoded, Rect(x, y, x + side, y + side), Rect(0, 0, 256, 256), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        destination.parentFile?.mkdirs(); destination.outputStream().use { require(thumb.compress(Bitmap.CompressFormat.JPEG, 88, it)) }; decoded.recycle(); thumb.recycle()
    }
    fun placeholder(songId: String, destination: File) {
        if (destination.isFile) return
        val hue = Canonical.hash(songId).take(6).toInt(16) % 360f
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 256f, 256f, Color.HSVToColor(floatArrayOf(hue, .38f, .46f)), Color.HSVToColor(floatArrayOf((hue + 35) % 360, .5f, .22f)), Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, 256f, 256f, paint); paint.shader = null; paint.color = Color.argb(55, 255, 255, 235)
        repeat(4) { i -> canvas.drawCircle(40f + i * 55, 200f - i * 35, 56f, paint) }
        paint.color = Color.rgb(246, 243, 230); paint.strokeWidth = 9f
        canvas.drawLine(145f, 68f, 145f, 160f, paint); canvas.drawLine(145f, 68f, 185f, 80f, paint); canvas.drawOval(102f, 143f, 147f, 175f, paint)
        destination.parentFile?.mkdirs(); destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
    }
    fun preview(root: File, manifest: String, doc: SongDocument, session: File, warnings: MutableList<String>): Pair<File?, String> {
        val source = doc.artwork?.path?.let { runCatching { resolve(root, manifest, it) ?: error("Missing artwork: $it") }.getOrElse { e -> warnings.add(e.message ?: "Artwork missing"); null } }
        val thumb = File(session, "previews/${doc.songId}-${Canonical.hash(manifest).take(12)}.jpg")
        if (source != null) runCatching { validate(source); thumbnail(source, thumb) }.onSuccess { return source to thumb.path }.onFailure { warnings.add("Artwork rejected: ${it.message}") }
        placeholder(doc.songId, thumb); return null to thumb.path
    }
    fun commit(source: File?, songId: String): ManagedArtwork {
        if (source == null) { val thumb = "thumbnails/placeholder-$songId.jpg"; placeholder(songId, File(context.filesDir, thumb)); return ManagedArtwork(null, thumb) }
        val ext = validate(source); val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input -> val b = ByteArray(8192); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }; val path = "artwork/$hash.$ext"; val target = File(context.filesDir, path); target.parentFile?.mkdirs()
        if (!target.exists()) { val temp = File(target.parentFile, "$hash.partial"); source.copyTo(temp, overwrite = true); require(temp.renameTo(target)) { "Could not commit artwork." } }
        val thumbnail = "thumbnails/$hash.jpg"; val thumb = File(context.filesDir, thumbnail); if (!thumb.exists()) thumbnail(target, thumb)
        return ManagedArtwork(path, thumbnail)
    }
    fun thumbnailPath(artworkPath: String?, songId: String) = File(context.filesDir, if (artworkPath == null) "thumbnails/placeholder-$songId.jpg" else "thumbnails/${File(artworkPath).nameWithoutExtension}.jpg")
    fun cleanUnreferenced(records: List<ArtworkUsage>) {
        val paths = records.mapNotNull { it.artworkPath }.toSet()
        File(context.filesDir, "artwork").listFiles()?.filter { "artwork/${it.name}" !in paths }?.forEach { it.delete() }
        val thumbnails = records.map { thumbnailPath(it.artworkPath, it.songUuid).name }.toSet()
        File(context.filesDir, "thumbnails").listFiles()?.filter { it.name !in thumbnails }?.forEach { it.delete() }
    }
}
