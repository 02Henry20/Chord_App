# Chordroom release acceptance audit

Audit date: 23 October 2026. Native Android release candidate **1.0.0-rc1 (7)**, package **`com.henry.chord`**, activity **`.MainActivity`**. This is a working Kotlin/Compose/Room implementation, installed and run on the owner's existing emulator. **Full production release acceptance remains conditional**: functional checks passed, but smooth rendering and complete modern-device/accessibility coverage are not certified. The phases were implemented and audited; that does not mean every acceptance criterion passed without qualification.

## Delivered implementation

- Offline native library with indexed ranked title/artist/album/tag/lyric-fragment search, highlighted excerpts, paged artwork rows, favorites, history, filters and sorting.
- Canonical schema-1 songs/arrangements with stable IDs, exact text segments and chord anchors. Room schema 4 with migrations 1→2→3→4; personal state separated from canonical content; DataStore preferences.
- SAF single/multiple files, recursive folders and ZIP imports; metadata/artwork previews, errors per file, duplicate decisions, cancellation/recovery and managed offline originals/thumbnails. No Internet or broad storage permission.
- Symbols/Roman/Both labels, major/minor and diatonic-spelling scale degrees, immutable original chords, root/slash-bass transpose, explicit unknown-key handling, native piano keyboard and validated guitar diagrams.
- Measured chord-over-syllable reader, collision lanes, Unicode-safe wrapping, lyrics/chords-only modes, fullscreen, time-based scrolling, speed/lead-in controls, manual/chord/background pause, section jumps, keep-awake and hardware Space.
- Native metadata/section/line editor with cursor-position chord insertion/move/removal, preview, undo/revert and unsaved warning. Setlist CRUD, repeated occurrences, handle drag/arrows, per-entry key overrides/notes and fullscreen previous/next.
- Versioned full ZIP backup with all arrangements, artwork, favorites, offsets, history, notes, setlists and preferences. Preview/Merge/acknowledged Replace; validated atomic database restore and durable preference recovery journal.
- Explicit violet/teal/amber palettes in Light/Dark/System, colorful deterministic artwork and static rainbow accent. Foreground contrast tests cover principal/raised surfaces in both themes.

## Environment and actual build

The already-running **Pixel_4_API_30**, serial **`emulator-5554`**, was reused. Android 11 / **API 30**, `sdk_gphone_x86` (x86), boot-completed property **1**. Installed alternatives were API 25/30; there was no installed Pixel 9 or modern API image. No AVD was wiped or replaced.

Physical configuration: **1080×2280, 440 dpi**. Most functional screenshots/tests used a temporary Pixel-9-like **1080×2424, 420 dpi** viewport, plus landscape and font scale **1.5**. Original size/density/font scale were restored. This validates a similar logical viewport, not Pixel 9 hardware or recent Android behavior. See [device configuration](device-config.txt).

JDK `A:\AI\runtimes\jdk\microsoft-17.0.20`; SDK `A:\AI\runtimes\android-sdk`; Gradle cache `A:\AI\cache\gradle`; emulator executable `C:\Android\sdk\emulator\emulator.exe`. Gradle 8.13 / AGP 8.13.2 / Kotlin 2.2.21, compile/target SDK 36, min SDK 26.

Executed build tasks:

```powershell
$env:JAVA_HOME='A:\AI\runtimes\jdk\microsoft-17.0.20'
$env:ANDROID_HOME='A:\AI\runtimes\android-sdk'
$env:GRADLE_USER_HOME='A:\AI\cache\gradle'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am start -W -n com.henry.chord/.MainActivity
```

**Build passed. 42 JVM tests passed, zero failures/errors. Lint: 0 errors, 17 warnings** (dependency/KTX recommendations). See [build output](build.log), [JUnit XML](unit-results/) and [lint output](lint.txt). APK: `app/build/outputs/apk/debug/app-debug.apk`; final SHA-256 and size are in [APK metadata](apk-metadata.txt). The APK is generated locally and ignored by Git, not a signed store release.

## Executed Android tests

**25 distinct functional Android test methods passed across the suites below**, plus a separate owner-preference/history restoration check. Repeated regression runs are not counted as extra tests. Tests ran inside the real installed native app with AndroidJUnitRunner, Room and Compose/UIAutomator. Direct instrumentation was deliberately used instead of Gradle connected-test cleanup, which can uninstall the owner's app. No owner library was cleared. Backup Replace was exercised only in isolated test databases; the real-picker test used Merge.

