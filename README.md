# Chordroom

A native, offline Android songbook built with Kotlin, Compose, Material 3 and Room. The app upgrades the original Chord package without clearing its existing library.

## Current implementation

Native measured chord/lyric alignment, colored section groups and instrumental rows; Symbols/Roman/Both labels configured in Settings; non-destructive transpose; full root-position piano chords or one validated guitar diagram; timed performance scrolling; structured song editing; full ZIP backup/restore; and colorful light/dark/system palettes. Home offers a random song and shuffle button. Settings open one category at a time with live typography, chord-label and spelling previews. The launcher uses the owner's supplied purple artwork with adaptive/themed icon resources.

**1.0.0-rc3:** long-press a library row to select versions, select every matching result (including unloaded pages), favorite/unfavorite or delete with confirmation. Home's shuffle refreshes both the artwork card and random collection. Settings have five icon categories. The compact chord sheet has adjustable side margins, chord/lyric gaps, line gaps and section gaps with live examples. Speed changes apply every 0.5 seconds while scrolling continues; fullscreen shows the transpose delta. More/capo controls and capo-based guitar shapes are removed; old stored values remain inert for data compatibility.

**1.0.0-rc2 simplifies the interface:** setlists/sessions, reader label toggles, lyrics-only/chords-only modes, section-jump/share/JSON-export menus, inversion selectors and chord-dialog instrument switches have been removed. Existing setlist records remain in the database/backup format for compatibility; they have no active UI. Songs and personal data are preserved. Android safe-drawing insets keep headers below status icons and cutouts.

Read the detailed, copyable **[song import guide (.txt)](docs/SONG-IMPORT-GUIDE.txt)**. New imports are strictly syntax/type/field/chord/asset checked; errors identify the file and field or line. Invalid files cannot be saved. Existing legacy library records remain readable.

Package: **`com.henry.chord`**. Launcher: **`com.henry.chord/.MainActivity`**. Debug APK: **`app/build/outputs/apk/debug/app-debug.apk`**. Room schema: **4**, with safe migrations from 1/2/3. Preferences use DataStore.

Read the [performance/editor/backup format guide](docs/PERFORMANCE-EDITOR-BACKUP.md) and [final acceptance evidence](qa-artifacts/REPORT.md). Earlier phase reports below are historical; the final audit states exactly which checks were rerun and which coverage remains limited.

The [rc3 verification](qa-artifacts/rc3/REPORT.md) records the latest executed checks and screenshots. The [rc2 interface/import verification](qa-artifacts/refinement/REPORT.md) and earlier [release audit](qa-artifacts/REPORT.md) are historical evidence. Physical Pixel 9/recent-Android/TalkBack and rendering-performance coverage limits remain; this is not an unconditional production sign-off. Phase summaries below describe the architecture/history; the rc2/rc3 simplifications above supersede removed UI features.

## Historical Phase 4

- Ranked offline title/artist/album/tag/lyric search, Unicode normalization, word fragments and highlighted lyric excerpts.
- Paged compact artwork rows, Favorites/Recent views, sorting/filtering and preserved reader-return context.
- Light/Dark/System themes, landscape navigation rail, compact performance header and large-text layouts.
- Bounded local thumbnail cache, deterministic fallbacks and native document-picker artwork changes.
- User-provided Spotify/YouTube links and clearly separate search actions; no account or network permission.

Read the [library/search implementation guide](docs/PHASE4.md) and [Phase 4 device evidence](docs/phase4-evidence/RESULTS.md).

## Historical Phase 3

- Persistent Chord symbols / Roman numerals / Both preferences, with major/minor and diatonic spelling context.
- Separate immutable canonical chords and saved transposition; current-key labels and slash basses transpose together.
- Tappable sounding chords, spelled notes, chromatic piano with voiced octaves/inversions, and validated guitar fingerings.
- Explicit unknown-key editing and unsupported-chord warnings; offline native Kotlin theory with deterministic tests.

