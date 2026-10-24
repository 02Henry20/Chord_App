# Performance reader, editing and full backups

The reader always combines chords and lyrics. Five expandable icon categories hold preferences and live previews. Setlist UI, display-mode/jump/share/JSON-export menus, inversion selectors, instrument switches inside diagrams, More and capo controls have been removed. Legacy setlist/capo data remains only for compatible persistence/backups. See the [import guide](SONG-IMPORT-GUIDE.txt) and [latest rc3 verification](../qa-artifacts/rc3/REPORT.md).

Native Kotlin / Compose; no WebView, JavaScript engine, account or network permission.

## Reader layout and controls

Canonical schema 1 remains unchanged: a chord anchor applies **before the exact text of its segment**. `SheetLayout` converts segment starts to UTF-16 offsets without inserting spaces. It wraps at measured native text widths, prefers word boundaries, retains every source character, and avoids splitting combining marks, emoji modifiers, ZWJ sequences and paired regional indicators. `AnchoredSheet` measures each rendered row using Compose's text shaper and locates a chord using the measured horizontal position of its source offset. If an upcoming label would exceed the available width, the row can break at that anchor. Adjacent labels use separate vertical lanes when necessary; they never push lyric syllables sideways. Very long labels can wrap within the available width. Chord text is visually compact; Compose expands native touch regions around clickable labels, which announce both the label and actual sounding name. Side, row, chord/lyric and section spacing are configurable. Native tests compare chord positions against actual shaped glyph coordinates after reflow.

The normal toolbar offers favorites, links, editor and ChordPro export. Use a collection/full-backup ZIP for native JSON and binary artwork. Performance mode hides the header and app navigation, retaining small controls, sounding key and the semitone delta. Reset is directly available beside normal transpose controls. Android Back exits fullscreen first. Space toggles scrolling while the reader has hardware-key focus; volume keys retain Android behavior.

ChordPro export writes the original key (including explicit minor mode), never the preferred transpose. It is a text interchange subset: stable UUIDs, personal state, artwork and unknown-mode distinctions require native JSON/full backup rather than a ChordPro round trip. Both text export paths write UTF-8 on the IO dispatcher.

Auto-scroll integrates `withFrameNanos` monotonic elapsed time in **dp/second**, multiplied by display density. The elapsed delta is capped at 100 ms to avoid a jump after a delayed frame. Speed is sampled/persisted every 500 ms without restarting or pausing motion, with optional 0/3/5-second lead-in. Dragging the sheet pauses; opening a chord pauses; backgrounding pauses; reaching the bottom stops. Resume starts at the current position. No running job survives leaving the composition, and process recreation starts paused. Font size, spacing/margins, chord color, speed and lead-in persist in DataStore. Settings changes merge atomically to avoid overwriting another recent adjustment. Chord-color options use contrasting foregrounds chosen for the actual active theme.

## Editor

The native editor edits title, artist, original key/mode, tempo, time signature, personal notes, source, album, year, tags, stored music links and local artwork. Sections can be added, removed, renamed, assigned a type and reordered. Lines can be added, removed and reordered, including blank and chord-only rows.

Tap **Edit line** to edit plain lyrics. Position the cursor at a syllable, enter a chord and choose **Insert chord at cursor**. Select an existing anchor to change, move to the current cursor, or remove it. The dialog previews the same native layout as the reader. Cursor positions are rounded to a complete grapheme boundary. A contiguous lyric replacement relocates anchors: preceding anchors stay, following anchors move by the replacement delta, and anchors deleted with the replaced text attach at its start. Stable anchor IDs survive. Pressing Return creates separate canonical lines on Apply while retaining anchors and the original first-line ID. Review the live preview after substantial replacements. The sheet has up to 30 undo steps with a bounded memory budget; undo history is session-local. All unsaved fields can be reverted, and Back/Close prompts before discarding edits. An advanced ChordPro editor remains available.

Large sheet/body/note snapshots use private cache files and tiny SavedState keys, avoiding Android's Binder bundle-size limit during Activity recreation. Cache-backed unsaved drafts can be removed by Android's cache cleanup; explicit Save persists content in Room. Old snapshot files expire after seven days. Canonical output is validated again before the editor changes the database.

Saving writes canonical original chords and the explicit original key/mode. Reader transpose is separate and never gets baked into those chords. Unknown chord annotations remain editable; unsupported diagrams/analysis are not fabricated. Artwork changes stage in cache and become managed files only when saved.

## Setlists and database migration