| Test classes | Passed | Actual coverage / evidence |
|---|---:|---|
| `ReleaseEngineTest`, `Phase2EngineTest`, `Phase4SearchTest` | 14 | [Engine log](engine-device.log): migrations, backup empty restore/repeat merge/replace/corruption/traversal, concurrent import/restore artwork, editor anchors, large saved-state snapshots, 320-song batch/cancel/resume, artwork paths, Unicode, duplicate protection, 5,007-row indexed corpus |
| `AppTest`, `Phase3InteractionTest` | 3 | [Regression log](regressions-device.log): navigation, original demos, persistence, Roman/key/transpose, piano/guitar and unknown-key editing |
| `Phase4LibraryTest` | 1 | [Library log](library-device.log): title/artist/lyrics/tag/album/filter/sort, artwork picker/fallback/repair, intent interception, favorites, scroll restoration, light/dark/landscape |
| `Phase4ColdStartTest` | 1 | [Separate process log](cold-start-device.log): force-stop/relaunch, retained favorite/artwork/theme, large text |
| `ReleasePerformanceTest` | 1 | [Performance log](performance-device.log): actual Android frame clock, speed/pause/resume/manual drag/end stop, sheet/background pause, +2 transpose and Roman/Both, sounding D visualizations, fullscreen/back/Space/awake flag, landscape/150% font, modes and saved speed |
| `ReleaseLargeLibraryTest` | 1 | [Large-library log](large-library-device.log): 1,331 songs in actual UI (1,000 newly generated original fixtures), Wi-Fi/mobile data disabled, title/artist/lyric/tag/album search, scroll, favorite, all theme choices |
| `ReleaseEditorTest` | 1 | [Editor log](editor-device.log): manual multiline song, insert/move midword anchors, save/reload/key/BPM/time signature, unsaved recreation/discard, repeated setlist entries/drag/overrides/next/previous, real SAF ZIP save/preview/Merge/settings restore and Replace acknowledgement gate |
| `ReleasePickerTest` | 1 | [Picker log](picker-device.log): real DocumentsUI single JSON, two ChordPro files, recursive folder/relative cover, ZIP, malformed/error/duplicate/traversal; source folder renamed unavailable and imported song/artwork reopened offline |
| `ReleaseEmptyStateTest` | 1 | [Empty-state log](empty-state-device.log): actual native empty/no-results UI backed by an isolated in-memory database |
| `ReleaseColdTapTest` | 1 | [Startup log](startup-device.log): ten Activity creations with immediate Library taps after startup race fix |

[Final APK regression run](final-regressions-device.log) repeats the startup and three core reader tests after the final export/startup changes. Full feature suites were run before these small final fixes; they were not all rerun after the last patch. Screenshots can therefore differ from the final APK only in these fixes, not the palette/layout.

Exact runner invocation pattern (the script fails on missing JUnit success, not merely ADB exit status):

```powershell
.\tools\run_device_suite.ps1 -Adb 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe' -Classes 'com.henry.chord.ReleaseEngineTest,com.henry.chord.Phase2EngineTest,com.henry.chord.Phase4SearchTest' -Output qa-artifacts/engine-device.log
.\tools\run_device_suite.ps1 -Adb 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe' -Classes 'com.henry.chord.AppTest,com.henry.chord.Phase3InteractionTest' -Output qa-artifacts/regressions-device.log
.\tools\run_device_suite.ps1 -Classes com.henry.chord.Phase4LibraryTest -Output qa-artifacts/library-device.log
adb shell am force-stop com.henry.chord
.\tools\run_device_suite.ps1 -Classes com.henry.chord.Phase4ColdStartTest -Output qa-artifacts/cold-start-device.log
.\tools\run_device_suite.ps1 -Classes com.henry.chord.ReleasePerformanceTest -Output qa-artifacts/performance-device.log
.\tools\run_device_suite.ps1 -Classes com.henry.chord.ReleaseLargeLibraryTest -Output qa-artifacts/large-library-device.log
.\tools\run_device_suite.ps1 -Classes com.henry.chord.ReleaseEditorTest -Output qa-artifacts/editor-device.log
.\tools\run_device_suite.ps1 -Classes com.henry.chord.ReleasePickerTest -Output qa-artifacts/picker-device.log
.\tools\run_device_suite.ps1 -Classes 'com.henry.chord.ReleaseColdTapTest,com.henry.chord.ReleaseEmptyStateTest' -Output qa-artifacts/startup-device.log
```

The first two commands use the explicit installed ADB path; the rest assume that executable is on `PATH`. See README for fixture generation/pushing and the picker selector/previous-backup requirements. SAF tests rely on the API-30 DocumentsUI labels. Original test fixture source files are in [picker-fixtures](picker-fixtures/). The full exported archive contains the real library and is kept private, not committed as test evidence.

## Acceptance checklist: every requested item audited

