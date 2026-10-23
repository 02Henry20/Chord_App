# Library, search, thumbnails and links

Phase 4 is a native Kotlin/Compose/Room update, app version 4.0-phase4, database schema 3. It retains the offline song documents, arrangements, IDs, notes, favorites, transposition, capo and setlist references from earlier phases. No Internet or broad storage permission is added.

## Searching a large library

`LibrarySearch.kt` builds a derived index; canonical lyrics/chord anchors are unchanged. Room migration 2→3 creates and populates:

- `library_index`: compact display metadata, normalized title/artist/album/tags/lyrics, mode/BPM and insertion order.
- `library_grams`: Room FTS4 with ASCII-encoded Unicode 1/2/3-character grams. This supports word fragments, including interior fragments and short Japanese/Latin searches, rather than only word-prefix matching.
- `library_excerpts`: overlapping, grapheme-safe chunks for bounded lyric previews. Chunks are at most 240 graphemes with 96-grapheme overlap; only visible matching rows fetch an excerpt.
- `library_tags`: indexed exact tag membership, avoiding any dependency on SQLite JSON1 availability.

Normalization folds case, diacritics and common Latin equivalents (including Vietnamese đ), joins apostrophes inside words, and treats other punctuation/whitespace as word separators. Canonical text is never normalized in place. Highlight ranges map back to original UTF-16 grapheme boundaries, so decomposed accents and Unicode text remain intact.

FTS supplies indexed candidates; bound `instr` predicates remove gram false positives and require every query term to occur in one of the searchable fields. Queries do not interpolate user text as SQL or FTS operators. The 256-character search field uses a 160 ms debounce. Ranking is exact title, title prefix, title contains, artist, tags/album, then lyrics. The selected title/artist/added/opened/favorites sort breaks ties within those relevance groups. Without a query, it directly controls ordering.

Results load in batches of 100 using a stable arrangement ID join. The library never loads canonical JSON/chord bodies into its result rows. Lazy rows have stable keys/content types. Query, tab, filters, sort, loaded limit and list position are saveable. The list waits for its first Room emission before mounting, protecting the restored scroll anchor. Search, Favorites and Recent share the same experience. The single sort/filter panel avoids permanent filter clutter; artist matching is normalized, tag membership and original-key filters use explicit values.

Existing rows did not record insertion timestamps: their preserved numeric ID supplies the historical import order. New imports/edits get a genuine insertion timestamp, retained on replacement. Transactions update both the original FTS table (for compatibility) and the new derived indexes. Deletes remove gram entries and cascade through derived metadata/excerpts/tags. The version 1→2→3 migration path is tested without rewriting stored documents.

See SQLite's [FTS4 documentation](https://www.sqlite.org/fts3.html) for token/index behavior. The implementation uses implicit conjunction between encoded tokens, which works on Android SQLite builds without the optional enhanced FTS query grammar.

## Layout and preferences

The portrait list uses square 52 dp artwork, bold titles, artist/version, original→preferred key, one small tag, and a 48 dp heart control. Favorites also appear in home rows, setlist rows and the reader. Recent viewing history is per-device and can be cleared in Settings without deleting songs or favorites.

Light/Dark/System preferences are persistent. Legacy dark/light choices are retained; new installs follow System. Android 12+ uses native wallpaper-derived Material colors. New installs default to Piano; existing instrument preferences remain intact. Landscape uses a native navigation rail and combines the library heading/search into a compact row. Reader artwork stays 40–56 dp; album/year/BPM/original key are shown when available. Landscape and large text reduce the header; fullscreen performance mode hides it.

## Local artwork

Imported image references remain managed app-private paths. `SongArtwork` recovers missing thumbnails from the managed source; missing/corrupt sources show a deterministic gradient/music-motif placeholder. `ThumbnailCache` keeps a 12 MB LRU of downsampled local bitmaps. Decoding/generation happens off the UI thread, with stable square cropping. Neither the loader nor placeholder uses a network request.

Edit song → Album, tags, artwork & links → Change artwork opens `ACTION_OPEN_DOCUMENT` for JPEG/PNG/WebP. ContentResolver streams are copied to private cache with a 12 MB cap; actual MIME, dimensions and decoding are validated. The choice previews locally and applies when the editor is saved. It is copied into managed storage before the database commit; source URI access is not needed afterward, and persistable/broad storage grants are unnecessary. Staging images older than one day are cleaned on a later selection. Existing art remains available if selection or saving fails.

Song-level album/year/tags/links/artwork edits update sibling arrangements transactionally, preserving their chord sheets, personal offsets and setlist entries. Collection export/re-import keeps the canonical structure and managed artwork; the schema remains song version 1.

## Song links

`SongLinks.kt` validates Spotify track and YouTube video URLs against explicit service hosts and identity formats. Accepted forms include normal Spotify track URLs (including intl paths), `spotify:track:…`, YouTube watch/shorts/embed/live URLs, `youtu.be`, and `vnd.youtube:…`. They are opened as normalized HTTPS `ACTION_VIEW` intents with the browsable category, allowing the installed app or browser to handle them. Missing handlers produce a readable message rather than a crash.

The app validates URL shape, not official authorship/licensing or remote availability. Links are chosen by the user/import provider; the editor asks for official links the user has checked. No link is guessed from a title. Missing/invalid stored links instead offer separately labeled Search Spotify / Search YouTube actions. Invalid legacy HTTP links remain editable with a warning and cannot open directly; dangerous/non-approved schemes are rejected on import and invalid track/video URLs are rejected in the editor. Spotify search path spaces are percent-encoded; YouTube search uses query encoding.

No Spotify credentials, artwork scraping, audio API, WebView or JavaScript is involved. Native UI tests intercept ACTION_VIEW using an Android activity monitor and fake track/video IDs, so no external service/app is called.

## Verification and limits

See [actual emulator results and screenshots](phase4-evidence/RESULTS.md). Validation includes a 5,007-song on-disk database with longer original lyric fixtures, the upgraded real library, native artwork selection, search highlights/normalization/ranking, scroll restoration, filters, safe link intents, favorites across an actual process restart, portrait/landscape and 150% system text. The original display and text-scale settings are restored afterward.

An optional grid and online enrichment are not included. The native dynamic-color branch requires Android 12+ and was not exercised on the existing API 30 AVD; physical Pixel 9 testing remains outside the available environment. Full personal-state backup/export remains a later phase.
