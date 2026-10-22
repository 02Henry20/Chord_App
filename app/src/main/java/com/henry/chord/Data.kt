package com.henry.chord

import android.content.Context
import androidx.room.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String, val artist: String = "Original", val key: String = "C",
    val body: String, val favorite: Boolean = false, val notes: String = "",
    val transpose: Int = 0, val capo: Int = 0, val lastOpened: Long = 0,
    val source: String = ""
)

@Fts4(contentEntity = Song::class)
@Entity(tableName = "song_search")
data class SongSearch(val title: String, val artist: String, val body: String)

@Entity(tableName = "setlists")
data class Setlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "setlist_entries", primaryKeys = ["setlistId", "songId"], foreignKeys = [
    ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Setlist::class, parentColumns = ["id"], childColumns = ["setlistId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("songId")])
data class SetlistEntry(val setlistId: Long, val songId: Long, val position: Int)

@Dao
interface ChordDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE") fun songs(): Flow<List<Song>>
    @Query("SELECT songs.* FROM songs JOIN song_search ON songs.id = song_search.rowid WHERE song_search MATCH :query ORDER BY title COLLATE NOCASE")
    fun search(query: String): Flow<List<Song>>
    @Query("SELECT * FROM setlists ORDER BY id") fun setlists(): Flow<List<Setlist>>
    @Query("SELECT * FROM setlist_entries ORDER BY position") fun entries(): Flow<List<SetlistEntry>>
    @Insert suspend fun insert(song: Song): Long
    @Update suspend fun update(song: Song)
    @Delete suspend fun delete(song: Song)
    @Insert suspend fun insertSetlist(setlist: Setlist): Long
    @Update suspend fun updateSetlist(setlist: Setlist)
    @Delete suspend fun deleteSetlist(setlist: Setlist)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addEntry(entry: SetlistEntry)
    @Query("DELETE FROM setlist_entries WHERE setlistId = :id AND songId = :songId") suspend fun removeEntry(id: Long, songId: Long)
    @Update suspend fun updateEntries(entries: List<SetlistEntry>)
    @Query("SELECT COUNT(*) FROM songs") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM songs WHERE title = :title AND artist = :artist AND body = :body") suspend fun duplicateCount(title: String, artist: String, body: String): Int
    @Transaction suspend fun reorder(entries: List<SetlistEntry>) = updateEntries(entries.mapIndexed { i, e -> e.copy(position = i) })
}

@Database(entities = [Song::class, SongSearch::class, Setlist::class, SetlistEntry::class], version = 1, exportSchema = true)
abstract class ChordDatabase : RoomDatabase() { abstract fun dao(): ChordDao }

val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val dark: Boolean = false, val instrument: String = "Guitar", val fontSize: Int = 19, val keepAwake: Boolean = true, val roman: Boolean = false)
class Settings(private val context: Context) {
    val flow = context.preferences.data.map { Preferences(it[booleanPreferencesKey("dark")] ?: false, it[stringPreferencesKey("instrument")] ?: "Guitar", it[intPreferencesKey("fontSize")] ?: 19, it[booleanPreferencesKey("keepAwake")] ?: true, it[booleanPreferencesKey("roman")] ?: false) }
    suspend fun save(p: Preferences) { context.preferences.edit { it[booleanPreferencesKey("dark")] = p.dark; it[stringPreferencesKey("instrument")] = p.instrument; it[intPreferencesKey("fontSize")] = p.fontSize; it[booleanPreferencesKey("keepAwake")] = p.keepAwake; it[booleanPreferencesKey("roman")] = p.roman } }
}

val demoSongs = listOf(
    Song(title = "A little closer", artist = "Chord Originals", key = "C", favorite = true, body = "{comment:Verse 1}\n[C]Morning gathers [G]at the door\n[Am]Light spills gently [F]on the floor\n[C]Every mile and [G]every turn\n[F]Brings me somewhere [G]I can learn\n\n{comment:Chorus}\n[F]Stay a little [C]closer\n[Am]Let the quiet [G]in\n[F]Every road leads [C]homeward\n[G]Let us start [C]again\n\n{comment:Verse 2}\n[C]Fold the map and [G]take my hand\n[Am]There is time to [F]understand\n[C]All the words we [G]could not say\n[F]Find their place in [G]us today\n\n{comment:Chorus}\n[F]Stay a little [C]closer\n[Am]Let the quiet [G]in\n[F]Every road leads [C]homeward\n[G]Let us start [C]again\n\n{comment:Outro}\n[F]Every road leads [C]homeward\n[G]Let us start [C]again"),
    Song(title = "Blue hour", artist = "Chord Originals", key = "G", body = "{comment:Verse}\n[G]Windows glow along the [D]street\n[Em]Evening moves beneath our [C]feet\n[G]Leave a little room for [D]wonder\n[C]Let the day fall [G]under\n\n{comment:Chorus}\n[C]Blue hour, [G]slow down\n[D]Keep this light [Em]around\n[C]One more song, [G]one more sound\n[D]Before the sun goes [G]down"),
    Song(title = "Paper boats", artist = "Chord Originals", key = "D", favorite = true, body = "{comment:Verse}\n[D]Paper boats on [A]silver streams\n[Bm]Carry all our [G]little dreams\n[D]Past the bridge and [A]out of view\n[G]I will follow [D]you\n\n{comment:Chorus}\n[G]Let them sail, [D]let them go\n[A]Where the quiet [Bm]waters flow\n[G]Every bend a [D]different blue\n[A]I will follow [D]you"),
    Song(title = "Sunday kind of slow", artist = "Chord Originals", key = "A", body = "{comment:Verse}\n[A]Coffee cooling [E]by the window\n[F#m]Nothing on the [D]radio\n[A]We have nowhere [E]else to go\n[D]Sunday kind of [A]slow\n\n{comment:Chorus}\n[D]Take your time and [A]stay\n[E]Let the hours drift [F#m]away\n[D]There is all we [A]need to know\n[E]Sunday kind of [A]slow"),
    Song(title = "The open road", artist = "Chord Originals", key = "E", body = "{comment:Verse}\n[E]Pack the car at [B]break of day\n[C#m]Watch the rooftops [A]fade away\n[E]Every skyline [B]something new\n[A]Open road and [E]you\n\n{comment:Chorus}\n[A]Drive until the [E]stars arrive\n[B]Sing to keep the [C#m]night alive\n[A]Every mile a [E]different view\n[B]Open road and [E]you"),
    Song(title = "Wildflower", artist = "Chord Originals", key = "F", body = "{comment:Verse}\n[F]Between the stones a [C]flower grows\n[Dm]How it finds the [Bb]light, who knows\n[F]Quiet strength and [C]nothing more\n[Bb]Waiting by the [F]door\n\n{comment:Chorus}\n[Bb]Wildflower, [F]hold your ground\n[C]Let the rain come [Dm]falling down\n[Bb]There is beauty [F]to be found\n[C]Even in this [F]town")
)
