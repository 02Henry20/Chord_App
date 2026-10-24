package com.henry.chord

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Compatibility projection for the existing Compose screens; id is an arrangement's preserved row ID. */
data class Song(
    val id: Long = 0, val title: String, val artist: String = "Original", val key: String = "C", val body: String,
    val favorite: Boolean = false, val notes: String = "", val transpose: Int = 0, val capo: Int = 0,
    val lastOpened: Long = 0, val source: String = "", val songUuid: String = "", val arrangementUuid: String = "",
    val documentJson: String = "", val artworkPath: String? = null, val arrangementName: String = "Main", val album: String = "", val year: Int? = null, val tagsJson: String = "[]", val tonalMode: String? = null, val tempoBpm: Double? = null, val addedAt: Long = 0
)
@Entity(tableName = "catalog_songs")
data class CatalogSong(@PrimaryKey val songUuid: String, val title: String, val artist: String)

@Entity(tableName = "arrangements", foreignKeys = [ForeignKey(entity = CatalogSong::class, parentColumns = ["songUuid"], childColumns = ["songUuid"], onDelete = ForeignKey.CASCADE)], indices = [Index("songUuid"), Index(value = ["arrangementUuid"], unique = true), Index("fingerprint"), Index("sourceId"), Index("normalizedIdentity")])
data class ArrangementRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val arrangementUuid: String, val songUuid: String,
    val name: String, val originalKey: String?, val body: String, val source: String,
    val documentJson: String, val artworkPath: String?, val fingerprint: String, val sourceId: String?, val normalizedIdentity: String
)
data class DuplicateMatch(val id: Long, val arrangementUuid: String, val songUuid: String, val name: String, val fingerprint: String, val sourceId: String?, val normalizedIdentity: String)
data class ArtworkUsage(val artworkPath: String?, val songUuid: String)
@Entity(tableName = "song_user_state", foreignKeys = [ForeignKey(entity = CatalogSong::class, parentColumns = ["songUuid"], childColumns = ["songUuid"], onDelete = ForeignKey.CASCADE)])
data class SongUserState(@PrimaryKey val songUuid: String, val favorite: Boolean = false, val notes: String = "", val lastOpened: Long = 0)
@Entity(tableName = "arrangement_user_state", foreignKeys = [ForeignKey(entity = ArrangementRecord::class, parentColumns = ["id"], childColumns = ["arrangementId"], onDelete = ForeignKey.CASCADE)])
data class ArrangementUserState(@PrimaryKey val arrangementId: Long, val transpose: Int = 0, val capo: Int = 0)
@Fts4
@Entity(tableName = "arrangement_search")
data class ArrangementSearch(@PrimaryKey @ColumnInfo(name = "rowid") val id: Long, val title: String, val artist: String, val body: String)
@Entity(tableName = "setlists", indices=[Index(value=["uuid"],unique=true)])
data class Setlist(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, @ColumnInfo(defaultValue="''") val uuid: String = Canonical.uuid())
@Entity(tableName="app_metadata")
data class AppMetadata(@PrimaryKey val name: String,val value: String)
@Entity(tableName = "setlist_entries", foreignKeys = [
    ForeignKey(entity = ArrangementRecord::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = Setlist::class, parentColumns = ["id"], childColumns = ["setlistId"], onDelete = ForeignKey.CASCADE)
], indices = [Index("songId"), Index("setlistId")])
data class SetlistEntry(val setlistId: Long, val songId: Long, val position: Int, @PrimaryKey val entryId: String = Canonical.uuid(), val transposeOverride: Int? = null, val notes: String = "")

private const val SONG_SELECT = "SELECT a.id, c.title, c.artist, COALESCE(a.originalKey, '') AS `key`, a.body, u.favorite, u.notes, p.transpose, p.capo, u.lastOpened, a.source, a.songUuid, a.arrangementUuid, a.documentJson, a.artworkPath, a.name AS arrangementName, m.album, m.year, m.tagsJson, m.tonalMode, m.tempoBpm, m.addedAt FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid JOIN song_user_state u ON u.songUuid=a.songUuid JOIN arrangement_user_state p ON p.arrangementId=a.id JOIN library_index m ON m.id=a.id"
private const val SUMMARY_SELECT = "SELECT a.id, c.title, c.artist, COALESCE(a.originalKey, '') AS `key`, '' AS body, u.favorite, '' AS notes, p.transpose, p.capo, u.lastOpened, '' AS source, a.songUuid, a.arrangementUuid, '' AS documentJson, a.artworkPath, a.name AS arrangementName, m.album, m.year, m.tagsJson, m.tonalMode, m.tempoBpm, m.addedAt FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid JOIN song_user_state u ON u.songUuid=a.songUuid JOIN arrangement_user_state p ON p.arrangementId=a.id JOIN library_index m ON m.id=a.id"

