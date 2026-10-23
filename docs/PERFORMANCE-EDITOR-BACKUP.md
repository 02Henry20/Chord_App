# Performance reader, editing, setlists and full backups

Historical rc1 architecture/format notes. **rc2 removes the setlist UI, reader options/modes/jumps/sharing/native-JSON-export controls, diagram inversions and chord-dialog instrument switches.** The reader always combines chords and lyrics; instrument and label preferences live in expandable Settings with live previews. Existing setlist records remain only for backward-compatible persistence/backups. See the [current import guide](SONG-IMPORT-GUIDE.txt) and [rc2 verification](../qa-artifacts/refinement/REPORT.md) for current behavior.

Native Kotlin / Compose; no WebView, JavaScript engine, account or network permission.

## Reader layout and controls

Canonical schema 1 remains unchanged: a chord anchor applies **before the exact text of its segment**. `SheetLayout` converts segment starts to UTF-16 offsets without inserting spaces. It wraps at measured native text widths, prefers word boundaries, retains every source character, and avoids splitting combining marks, emoji modifiers, ZWJ sequences and paired regional indicators. `AnchoredSheet` measures each rendered row using Compose's text shaper and locates a chord using the measured horizontal position of its source offset. If an upcoming label would exceed the available width, the row can break at that anchor. Adjacent labels use separate vertical lanes when necessary; they never push lyric syllables sideways. Very long labels can wrap within the available width. Chords have clickable 48 dp-high regions and announce both their label and actual sounding name.

Reader options offer Chords and lyrics / Lyrics only / Chords only, section jumps, return to top, native JSON export, and metadata-only sharing. The normal toolbar exports ChordPro. JSON exports preserve canonical sections, IDs, anchors and metadata; use a collection/full-backup ZIP to include binary artwork. Performance mode hides the header and app navigation, retains small controls and current key, and supports explicit setlist previous/next. Android Back exits fullscreen first. Space toggles scrolling while the reader has hardware-key focus; volume keys retain Android behavior.

ChordPro export writes the original key (including explicit minor mode), never the preferred transpose. It is a text interchange subset: stable UUIDs, personal state, artwork and unknown-mode distinctions require native JSON/full backup rather than a ChordPro round trip. Both text export paths write UTF-8 on the IO dispatcher.

Auto-scroll integrates `withFrameNanos` monotonic elapsed time in **dp/second**, multiplied by display density. The elapsed delta is capped at 100 ms to avoid a jump after a delayed frame. Speed is persisted, with optional 0/3/5-second lead-in. Dragging pauses; opening a chord or reader-options dialog pauses; backgrounding pauses; reaching the bottom stops. Resume starts at the current position. No running job survives leaving the composition, and process recreation starts paused. Font size, line spacing, chord color, display mode, speed and lead-in persist in DataStore. Chord-color options use contrasting foregrounds chosen for the actual active theme.

## Editor

The native editor edits title, artist, original key/mode, tempo, time signature, personal notes, source, album, year, tags, stored music links and local artwork. Sections can be added, removed, renamed, assigned a type and reordered. Lines can be added, removed and reordered, including blank and chord-only rows.

Tap **Edit line** to edit plain lyrics. Position the cursor at a syllable, enter a chord and choose **Insert chord at cursor**. Select an existing anchor to change, move to the current cursor, or remove it. The dialog previews the same native layout as the reader. Cursor positions are rounded to a complete grapheme boundary. A contiguous lyric replacement relocates anchors: preceding anchors stay, following anchors move by the replacement delta, and anchors deleted with the replaced text attach at its start. Stable anchor IDs survive. Pressing Return creates separate canonical lines on Apply while retaining anchors and the original first-line ID. Review the live preview after substantial replacements. The sheet has up to 30 undo steps with a bounded memory budget; undo history is session-local. All unsaved fields can be reverted, and Back/Close prompts before discarding edits. An advanced ChordPro editor remains available.

Large sheet/body/note snapshots use private cache files and tiny SavedState keys, avoiding Android's Binder bundle-size limit during Activity recreation. Cache-backed unsaved drafts can be removed by Android's cache cleanup; explicit Save persists content in Room. Old snapshot files expire after seven days. Canonical output is validated again before the editor changes the database.

Saving writes canonical original chords and the explicit original key/mode. Reader transpose is separate and never gets baked into those chords. Unknown chord annotations remain editable; unsupported diagrams/analysis are not fabricated. Artwork changes stage in cache and become managed files only when saved.

## Setlists and database migration

Room schema 4 adds a persistent setlist UUID and a unique identity for **each occurrence** of a song. Migration 3→4 copies all existing setlist IDs, song references and positions; new identities do not change the library's canonical documents or personal song state. The complete supported migration chain is 1→2→3→4. There is no destructive migration fallback.

Setlists support create, rename, duplicate, delete, repeated songs, removal of one occurrence, and long-press handle drag reorder. Arrow controls offer an accessible precise alternative. Each occurrence has optional notes and a −24…+24 semitone override. A blank override uses the arrangement preference. Reader routes carry the occurrence ID, so repeated songs and previous/next are unambiguous. Transposing an occurrence with an override updates that occurrence, not the arrangement preference. Drag reorder applies on release; edge auto-scroll during a long drag is not implemented—use multiple drags or arrow controls for distant moves.

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