| Acceptance criterion | Result and limits |
|---|---|
| Native Kotlin/Compose, existing emulator, Pixel 9 design | **Pass with coverage limit:** installed/launched native APK; Pixel-9-like viewport only, actual hardware/new Android untested |
| Offline existing library/import/search/reader/favorites/setlists | **Pass:** radios disabled in large-library/picker tests; no Internet permission |
| Room safe migrations, DataStore persistence | **Pass:** migration tests and owner-data comparison, process relaunch and preferences/backup journal |
| Light/dark/system, list/grid, real/fallback thumbnails | **Pass for list:** polished compact paged list, manual/imported/fallback art and themes; optional grid not implemented |
| Search title/artist/album/tag/lyric and large library | **Pass functionally:** 5,007-row search and 1,331-song UI; performance measurements below |
| Favorites/history/setlists, reorder/next/previous | **Pass:** persistence, occurrence identity, drag/arrows and overrides; no drag edge-auto-scroll |
| Genuine stored Spotify/YouTube links or labeled Search | **Pass for intent behavior:** validated user-provided URLs, intercepted outgoing intents; identity/availability of online music not certified |
| Ordered verse/prechorus/chorus/bridge/intro/solo/outro/custom | **Pass:** canonical parser/editor and reader tests |
| Chords over exact syllables at different widths/font scales | **Pass in tested fixtures:** measured Unicode offsets, midword/adjacent/long labels; collision lanes may add height |
| Original/current keys; non-destructive roots/slash transpose | **Pass:** unit/native tests; original documents compared unchanged |
| Symbols/Roman/Both, C–G–F → I–V–IV | **Pass:** theory and native reader assertions/screenshots |
| Major/minor/non-diatonic/invalid/missing-key behavior | **Pass:** explicit scale-degree convention and unknown-key action; no claimed secondary-dominant inference |
| +2 actual D–A–G while Roman remains I–V–IV | **Pass:** native performance/theory tests and sounding D sheet |
| Sounding piano keyboard / valid guitar fingering | **Pass for supported chords:** real native visuals; unavailable guitar voicings are explicitly labeled |
| Auto-scroll speed, pause/resume/manual pause/fullscreen/awake | **Functional pass; rendering quality open:** real-clock positions differ with speed, pause/end/lifecycle assertions; jank below |
| Single/multiple/folder/ZIP, art, preview/errors/duplicates | **Pass:** actual SAF picker flows plus engine/security tests |
| Managed offline imports, no unrestricted storage permission | **Pass:** source renamed unavailable then reopen; app-private managed content |
| Metadata/lyrics/chords/anchor editing save/reopen | **Pass:** native cursor operations, multiline split, preview/undo/discard and Room reload |
| Versioned full ZIP preserving versions/images/user state | **Pass:** isolated empty restore/merge/replace and real SAF export/Merge; owner DB never replaced for testing |
| Empty/no-art/no-key/unsupported-chord/bad-file states | **Pass in exercised states:** native fallbacks and actionable validation |
| Gradle/unit/instrumented tests | **Pass for executed tasks/suites:** 42 JVM and 25 distinct Android functional methods; not every historical picker suite rerun |
| Actual emulator screenshots | **Pass:** 76 PNGs covering requested screens, including the final preserved-library capture |
| Build/install/test/model/import documentation | **Pass:** README, schema, phase format guides and this report |

## Measured performance, defects and fixes

The 5,007-row indexed corpus returned measured query results in **3–28 ms**; [search timings](search-timings.json). The real 1,331-song UI lyric-query-to-visible check took **1,054 ms**, including the 160 ms debounce and test/UI interaction; it is not an SQL-only measurement. See [large-library results](large-library-results.json).

[Actual scroll positions](scroll-results.json) demonstrate slow motion `(1,13)→(1,44)` and faster motion `(1,44)→(3,49)` (LazyColumn item index, pixel offset). Manual drag, background, chord sheet and end-stop assertions passed. These use real Android frame timing, not Compose test-clock advancement.

**P2 — rendering performance remains open.** [Gfxinfo](reader-gfxinfo.txt) recorded **799 frames, 708 janky (88.61%), median 28 ms, p95 150 ms** during automated reading/dialog/rotation/gesture work on the old software-rendered API-30 emulator. This is not a controlled physical-device benchmark, but it prevents claiming smooth rendering is accepted. Profile and optimize on a hardware-accelerated recent AVD and physical Pixel 9 before release. [Memory snapshot](reader-meminfo.txt) is diagnostic evidence, not a long-duration leak certification.

One real cold-start crash was discovered late: a navigation tap could run before NavHost attached its graph. Navigation is now gated on startup readiness/current destination; ten immediate navigation attempts and the final regressions passed. Earlier dynamic accessibility/chord callbacks, unsaved Binder-sized editor state, multiline anchor splitting, concurrent managed-artwork cleanup and original-minor-key export were also corrected. See [final logcat summary](logcat-summary.txt) for the post-fix result; do not interpret it as a claim that the entire development session never crashed.