Read the [theory conventions and implementation](docs/PHASE3.md) and [Phase 3 emulator evidence](docs/phase3-evidence/RESULTS.md).

## Historical Phase 2

- Versioned canonical songs with stable UUIDs, multiple arrangements, rich metadata, sections and exact Unicode lyric/chord segments.
- Data-preserving Room 1→2 migration; personal favorites, notes, last-played and key offsets stored separately from song content.
- Staged bulk import using Android's real multiple-document, recursive-folder and ZIP pickers.
- JSON, ChordPro and TXT parsing, metadata/art previews, per-file warnings/errors, explicit review of uncertain TXT positions.
- Source/UUID/content/metadata duplicate detection; Skip, Replace arrangement, or Import as new version.
- Progress, cancellation, atomic import journals and safe resumption after interruptions.
- Managed offline artwork, validated images, cached square thumbnails and deterministic placeholders.
- Portable collection ZIP export/re-import with all arrangements and local images.

These features were delivered in those phases; the rc2 removals above supersede the older setlist and reader-option UI. Six original demo songs are seeded only on first installation. No account, paid API, Internet permission or broad storage permission is required.

Read the [phase-2 format and implementation guide](docs/PHASE2.md), [canonical JSON schema](docs/song-v1.schema.json), and [actual device validation](docs/evidence/PHASE2-RESULTS.md).

## Build and run

