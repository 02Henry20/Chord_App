package com.henry.chord

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReaderHeader(song: Song, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        SongArtwork(song, Modifier.size(if (compact) 40.dp else 56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontFamily = Serif, fontSize = if (compact) 21.sp else 26.sp, lineHeight = 30.sp, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist.ifBlank { "Unknown artist" }, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!compact) Text(listOfNotNull(song.album.takeIf { it.isNotBlank() }, song.year?.toString(), song.originalKeyLabel().takeIf { song.key.isNotBlank() }?.let { "Original $it" }, song.tempoBpm?.let { "${it.toInt()} BPM" }).joinToString(" · "), fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongLinksDialog(song: Song, links: MusicLinks, dismiss: () -> Unit, open: (String) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Song links", fontFamily = Serif) }, text = { Column {
        Text("Links supplied with this song open directly. Search buttons look for the title and artist; they are not verified track matches.", fontSize = 13.sp, lineHeight = 20.sp)
        LinkService.entries.forEach { service ->
            val raw = if (service == LinkService.SPOTIFY) links.spotify else links.youtube
            val valid = raw?.let { SongLinks.valid(it, service) }
            val name = if (service == LinkService.SPOTIFY) "Spotify" else "YouTube"
            Button({ open(valid ?: SongLinks.search(song.title, song.artist, service)) }, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(if (valid != null) "Open $name ${if (service == LinkService.SPOTIFY) "track" else "video"}" else "Search $name") }
            if (raw != null && valid == null) Text("The stored $name link is invalid. Edit the song to correct it.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    } }, confirmButton = { TextButton(dismiss) { Text("Done") } })
}