@Dao
abstract class ChordDao {
    @RawQuery abstract suspend fun matchingLibraryIds(query: androidx.sqlite.db.SupportSQLiteQuery): List<Long>
    @RawQuery(observedEntities = [LibraryIndex::class, LibraryGrams::class, SongUserState::class, ArrangementUserState::class, CatalogSong::class, ArrangementRecord::class, LibraryTag::class]) abstract fun libraryResults(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<LibraryHit>>
    @RawQuery(observedEntities = [LibraryIndex::class, LibraryGrams::class, SongUserState::class, ArrangementUserState::class, CatalogSong::class, ArrangementRecord::class, LibraryTag::class]) abstract fun libraryResultCount(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<Int>
    @Query("SELECT text FROM library_excerpts WHERE arrangementId=:id AND instr(normalized,:term)>0 ORDER BY position LIMIT 1") abstract suspend fun excerpt(id: Long, term: String): String?
    @Query("SELECT addedAt FROM library_index WHERE id=:id") abstract suspend fun addedAt(id: Long): Long?
    @Upsert abstract suspend fun putLibrary(value: LibraryIndex)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putGrams(value: LibraryGrams)
    @Query("DELETE FROM library_grams WHERE rowid=:id") abstract suspend fun removeGrams(id: Long)
    @Query("DELETE FROM library_excerpts WHERE arrangementId=:id") abstract suspend fun removeExcerpts(id: Long)
    @Insert abstract suspend fun putExcerpts(values: List<LibraryExcerpt>)
    @Query("DELETE FROM library_tags WHERE arrangementId=:id") abstract suspend fun removeTags(id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun putTags(values: List<LibraryTag>)
    @Query("UPDATE song_user_state SET lastOpened=0") abstract suspend fun clearHistory()
    @Transaction open suspend fun indexLibrary(id: Long, doc: SongDocument) {
        val metadata = LibrarySearch.index(id, doc, addedAt(id) ?: System.currentTimeMillis())
        putLibrary(metadata); putGrams(LibrarySearch.grams(metadata))
        removeExcerpts(id); putExcerpts(LibrarySearch.excerpts(id, doc.arrangements.single().lyricText()))
        removeTags(id); putTags(doc.tags.distinct().map { LibraryTag(id,it) })
    }

    @Query("$SONG_SELECT ORDER BY c.title COLLATE NOCASE, a.id") abstract fun songs(): Flow<List<Song>>
    @Query("$SUMMARY_SELECT ORDER BY c.title COLLATE NOCASE, a.id") abstract fun librarySummaries(): Flow<List<Song>>
    @Query("$SUMMARY_SELECT JOIN arrangement_search ON a.id=arrangement_search.rowid WHERE arrangement_search MATCH :query ORDER BY c.title COLLATE NOCASE") abstract fun searchSummaries(query: String): Flow<List<Song>>
    @Query("$SONG_SELECT WHERE a.id=:id") abstract fun observeSong(id: Long): Flow<Song?>
    @Query("$SONG_SELECT JOIN arrangement_search ON a.id=arrangement_search.rowid WHERE arrangement_search MATCH :query ORDER BY c.title COLLATE NOCASE") abstract fun search(query: String): Flow<List<Song>>
    @Query("$SONG_SELECT WHERE a.id=:id") abstract suspend fun song(id: Long): Song?
    @Query("SELECT * FROM arrangements WHERE id=:id") abstract suspend fun arrangement(id: Long): ArrangementRecord?
    @Query("SELECT * FROM arrangements ORDER BY id") abstract suspend fun arrangements(): List<ArrangementRecord>
    @Query("SELECT * FROM catalog_songs WHERE songUuid=:id") abstract suspend fun catalog(id: String): CatalogSong?
    @Query("SELECT * FROM arrangements WHERE songUuid=:id") abstract suspend fun songArrangements(id: String): List<ArrangementRecord>
    @Query("SELECT id,arrangementUuid,songUuid,name,fingerprint,sourceId,normalizedIdentity FROM arrangements WHERE fingerprint=:fingerprint OR (:sourceId IS NOT NULL AND sourceId=:sourceId) OR normalizedIdentity=:identity OR arrangementUuid=:uuid ORDER BY id")
    abstract suspend fun potentialDuplicates(fingerprint: String, sourceId: String?, identity: String, uuid: String): List<DuplicateMatch>
    @Query("SELECT id,arrangementUuid,songUuid,name,fingerprint,sourceId,normalizedIdentity FROM arrangements WHERE songUuid=:id") abstract suspend fun duplicateSummaries(id: String): List<DuplicateMatch>
    @Query("SELECT id FROM arrangements ORDER BY id") abstract suspend fun arrangementIds(): List<Long>
    @Query("SELECT artworkPath,songUuid FROM arrangements") abstract suspend fun artworkUsage(): List<ArtworkUsage>
    @Query("UPDATE song_user_state SET lastOpened=:timestamp WHERE songUuid=(SELECT songUuid FROM arrangements WHERE id=:id)") abstract suspend fun markPlayed(id: Long, timestamp: Long = System.currentTimeMillis())
    @Query("UPDATE song_user_state SET favorite=:favorite WHERE songUuid=(SELECT songUuid FROM arrangements WHERE id=:id)") abstract suspend fun favorite(id: Long, favorite: Boolean)
    @Query("UPDATE song_user_state SET favorite=:favorite WHERE songUuid IN (SELECT songUuid FROM arrangements WHERE id IN (:ids))") protected abstract suspend fun favoriteBatch(ids: List<Long>, favorite: Boolean)
    @Transaction open suspend fun favoriteSelected(ids: List<Long>,favorite: Boolean) { ids.distinct().chunked(400).forEach { favoriteBatch(it,favorite) } }
    @Query("SELECT * FROM setlists ORDER BY id") abstract fun setlists(): Flow<List<Setlist>>
    @Query("SELECT * FROM setlist_entries ORDER BY position") abstract fun entries(): Flow<List<SetlistEntry>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addCatalog(value: CatalogSong): Long
    @Query("UPDATE catalog_songs SET title=:title, artist=:artist WHERE songUuid=:id") abstract suspend fun renameCatalog(id: String, title: String, artist: String)
    @Insert abstract suspend fun addArrangement(value: ArrangementRecord): Long
    @Update abstract suspend fun changeArrangement(value: ArrangementRecord)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addUserState(value: SongUserState)
    @Upsert abstract suspend fun saveUserState(value: SongUserState)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addOffsets(value: ArrangementUserState)
    @Upsert abstract suspend fun saveOffsets(value: ArrangementUserState)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun index(value: ArrangementSearch)
    @Query("DELETE FROM arrangement_search WHERE rowid=:id") abstract suspend fun removeIndex(id: Long)
    @Query("DELETE FROM arrangements WHERE id=:id") abstract suspend fun removeArrangement(id: Long)
    @Query("DELETE FROM catalog_songs WHERE songUuid=:id AND NOT EXISTS (SELECT 1 FROM arrangements WHERE songUuid=:id)") abstract suspend fun removeEmptyCatalog(id: String)
    @Insert abstract suspend fun insertSetlist(setlist: Setlist): Long
    @Update abstract suspend fun updateSetlist(setlist: Setlist)
    @Delete abstract suspend fun deleteSetlist(setlist: Setlist)
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun addEntry(entry: SetlistEntry)
    @Query("DELETE FROM setlist_entries WHERE setlistId=:id AND songId=:songId") abstract suspend fun removeEntry(id: Long, songId: Long)
    @Delete abstract suspend fun deleteEntry(entry: SetlistEntry)
    @Update abstract suspend fun updateEntry(entry: SetlistEntry)
    @Update abstract suspend fun updateEntries(entries: List<SetlistEntry>)
    @Query("SELECT COUNT(*) FROM arrangements") abstract suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM arrangements a JOIN catalog_songs c ON c.songUuid=a.songUuid WHERE c.title=:title AND c.artist=:artist AND a.body=:body") abstract suspend fun duplicateCount(title: String, artist: String, body: String): Int
    @Transaction open suspend fun reorder(entries: List<SetlistEntry>) = updateEntries(entries.mapIndexed { i, e -> e.copy(position = i) })
    @Transaction open suspend fun duplicateSetlist(set: Setlist): Long {
        val id = insertSetlist(Setlist(name=set.name + " (copy)"))
        snapshotEntries().filter { it.setlistId == set.id }.forEach { addEntry(it.copy(setlistId=id,entryId=Canonical.uuid())) }
        return id
    }
    @Query("SELECT * FROM setlist_entries ORDER BY setlistId,position") abstract suspend fun snapshotEntries(): List<SetlistEntry>
    @Query("SELECT * FROM setlists ORDER BY id") abstract suspend fun snapshotSets(): List<Setlist>
    @Query("SELECT * FROM song_user_state") abstract suspend fun snapshotUsers(): List<SongUserState>
    @Query("SELECT * FROM arrangement_user_state") abstract suspend fun snapshotOffsets(): List<ArrangementUserState>
    @Query("SELECT value FROM app_metadata WHERE name=:name") abstract suspend fun metadata(name: String): String?
    @Upsert abstract suspend fun saveMetadata(value: AppMetadata)
    @Query("DELETE FROM app_metadata WHERE name=:name") abstract suspend fun deleteMetadata(name: String)
    @Query("DELETE FROM setlists") abstract suspend fun clearSets()
    @Query("DELETE FROM arrangement_search") abstract suspend fun clearLegacySearch()
    @Query("DELETE FROM library_grams") abstract suspend fun clearGrams()
    @Query("DELETE FROM catalog_songs") abstract suspend fun clearCatalog()
    @Transaction open suspend fun clearLibrary() { clearSets(); clearLegacySearch(); clearGrams(); clearCatalog() }

    /** Replace retains the target's IDs, personal offsets, favorite, notes and every setlist entry. */
    @Transaction open suspend fun store(document: SongDocument, replaceId: Long? = null, preferredTranspose: Int = 0): Long {
        val old = replaceId?.let { arrangement(it) }
        val targetSong = old?.songUuid ?: document.songId
        val sourceArrangement = document.arrangements.single()
        val a = sourceArrangement.copy(id = old?.arrangementUuid ?: sourceArrangement.id)
        val doc = document.copy(songId = targetSong, arrangements = listOf(a))
        val catalog = catalog(targetSong)
        require(old != null || catalog == null || Canonical.normalized(catalog.title) == Canonical.normalized(doc.title) && Canonical.normalized(catalog.artist) == Canonical.normalized(doc.artist)) { "Song UUID belongs to different metadata. Choose Import as new version to keep it separate." }
        addCatalog(CatalogSong(targetSong, doc.title, doc.artist))
        if (old != null) {
            renameCatalog(targetSong, doc.title, doc.artist)
            // Catalog-level edits must also update portable documents and FTS for sibling versions.
            if (catalog?.title != doc.title || catalog.artist != doc.artist) for (sibling in songArrangements(targetSong).filter { it.id != old.id }) {
                val portable = Canonical.decode(sibling.documentJson).single().document.copy(title = doc.title, artist = doc.artist)
                changeArrangement(sibling.copy(documentJson = Canonical.encode(portable), fingerprint = Canonical.fingerprint(portable), normalizedIdentity = identity(portable)))
                index(ArrangementSearch(sibling.id, doc.title, doc.artist, portable.arrangements.single().lyricText()))
                indexLibrary(sibling.id, portable)
            }
        }
        addUserState(SongUserState(targetSong))
        val row = ArrangementRecord(id = old?.id ?: 0, arrangementUuid = a.id, songUuid = targetSong, name = a.name, originalKey = a.originalKey,
            body = a.editorText(), source = a.provenance.orEmpty(), documentJson = Canonical.encode(doc), artworkPath = doc.artwork?.path,
            fingerprint = Canonical.fingerprint(doc), sourceId = a.sourceId, normalizedIdentity = identity(doc))
        val id = if (old == null) addArrangement(row) else { changeArrangement(row); old.id }
        addOffsets(ArrangementUserState(id, preferredTranspose))
        index(ArrangementSearch(id, doc.title, doc.artist, a.lyricText()))
        indexLibrary(id, doc)
        return id
    }
    @Transaction open suspend fun insert(song: Song): Long {
        val doc = legacyDocument(song)
        val id = store(doc, preferredTranspose = song.transpose)
        saveUserState(SongUserState(doc.songId, song.favorite, song.notes, song.lastOpened))
        saveOffsets(ArrangementUserState(id, song.transpose, song.capo))
        return id
    }
    @Transaction open suspend fun update(song: Song) {
        val current = song(song.id) ?: return
        if (current.body != song.body || current.key != song.key || current.title != song.title || current.artist != song.artist || current.source != song.source) store(legacyDocument(song), song.id)
        saveUserState(SongUserState(current.songUuid, song.favorite, song.notes, song.lastOpened))
        saveOffsets(ArrangementUserState(song.id, song.transpose, song.capo))
    }
    @Transaction open suspend fun saveEditor(song: Song, album: String?, year: Int?, tags: List<String>, links: MusicLinks, artwork: ArtworkRef?, arrangement: ArrangementDoc? = null): Long {
        val base = legacyDocument(song)
        val doc = base.copy(album = album, year = year, tags = tags, links = links, arrangements=arrangement?.let { listOf(it) } ?: base.arrangements).let { if (artwork != null) it.copy(artwork = artwork) else it }
        Canonical.decode(Canonical.encode(doc)) // Validate native editor output before any database mutation.
        val id = store(doc, song.id.takeIf { it != 0L })
        // These details belong to the song, so sibling arrangements receive the same edits.
        for (sibling in songArrangements(doc.songId).filter { it.id != id }) {
            val old = Canonical.decode(sibling.documentJson).single().document
            store(old.copy(album = album, year = year, tags = tags, links = links, artwork = artwork ?: old.artwork), sibling.id)
        }
        saveUserState(SongUserState(doc.songId, song.favorite, song.notes, song.lastOpened))
        saveOffsets(ArrangementUserState(id, song.transpose, song.capo))
        return id
    }
    @Transaction open suspend fun delete(song: Song) { removeGrams(song.id); removeIndex(song.id); removeArrangement(song.id); removeEmptyCatalog(song.songUuid) }
    @Transaction open suspend fun deleteSelected(ids: List<Long>) { ids.distinct().forEach { id -> song(id)?.let { delete(it) } } }
    companion object {
        fun identity(doc: SongDocument) = listOf(doc.title, doc.artist, doc.arrangements.single().name).joinToString("\u001F") { Canonical.normalized(it) }
        fun legacyDocument(song: Song): SongDocument {
            val previous = song.documentJson.takeIf { it.isNotEmpty() }?.let { Canonical.decode(it).single().document }
            val parsed = if (previous != null && previous.arrangements.single().editorText() == song.body) previous else if (song.body.isBlank()) {
                SongDocument(songId = song.songUuid.ifEmpty { Canonical.uuid() }, title = song.title, artist = song.artist, arrangements = listOf(ArrangementDoc(song.arrangementUuid.ifEmpty { Canonical.uuid() }, sections = listOf(SheetSection(Canonical.uuid(), "custom", "", listOf(SheetLine(Canonical.uuid(), listOf(TextSegment(Canonical.uuid(), song.body)))))))))
            } else SheetParser.parse(song.body, "song.cho", previous).document
            val oldArrangement = previous?.arrangements?.single()
            val mode = if (oldArrangement != null && oldArrangement.originalKey.orEmpty() == song.key) oldArrangement.tonalMode else Canonical.mode(song.key.ifBlank { null })
            val a = parsed.arrangements.single().copy(originalKey = song.key.ifBlank { null }, tonalMode = mode, provenance = song.source.ifBlank { parsed.arrangements.single().provenance })
            return parsed.copy(title = song.title, artist = song.artist, arrangements = listOf(a))
        }
    }
}

@Database(entities = [CatalogSong::class, ArrangementRecord::class, SongUserState::class, ArrangementUserState::class, ArrangementSearch::class, LibraryIndex::class, LibraryGrams::class, LibraryExcerpt::class, LibraryTag::class, Setlist::class, SetlistEntry::class, AppMetadata::class], version = 4, exportSchema = true)
abstract class ChordDatabase : RoomDatabase() { abstract fun dao(): ChordDao }

val MIGRATION_3_4 = object : Migration(3,4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE setlists ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE setlists SET uuid=lower(hex(randomblob(16)))")
        db.execSQL("CREATE UNIQUE INDEX index_setlists_uuid ON setlists(uuid)")
        db.execSQL("CREATE TABLE app_metadata (name TEXT NOT NULL PRIMARY KEY,value TEXT NOT NULL)")
        db.execSQL("UPDATE library_index SET originalKey=originalKey || 'm' WHERE tonalMode='minor' AND originalKey<>'' AND substr(originalKey,-1)<>'m'")
        db.execSQL("CREATE TABLE setlist_entries_v4 (setlistId INTEGER NOT NULL, songId INTEGER NOT NULL, position INTEGER NOT NULL, entryId TEXT NOT NULL PRIMARY KEY, transposeOverride INTEGER, notes TEXT NOT NULL, FOREIGN KEY(songId) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(setlistId) REFERENCES setlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT INTO setlist_entries_v4 SELECT setlistId,songId,position,lower(hex(randomblob(16))),NULL,'' FROM setlist_entries")
        db.execSQL("DROP TABLE setlist_entries")
        db.execSQL("ALTER TABLE setlist_entries_v4 RENAME TO setlist_entries")
        db.execSQL("CREATE INDEX index_setlist_entries_songId ON setlist_entries(songId)")
        db.execSQL("CREATE INDEX index_setlist_entries_setlistId ON setlist_entries(setlistId)")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS catalog_songs (songUuid TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, artist TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS arrangements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, arrangementUuid TEXT NOT NULL, songUuid TEXT NOT NULL, name TEXT NOT NULL, originalKey TEXT, body TEXT NOT NULL, source TEXT NOT NULL, documentJson TEXT NOT NULL, artworkPath TEXT, fingerprint TEXT NOT NULL, sourceId TEXT, normalizedIdentity TEXT NOT NULL, FOREIGN KEY(songUuid) REFERENCES catalog_songs(songUuid) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_arrangements_songUuid ON arrangements(songUuid)")
        db.execSQL("CREATE UNIQUE INDEX index_arrangements_arrangementUuid ON arrangements(arrangementUuid)")
        listOf("fingerprint", "sourceId", "normalizedIdentity").forEach { db.execSQL("CREATE INDEX index_arrangements_$it ON arrangements($it)") }
        db.execSQL("CREATE TABLE IF NOT EXISTS song_user_state (songUuid TEXT NOT NULL PRIMARY KEY, favorite INTEGER NOT NULL, notes TEXT NOT NULL, lastOpened INTEGER NOT NULL, FOREIGN KEY(songUuid) REFERENCES catalog_songs(songUuid) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE TABLE IF NOT EXISTS arrangement_user_state (arrangementId INTEGER NOT NULL PRIMARY KEY, transpose INTEGER NOT NULL, capo INTEGER NOT NULL, FOREIGN KEY(arrangementId) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS arrangement_search USING FTS4(title TEXT NOT NULL, artist TEXT NOT NULL, body TEXT NOT NULL)")
        db.query("SELECT * FROM songs ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) {
                fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
                fun number(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                val id = number("id")
                val ui = Song(id, text("title"), text("artist"), text("key"), text("body"), number("favorite") != 0L, text("notes"), number("transpose").toInt(), number("capo").toInt(), number("lastOpened"), text("source"))
                val parsed = ChordDao.legacyDocument(ui)
                val a = parsed.arrangements.single().copy(id = Canonical.stable("legacy-arrangement:$id"), sourceFormat = "legacy-v1")
                val doc = parsed.copy(songId = Canonical.stable("legacy-song:$id"), arrangements = listOf(a))
                db.execSQL("INSERT INTO catalog_songs VALUES (?,?,?)", arrayOf(doc.songId, doc.title, doc.artist))
                db.execSQL("INSERT INTO arrangements VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(id, a.id, doc.songId, a.name, a.originalKey, ui.body, ui.source, Canonical.encode(doc), null, Canonical.fingerprint(doc), null, ChordDao.identity(doc)))
                db.execSQL("INSERT INTO song_user_state VALUES (?,?,?,?)", arrayOf(doc.songId, ui.favorite, ui.notes, ui.lastOpened))
                db.execSQL("INSERT INTO arrangement_user_state VALUES (?,?,?)", arrayOf(id, ui.transpose, ui.capo))
                db.execSQL("INSERT INTO arrangement_search(rowid,title,artist,body) VALUES (?,?,?,?)", arrayOf(id, ui.title, ui.artist, a.lyricText()))
            }
        }
        db.execSQL("CREATE TABLE setlist_entries_new (setlistId INTEGER NOT NULL, songId INTEGER NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(setlistId,songId), FOREIGN KEY(songId) REFERENCES arrangements(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(setlistId) REFERENCES setlists(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT INTO setlist_entries_new SELECT * FROM setlist_entries")
        db.execSQL("DROP TABLE setlist_entries")
        db.execSQL("ALTER TABLE setlist_entries_new RENAME TO setlist_entries")
        db.execSQL("CREATE INDEX index_setlist_entries_songId ON setlist_entries(songId)")
        val triggers = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'room_fts_content_sync_song_search_%'").use { while (it.moveToNext()) triggers.add(it.getString(0)) }
        triggers.forEach { db.execSQL("DROP TRIGGER `$it`") }
        db.execSQL("DROP TABLE song_search")
        db.execSQL("DROP TABLE songs")
    }
}