Room schema 4 adds a persistent setlist UUID and a unique identity for **each occurrence** of a song. Migration 3→4 copies all existing setlist IDs, song references and positions; new identities do not change the library's canonical documents or personal song state. The complete supported migration chain is 1→2→3→4. There is no destructive migration fallback.

Setlist tables, occurrence identities, notes and offsets remain serialized for compatibility. There is no active setlist screen, reorder UI, occurrence transpose or setlist navigation. Library long-press selection instead offers select-all across matching pages, favorites and transactional confirmed deletion of selected arrangements. Changing search/filter clears selection; rotation retains it.

## Full backup ZIP schema 1

Settings → **Export full backup ZIP** uses `ACTION_CREATE_DOCUMENT`. **Restore full backup** uses `ACTION_OPEN_DOCUMENT`, validates the archive and previews song/version/cover/setlist counts before offering Merge or Replace. Replace requires an explicit acknowledgement that the current library will be deleted. Ordinary song imports retain their separate staged preview/conflict choices.

The archive contains:

- `backup.json`: `format: "chordroom-backup"`, `schemaVersion: 1`, creation timestamp, document paths/hashes, users, arrangement offsets, setlists/occurrences and preferences.
- `songs/<hashed-arrangement-id>.json`: native canonical schema-1 documents, one arrangement per file; song and arrangement UUIDs remain intact.
- `assets/<content-hash>.<jpg|png|webp>`: managed originals, referenced by portable document paths. Thumbnails regenerate locally.

`users` entries hold song UUID, favorite, notes and last-opened timestamp. `offsets` reference arrangement UUID with transpose/capo. Setlists have stable UUID/name and ordered entries with entry UUID, arrangement UUID, optional transpose override and notes. Preferences have an independent version 1 and preserve theme, instrument, labels, enharmonic spelling, typography, colors, display mode, speed, lead-in and awake option. No arbitrary device path is exported.

Restore resolves UUID references to local Room row IDs. Merge skips existing arrangements, preserves their personal edits and existing setlist order/notes, and adds missing songs/versions/setlists. Repeated merge is idempotent. Conflicting identities fail with the transaction rolled back. Replace restores the validated archive as the collection. Accepted immutable artwork is copied first; the database changes occur in one cancellable Room transaction. A durable `app_metadata` settings journal bridges the Room and DataStore commits and is recovered at app start if interrupted. The settings checkbox can exclude device preferences.

All member names are validated; absolute paths, traversal, duplicate paths and unsafe filenames are rejected. Extraction is streamed into private cache, capped at 12,000 entries / 512 MB expanded. A backup holds up to 10,000 arrangements; song members are capped at 4 MB, the manifest at 16 MB, images at 12 MB / 32 megapixels. UTF-8 decoding is strict, song content hashes detect corruption, and image content/dimensions are validated before database replacement. Future unsupported schema versions fail with an update message. Native schema-0 song drafts still use the existing canonical migration. These are portable logical backups, not raw SQLite files.

If managed artwork is missing, export asks for repair/change instead of silently creating an incomplete backup. Regenerable thumbnails and deterministic placeholders do not need to be archived. Cancellation can leave an unreferenced content-addressed image or cache preview, never a database row pointing to a half-written image. Ordinary later import cleanup can remove unreferenced managed files.

## Color design

The app uses explicit violet, teal and amber Material 3 palettes on lavender-white and deep navy surfaces. A restrained static rainbow ribbon accents top-level screens; there is no animation or rainbow lyric background while performing. This supersedes Phase 4's wallpaper-derived palette so colors stay predictable across offline devices. Tests check a minimum 4.5:1 contrast for primary text, supporting text and all three accent foregrounds on the principal background and raised surface in both themes. Generated artworks remain deterministically varied per song. Color is paired with labels/icons and is not the sole state indicator.
## rc3 spacing and controls

Settings now has five icon categories. Reader options live under Song reader and Performance; chord/instrument/spelling choices share Chords & instrument. There is no More menu or active capo behavior. Fullscreen's upper left displays the semitone delta. Auto-scroll uses the latest applied speed without restarting its frame loop; slider changes are sampled every 500 ms.

Version-1 backup settings accept four additive optional numeric fields: `sheetMargin` (4–40 dp, default 16), `songLineGap` (0–24 dp, default 6), `chordLineGap` (0–12 dp, default 2), `sectionGap` (0–32 dp, default 12). Old backups gain these defaults. New backups round-trip all four. Room schema remains 4. Legacy capo/setlist data stays serialized for compatibility but has no active UI behavior.
