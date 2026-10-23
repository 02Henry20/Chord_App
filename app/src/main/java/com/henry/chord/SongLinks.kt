package com.henry.chord

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URLEncoder

enum class LinkService { SPOTIFY, YOUTUBE }
/** Allowlisted user-provided identities only. This validates URL shape, not official ownership. */
object SongLinks {
    fun valid(raw: String, service: LinkService): String? = runCatching {
        val text = raw.trim()
        require(text.isNotEmpty() && text.none { it.isWhitespace() || it.code < 32 })
        if (service == LinkService.SPOTIFY && Regex("spotify:track:[A-Za-z0-9]{22}").matches(text)) return@runCatching "https://open.spotify.com/track/${text.substringAfterLast(':')}"
        if (service == LinkService.YOUTUBE && Regex("vnd.youtube:[A-Za-z0-9_-]{11}").matches(text)) return@runCatching "https://www.youtube.com/watch?v=${text.substringAfter(':')}"
        val uri = URI(text)
        require(uri.scheme?.lowercase() in listOf("http", "https") && uri.userInfo == null && uri.port in listOf(-1, 80, 443))
        val host = uri.host?.lowercase() ?: error("Missing host")
        if (service == LinkService.SPOTIFY) {
            require(host in setOf("open.spotify.com", "www.open.spotify.com"))
            val match = Regex("/(?:intl-[a-z]{2}/)?track/([A-Za-z0-9]{22})/?").matchEntire(uri.path) ?: error("Not a track URL")
            "https://open.spotify.com/track/${match.groupValues[1]}"
        } else {
            val id = when {
                host in setOf("youtu.be", "www.youtu.be") -> uri.path.removePrefix("/").removeSuffix("/")
                host in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") -> {
                    if (uri.path == "/watch") uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }.singleOrNull { it[0] == "v" }?.getOrNull(1)
                    else Regex("/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})/?").matchEntire(uri.path)?.groupValues?.get(1)
                }
                else -> null
            }
            require(id != null && Regex("[A-Za-z0-9_-]{11}").matches(id))
            "https://www.youtube.com/watch?v=$id"
        }
    }.getOrNull()
    fun search(title: String, artist: String, service: LinkService): String {
        val query = URLEncoder.encode(listOf(artist, title).filter { it.isNotBlank() }.joinToString(" "), "UTF-8")
        return if (service == LinkService.SPOTIFY) "https://open.spotify.com/search/${query.replace("+", "%20")}" else "https://www.youtube.com/results?search_query=$query"
    }
    fun intent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    fun open(context: Context, url: String): Boolean = runCatching { context.startActivity(intent(url)); true }.getOrDefault(false)
}