**P2 — device/accessibility coverage remains open.** No physical Pixel 9, recent-API emulator, tablet hardware or live TalkBack service was available. Tested native semantics, announced labels, large text, keyboard/back and touch behavior do not substitute for a live screen-reader audit. Target SDK 36 builds successfully, but API-35/36 runtime changes still need device validation.

**P3 — bounded scope:** no grid, drag edge-auto-scroll, comprehensive guitar dictionary, audio preview/metronome or Bluetooth pedal mapping. Dense adjacent chord labels use extra vertical lanes. Undo is session-local; unsaved cache drafts can be evicted. ChordPro is a text interchange subset; full identity/metadata/images/personal-state fidelity uses native JSON/ZIP. Android process recreation is tested; uninterrupted scroll motion across process death deliberately resumes paused. A release signing/store pipeline is not included.

## Data preservation and evidence

Private before/after copies were compared without publishing lyrics, notes or raw databases. **331 original arrangements remain 331**; canonical documents, UUIDs/fingerprints, catalog, favorites/notes, offsets, setlist names/order/references and artwork bytes match. Original history and preference choices were restored after tests. SQLite integrity is `ok`, foreign-key violations **0**, search/index counts **331**. [Machine-readable check](data-preservation.json), generated by `tools/check_release_preservation.py`. Viewport/font changes were restored too. Test-generated songs were removed; original AVD/library data was never cleared.

All screenshots below are actual emulator captures. Earlier phase evidence remains under `docs/`; current `release-*` captures show the new palettes and functionality.

| Screen | Evidence |
|---|---|
| New home palettes | [Light](screenshots/release-33-home-light.png), [Dark](screenshots/release-34-home-dark.png) |
| Large library | [Light](screenshots/release-28-library-light-1000-offline.png), [Dark](screenshots/release-31-library-dark-1000-offline.png) |
| Lyric search | [Highlighted offline result](screenshots/release-29-lyric-search-offline.png) |
| Reader labels | [Symbols](screenshots/release-01-reader-symbols.png), [Roman](screenshots/release-02-reader-roman.png), [Both](screenshots/release-05-reader-both.png) |
| Chord detail | [Piano sounding D](screenshots/release-03-roman-tap-piano-D.png), [Guitar D](screenshots/release-04-guitar-D.png) |
| Performance | [Playing](screenshots/release-07-fullscreen-playing.png), [Landscape](screenshots/release-08-landscape-reader.png), [150% text](screenshots/release-09-large-text-reader.png) |
| Editor | [Anchor editor](screenshots/release-12-line-anchor-editor-light.png), [Light](screenshots/release-13-editor-light.png), [Dark](screenshots/release-20-editor-dark.png) |
| Setlists/settings | [Setlist light](screenshots/release-14-setlist-light.png), [Dark](screenshots/release-19-setlist-dark.png), [Settings dark](screenshots/release-15-settings-dark.png) |
| Imports | [Relative artwork preview](screenshots/release-22-folder-artwork-preview.png), [Folder summary](screenshots/release-23-folder-import-summary.png), [Malformed report](screenshots/release-24-malformed-file-report.png), [Traversal](screenshots/release-26-path-defense.png) |
| Backup | [SAF save](screenshots/release-16-native-backup-save-picker.png), [Merge preview](screenshots/release-17-backup-merge-preview.png), [Replace acknowledgement](screenshots/release-17b-backup-replace-warning.png) |
| Final preserved installation | [Library after cleanup/relaunch](screenshots/final-preserved-library.png) |

## Final phase check

| Phase | Implementation/audit outcome |
|---|---|
| 1 — native foundation | Implemented, reviewed, emulator launches and upgrade retained |
| 2 — model/bulk import | Implemented; unit/engine/real picker, offline art and safety tested |
| 3 — theory/chord interaction | Implemented; unit/native labels/transpose/piano/guitar tested |
| 4 — library/search/art | Implemented; indexed corpus, large real UI and persistence tested |
| 5 — performance view | Implemented and functionally exercised; **jank/performance acceptance pending** |
| 6 — editor/setlists/settings/backup | Implemented; native CRUD/SAF and isolated full restore tested |
| 7 — emulator QA/hardening | Executed on existing AVD; fixes/evidence recorded; **modern-device/TalkBack coverage pending** |
| Final acceptance audit | Every checklist item reviewed; runnable RC delivered; **not an unconditional production sign-off** |

Pixel 9 setup: enable USB debugging, authorize the computer, select the phone in Android Studio and Run `app`, or use `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk` then launch `com.henry.chord/.MainActivity`. The same signing key is required for upgrades. Data stays in the app sandbox: `databases/chord.db`, `files/artwork`, `files/thumbnails` and `files/datastore`; full backups go to the SAF location chosen by the user. See README for the complete commands and import/schema guides.
