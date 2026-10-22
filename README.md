# Chordroom

A native, offline Android songbook built with Kotlin, Compose, Material 3 and Room. The app upgrades the original Chord package without clearing its existing library.

## Phase 2

- Versioned canonical songs with stable UUIDs, multiple arrangements, rich metadata, sections and exact Unicode lyric/chord segments.
- Data-preserving Room 1→2 migration; personal favorites, notes, last-played and key offsets stored separately from song content.
- Staged bulk import using Android's real multiple-document, recursive-folder and ZIP pickers.
- JSON, ChordPro and TXT parsing, metadata/art previews, per-file warnings/errors, explicit review of uncertain TXT positions.
- Source/UUID/content/metadata duplicate detection; Skip, Replace arrangement, or Import as new version.
- Progress, cancellation, atomic import journals and safe resumption after interruptions.
- Managed offline artwork, validated images, cached square thumbnails and deterministic placeholders.
- Portable collection ZIP export/re-import with all arrangements and local images.

The original searchable library, favorites, editor, reader, transposition, capo, guitar/piano diagrams, Roman numerals, fullscreen, auto-scroll, notes, setlists and settings remain available. Six original demo songs are seeded only on first installation. No account, paid API, Internet permission or broad storage permission is required.

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
```

These tests exercise real Room persistence, 320-song batches, repeat imports, cancellation/recovery, ZIP defenses, image copying and archive round trips. The picker suite navigates Android DocumentsUI and imports original fixtures. See the validation report for the source-unavailable cold-start test and screenshot retrieval commands. Direct ADB testing leaves the installed app and library intact; Gradle connected-test cleanup can remove its installation.

## Import boundaries

Per batch: 2,000 arrangements / 10,000 entries / 256 MB expanded. Each song is limited to 4 MB, each image to 12 MB and 32 megapixels. Supported song suffixes: `.json`, `.cho`, `.chopro`, `.chordpro`, `.pro`, `.txt`; artwork: JPG/PNG/WebP. Text must be valid UTF-8.

Unknown chord strings and source directives are preserved with warnings. Ambiguous TXT spacing never becomes accepted anchors silently. Unsupported future JSON schemas fail per file while valid files remain importable. No source URI is needed after import.

Collection export preserves song content and artwork. Full personal-state/settings/setlist backup remains a later phase. Release signing, additional Android/device/accessibility coverage, advanced ChordPro constructs and a complete guitar-voicing database remain outside this phase. Historical phase-1 screenshots and validation are retained under `docs/`.
