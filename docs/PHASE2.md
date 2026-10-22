# Chordroom phase 2 — local arrangements and staged bulk import

This is the existing native Kotlin/Compose application. The package stays `com.henry.chord` so upgrades preserve the installed library. The display name is now Chordroom.

## Canonical format and positioning

[song-v1.schema.json](song-v1.schema.json) describes the emitted canonical JSON. A song has a persistent UUID, metadata, artwork and music links, plus one or more arrangements. Each arrangement has its own UUID, original key/mode, tempo, meter, source/provenance/license/comments, and ordered sections/lines/segments.

An anchor **always applies immediately BEFORE its segment's text**. Segment text is exact Unicode, including leading/trailing spaces, tabs, nonbreaking spaces, combining marks and emoji. Positioning never depends on monospace width or retained alignment spaces. No UTF-16 offsets are stored. A blank line is a line with no segments or empty text; consecutive chords and instrumental rows use successive anchored empty segments. Line breaks are the ordered line boundaries, never embedded newlines inside a segment.

Every section, line, segment and anchor has a stable nonempty ID, unique within its arrangement. Canonical exports include these IDs. The concise input form in the brief is supported: top-level `originalKey`/`sections` becomes a single Main arrangement; segment `chord` becomes `anchor.rawChord`. Missing subordinate IDs are deterministically derived from the parent UUID and position. Non-UUID external song/arrangement IDs are deterministically namespaced into UUIDs. They are not regenerated on repeat parsing. New ChordPro IDs are generated once at staging and persisted.

The editor keeps bracket markers attached to the intended segment, reparses changed content and recalculates boundaries. Unchanged matching lines retain IDs even after movement. Modified lines retain their identity where possible; chord anchors retain their identity when the same chord remains in the corresponding segment. Notes/favorite/transpose updates do not reparse the sheet. Unknown chord annotations are retained verbatim with warnings.

```json
{
  "schemaVersion": 1,
  "songId": "fa2ab520-8b32-4b40-a18d-3d3de1a5a340",
  "title": "Evening Lights",
  "artist": "Demo Artist",
  "artwork": {"path": "assets/cover.png", "credit": "Original artwork"},
  "arrangements": [{
    "arrangementId": "d3671a8f-4210-482b-aad0-a4e5739e5dbc",
    "name": "Acoustic", "originalKey": "G", "tonalMode": "major",
    "tempoBpm": 96, "timeSignature": "4/4",
    "sections": [{"id": "v1", "type": "verse", "label": "Verse 1", "lines": [
      {"id": "v1-l1", "segments": [
        {"id": "s1", "text": "Under the "},
        {"id": "s2", "anchor": {"id": "a1", "rawChord": "G"}, "text": "evening lights, "},
        {"id": "s3", "anchor": {"id": "a2", "rawChord": "D/F#"}, "text": "we sing"}
      ]}
    ]}]
  }]
}
```

Canonical schema version **1** is independent of Room database version **2**. An explicit JSON migration accepts the earlier unversioned/version-0 draft (`id`/`key` aliases) and records a warning. Future versions are rejected per file. JSON documents with inconsistent keys/modes, bad meter/tempo, duplicate IDs, embedded segment newlines, or malformed UTF-8 produce actionable file errors.

Optional inline multi-song manifests are supported:

```json
{"format":"chordroom-collection","schemaVersion":1,"songs":[{"schemaVersion":1,"songId":"...","title":"...","artist":"...","arrangements":[]}]}
```

Each array element must be a valid song with at least one arrangement. Up to 2,000 songs may be listed, subject to the batch arrangement and file-size limits.

## Persistence and preserving existing data

`catalog_songs` owns song identity/title/artist. `arrangements` owns its UUID, version name, original key, canonical document, content fingerprint, source identity, artwork reference and a derived editor sheet. `song_user_state` stores favorite, notes and last-played separately. `arrangement_user_state` stores preferred transpose and capo separately. Setlist entries refer to preserved numeric arrangement row IDs. FTS4 indexes lyrics/title/artist and is maintained in the same transaction as content edits.

Library/search/setlist lists query lightweight summaries, without loading every chord sheet, canonical JSON document or personal note. Reader/editor destinations observe the selected full arrangement by ID. Favorite/play actions update personal-state columns directly. Duplicate checks and artwork cleanup use metadata projections; export reads one arrangement at a time.

Migration `MIGRATION_1_2` copies every legacy song, personal property and numeric ID, assigns stable legacy UUIDs, reconstructs canonical sections/anchors and retargets the setlist foreign key. There is no destructive migration fallback. A failed upgrade rolls back; the original database remains intact. The installed app was backed up privately before upgrade; no personal database is committed to Git.

Each arrangement commit is transactional. Replace updates the target in place, retaining its IDs, personal state and all setlist references. New version keeps the matched song identity only when normalized title/artist agree; otherwise it creates a separate song. Catalog-level title edits synchronize sibling portable documents and FTS.