Requires JDK 17, Android SDK platform 36, and an existing Android device/emulator with API 26+. Set `ANDROID_HOME` or add an ignored `local.properties` containing `sdk.dir`.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.henry.chord/.MainActivity
```

Use `install -r` to preserve data. Do not uninstall or clear storage to apply an upgrade. The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Device tests and original demo packs

```powershell
python tools/generate_demo_packs.py
.\gradlew.bat assembleDebugAndroidTest
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb push docs\demo-packs /sdcard/Documents/ChordroomPhase2
adb shell am instrument -w -e class com.henry.chord.Phase2EngineTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.henry.chord.Phase2PickerTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.henry.chord.RefinementUiTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.henry.chord.RefinementImportTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.henry.chord.Phase4SearchTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.henry.chord.Phase4LibraryTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am force-stop com.henry.chord
adb shell am instrument -w -e class com.henry.chord.Phase4ColdStartTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
```

These tests exercise real Room persistence, 320-song batches, repeat imports, cancellation/recovery, ZIP defenses, image copying and archive round trips. The picker suite navigates Android DocumentsUI and imports original fixtures. See the validation report for the source-unavailable cold-start test and screenshot retrieval commands. Direct ADB testing leaves the installed app and library intact; Gradle connected-test cleanup can remove its installation.

For a library containing previously imported Phase 2 demo packs, use the final picker suite below: the historical `Phase2PickerTest` removes/recreates its named demo fixtures as part of setup. The new release picker suite deletes only newly inserted test rows and verifies every existing canonical document is unchanged.

```powershell
python tools/generate_release_packs.py
adb push qa-artifacts\picker-fixtures /sdcard/Documents/ChordroomRelease
adb shell am instrument -w -r -e class com.henry.chord.ReleaseEngineTest,com.henry.chord.Phase2EngineTest,com.henry.chord.Phase4SearchTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e class com.henry.chord.ReleasePerformanceTest,com.henry.chord.ReleaseEditorTest,com.henry.chord.ReleasePickerTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
```

The real-picker tests assume the API 30 emulator's internal-storage label `sdk_gphone_x86`; adapt that selector for another device. `ReleaseEditorTest` exports a timestamped `Documents/Chordroom-release-backup-*.zip` through the system picker and merges it back without replacing existing songs. Do not use destructive test setup on a personal library. Use `connectedDebugAndroidTest` only with a dedicated disposable test AVD; direct ADB instrumentation is used for the owner's existing installation. The runner supports `-ServerPort`/`-Serial` for machines with conflicting ADB installations. The optional owner-snapshot cleanup test requires an explicit opt-in argument and private snapshot files.

## Importing and backing up your songs

Open **Import → Choose files or ZIP** to select one or many UTF-8 native JSON/ChordPro/TXT files or a ZIP. **Choose a folder** recursively scans an Android Storage Access Framework tree, including nested song folders and relative assets. Review the song/artwork previews, warnings, per-file errors and duplicate choices, then import. Ambiguous TXT chord placement needs explicit approval. Local copies remain usable after the source is disconnected or deleted.

For example, a folder can contain `songs/Title/song.json` and `assets/cover.png`; use `artwork.path: "assets/cover.png"` in the JSON. See the [schema and format examples](docs/PHASE2.md). In the reader, use **Edit song** for native section/anchor editing and **Song links** for verified user-provided links or clearly labeled searches.

**Settings → Library & backup → Export full backup ZIP** saves all versions, artwork, favorites, notes, offsets, history and preferences, including legacy setlist records for compatibility. **Restore full backup** validates and previews the archive. Merge retains existing edits when IDs match; Replace needs an explicit destructive-data acknowledgement. **Import → Export collection** contains portable content/artwork only. For a single song use the reader's ChordPro export button. Native JSON files are also stored inside collection/full-backup archives.

Library selection applies to the current search/filter, not just visible rows. Tap a row/checkbox to toggle selection; Back/Close exits selection. Changing the search/filter clears selection. Delete removes selected arrangements; other versions of a song remain. Favorites are shared across versions of the same song. Export a backup before deleting anything you may want back.

Settings categories: **Appearance**, **Song reader**, **Chords & instrument**, **Performance**, **Library & backup**. Reader spacing defaults to 16 dp side margins, 6 dp between source/wrapped lyric rows, 2 dp between chords and lyrics, and 12 dp before sections. The inner colored section inset is 6 dp. Native touch targets expand around visually compact chord text; dense adjacent changes may use extra vertical lanes to preserve precise anchors.

## Installing on a physical Pixel 9

Enable Developer options and USB debugging on the phone, connect it by USB, and accept its RSA authorization prompt. Open this repository in Android Studio, allow Gradle sync with JDK 17, select the phone and run the `app` configuration. Alternatively:

```powershell
adb devices -l
.\gradlew.bat assembleDebug
adb -s <phone-serial> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <phone-serial> shell am start -n com.henry.chord/.MainActivity
```

Use the same signing key for updates. The APK is a developer debug build, not a Play Store-signed release. Avoid uninstalling or clearing storage: those actions remove private local data. Export a full backup before changing signing keys or installations.

Android keeps the library in `/data/user/0/com.henry.chord/databases/chord.db`, managed artwork/thumbnail files under `files/artwork` and `files/thumbnails`, preferences under `files/datastore`, and staged imports in private files/cache. These are sandboxed app paths, not folders users must grant storage access to. Exported files live at the SAF destination you choose. No network or broad storage permission is requested.

## Import boundaries

Per batch: 2,000 arrangements / 10,000 entries / 256 MB expanded. Each song is limited to 4 MB, each image to 12 MB and 32 megapixels. Supported song suffixes: `.json`, `.cho`, `.chopro`, `.chordpro`, `.pro`, `.txt`; artwork: JPG/PNG/WebP. Text must be valid UTF-8.

New imports reject malformed JSON, unknown fields, invalid field types/IDs, unsupported chord tokens/directives, invalid metadata/links and missing declared local artwork with per-file errors. Native imports require schema 1. Existing older library data is preserved through the backward-compatible decoder. Ambiguous TXT spacing still requires explicit preview approval. No source URI is needed after import.

Full backups add a 10,000-arrangement / 12,000-member / 512 MB expanded archive boundary; ordinary staged import limits above remain unchanged. Release signing, broader physical-device/accessibility coverage, advanced ChordPro constructs and a complete guitar-voicing database remain additional work. Historical phase screenshots and validation are retained under `docs/`; current known limitations are listed in the final audit.