## Import workflow

1. Choose multiple files, an entire recursive folder, or a ZIP through Android's system picker.
2. Copy allowed sources and assets to app-private staging, preserving relative paths. User URIs are opened through ContentResolver; folders use DocumentFile. No unrestricted filesystem or network permissions are requested. Persistable URI permission is unnecessary because import copies the content immediately.
3. Discover and validate; show file/arrangement/cover counts, warnings and per-file errors. No library rows change during discovery/review.
4. Preview metadata, cover and canonical chord positioning. TXT chord-above-lyric conversion is a proposal only. Tabs, non-ASCII/combining/emoji ambiguity or column overrun retain original rows without invented anchors. The proposed bracketed sheet is editable, and explicit acceptance is required before import.
5. Duplicates default to Skip. Matching considers exact content fingerprints, arrangement UUID/source ID, and normalized title + artist + arrangement, with visible collision warnings. Similar metadata alone never silently overwrites or merges songs. Users may Skip, Replace an existing arrangement or Import as new version.
6. Import with progress and Cancel/pause. Already committed songs remain safe. A persistent atomic session journal allows recovery after activity/process interruptions. Commit rechecks duplicates. New-version UUIDs are deterministic per staged candidate so recovery cannot multiply versions after a database/journal crash gap.
7. Summary reports imported/skipped totals and the full file-error list. All-skipped packs can be finalized explicitly. Starting a new import deletes only that private staging session.

Discovery interrupted before a complete review session needs the sources selected again. A completed review/import session survives process death; original file access is no longer required. Storage limits and read failures never clear the existing library.

## Artwork and safety limits

Artwork resolves first relative to the JSON file, then the ZIP root/selected-folder root. Both `songs/Title/song.json + songs/Title/assets/cover.png` and shared root `assets/cover.png` layouts work. JPG/PNG/WebP content is validated by decoded MIME and dimensions, not just suffix. Accepted originals are stored by SHA-256 under private `artwork/`; 256×256 center-cropped JPEG thumbnails are cached under `thumbnails/`. Missing/rejected artwork gets a deterministic gradient/music-note placeholder. Source URLs/credits are preserved; no images are downloaded.

Bounds: 2,000 arrangements, 10,000 entries, 256 MB expanded data per batch; 4 MB per song; 12 MB per image; image sides ≤8192 px and total ≤32 megapixels; folder depth ≤16. ZIP inflation is streamed to private disk, including accounting for rejected/unsupported payloads. Expanded-size, per-entry and high compression-ratio limits prevent unbounded inflation. Parent traversal, absolute paths, Windows drive/backslash names, unsafe path components and case-colliding paths are rejected. ZIP entries are never extracted outside their session. A malformed file is reported independently so other valid songs can still be imported.

Artwork is committed before the database reference, and content-addressed files are shared safely. Garbage collection considers every committed arrangement before removing unused managed files. Interrupted copies can leave harmless unreferenced files until the next completed import cleanup.

## Portable collection archive

Import → Export collection uses the system Create Document picker. It writes a ZIP with one schema-v1 JSON document per arrangement in `songs/<song UUID>/<arrangement UUID>.json`, shared artwork in `assets/`, and a `collection.index` listing those documents. The index is `{"format":"chordroom-collection-index","schemaVersion":1,"files":["songs/.../....json"],"includesPersonalState":false}`. Each path must be safe, unique, present and point to JSON within that same folder/archive.

A validated index requests identity-preserving restoration: distinct arrangement IDs remain distinct even when two versions intentionally have identical sheets. An already-existing arrangement UUID still defaults to Skip and differing content needs explicit replacement; the index never authorizes overwrites. Ordinary loose imports retain conservative content/metadata duplicate detection. The recursive importer preserves IDs, metadata, exact segments, annotations and images. Documents remain separate to avoid loading an entire collection into one JSON buffer.

This phase exports collection **content**. A complete backup of user favorites, notes, offsets, settings and setlists remains a later backup phase; these are deliberately stored separately from the canonical song content.

## Reproducible fixtures and evidence

Run `python tools/generate_demo_packs.py` to regenerate original demo material. It creates the 320-song nested ZIP, a recursive folder with local artwork, two single files, a TXT alignment review file, and a rejected traversal archive. The bulk ZIP includes two intentionally invalid files for per-file error reporting.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
$adb = 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb -s emulator-5554 push docs\demo-packs /sdcard/Documents/ChordroomPhase2
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2EngineTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2PickerTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
```

Direct installation/testing preserves the existing app data; avoid uninstalling or `pm clear`. The picker fixture cleanup is restricted to the original demo records created by this test. See [phase-2 evidence](evidence/PHASE2-RESULTS.md) for actual results, screenshots, limits and final commands.

Android references used for the implementation: [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files), [Room migration testing](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper).
