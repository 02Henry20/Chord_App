# Chordroom rc3 verification

Work and verification: 23–24 October 2026. Native Kotlin/Compose/Room, package **com.henry.chord**, activity **com.henry.chord/.MainActivity**, version **1.0.0-rc3 (9)**. Room schema stays **4**. The existing purple launcher artwork is retained. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`; [identity/hash](apk-metadata.json).

## Requested changes implemented

| Request | Result and evidence |
|---|---|
| Long-press library selection | Long-press enters selection, row/checkbox toggles, Back/Close exits. Select all queries **all matching IDs**, including unloaded pages. Favorite, Unfavorite and confirmed transactional Delete are working. Search/filter changes clear selection; recreation retains it. Keyboard hides on entering selection. [Light](screenshots/01-multi-select-light.png), [Dark](screenshots/01b-multi-select-dark.png), [Delete confirmation](screenshots/02-delete-confirmation.png) |
| Remove More and all capo behavior | More/capo controls removed in both instruments. Reset sits directly beside transpose. Guitar diagrams use the sounding chord even when a legacy stored capo is nonzero. Legacy values remain inert in the database/backup, preserving data |
| Compact chord-sheet spacing | Chords sit close above the lyrics, without reserving 48 dp of visual height for every chord. Native clickable text retains Compose touch-target expansion. Settings offer side margins, chord/lyric gap, source/wrapped-row gap and section gap, plus existing font size/line height. [Default sheet](screenshots/reader-09-reader-section-colors-light.png), [Spacing preview](screenshots/05-spacing-preview.png) |
| Correct alignment after wrapping | Native UI assertions compare each chord's horizontal position to Android's actual shaped lyric glyph position at multiple widths/fonts. Exact Unicode/whitespace, mid-word and adjacent anchors survive reflow. Width-cache invalidation is tested; labels update correctly when callback state changes |
| Home thumbnail and random collection | Pick up & play displays its managed artwork/fallback. Entering/resuming Home and Shuffle refresh both the random song and four random collection rows. Shuffle changes the card and first collection row when possible. [Artwork and shuffled collection](screenshots/08-home-thumbnail-shuffled.png) |
| Five icon Settings categories | Appearance, Song reader, Chords & instrument, Performance, Library & backup. Only one opens at once. [Collapsed light](screenshots/04-five-settings-light.png), [Dark](screenshots/06-five-settings-dark.png) |
| Import headline spacing | The two-line headline has explicit **39 sp line height** at 29 sp type; file/folder button spacing is retained. Existing staged imports were preserved, not discarded to obtain a welcome-screen screenshot |
| Continuous speed adjustment | Slider follows the finger; applied speed and persistence are sampled every **500 ms**. The active monotonic frame loop reads the latest speed without restarting. Native assertions verify motion continues while adjusting, plus slow/fast, manual pause, chord/background pause and end stop |
| Fullscreen delta | Upper-left controls show signed semitone delta alongside title/current sounding key. [Delta](screenshots/09-compact-fullscreen-delta.png), [Playing](screenshots/flow-07-fullscreen-playing.png) |

Favorites apply to the song across its versions. Delete removes selected arrangements; other versions remain, and shared personal state is removed only after the last version is deleted. SQL updates use bounded batches rather than exceeding SQLite bind limits. Unreferenced managed files can be reclaimed by the existing safe import cleanup. There is no undo for deletion; confirmation explains this and recommends a backup.

Four additive optional fields in version-1 backup settings preserve reader layout: `sheetMargin` 4–40 dp (default 16), `songLineGap` 0–24 dp (6), `chordLineGap` 0–12 dp (2), `sectionGap` 0–32 dp (12). Old backups receive defaults; invalid values fail validation. See [format documentation](../../docs/PERFORMANCE-EDITOR-BACKUP.md).

## Environment and commands actually used

Reused the existing **Pixel_4_API_30**, Android 11/API 30 x86, `sdk_gphone_x86`, headless SwiftShader. Physical **1080×2280, 440 dpi**; landscape and 150% system text were also exercised. Font scale returned to 1.0 and normal orientation afterward. No AVD wipe, app uninstall or owner-data clear.

Installed AVDs were listed with `C:\Android\sdk\emulator\emulator.exe -list-avds`; available compatible phone images were API 30. The same AVD was booted with:

```powershell
$env:ANDROID_AVD_HOME='C:\Android\.android\avd'
$env:ANDROID_HOME='C:\Android\sdk'
$env:ANDROID_SDK_ROOT='C:\Android\sdk'
Start-Process -WindowStyle Hidden -FilePath 'C:\Android\sdk\emulator\emulator.exe' -ArgumentList '-avd','Pixel_4_API_30','-no-window','-no-snapshot-load','-no-snapshot-save','-no-audio','-gpu','swiftshader_indirect','-no-boot-anim','-skin','1080x2280'
```

The machine has conflicting ADB versions; the modern isolated server reused the existing trusted key and running emulator. Initial unauthorized connection was resolved by restarting **only server 5038** with that key. Boot completion was checked as `1`.

```powershell
$adb='A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
$env:ADB_VENDOR_KEYS='C:\Android\.android\adbkey'
& $adb -P 5038 start-server
& $adb -P 5038 connect 127.0.0.1:5555
& $adb -P 5038 devices -l
& $adb -P 5038 -s 127.0.0.1:5555 shell getprop sys.boot_completed

$env:JAVA_HOME='A:\AI\runtimes\jdk\microsoft-17.0.20'
$env:ANDROID_HOME='A:\AI\runtimes\android-sdk'
$env:GRADLE_USER_HOME='A:\AI\cache\gradle'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
& $adb -P 5038 -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -P 5038 -s 127.0.0.1:5555 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Direct instrumentation avoids Gradle connected-test cleanup uninstalling an owner's installation. Each suite was executed using:

```powershell
.\tools\run_device_suite.ps1 -Adb $adb -Serial 127.0.0.1:5555 -ServerPort 5038 -Classes $classes -Output $log
```

Executed class lists and outputs:

```text
com.henry.chord.LibrarySelectionTest,com.henry.chord.AnchoredWrapUiTest,com.henry.chord.RefinementUiTest#accordionLiveTypographySpellingAndLabelPreferences,com.henry.chord.RefinementUiTest#combinedSheetSectionsRootOnlyDiagramsAndServiceIcons -> qa-artifacts/rc3/ui-device.log
com.henry.chord.ReleasePerformanceTest,com.henry.chord.ReleaseEditorTest,com.henry.chord.ReleaseEngineTest,com.henry.chord.RefinementImportTest,com.henry.chord.Phase4SearchTest,com.henry.chord.Phase4LibraryTest -> qa-artifacts/rc3/regression-device.log
com.henry.chord.Phase4ColdStartTest,com.henry.chord.ReleaseLargeLibraryTest -> qa-artifacts/rc3/cold-large-device.log
com.henry.chord.ReaderFrameTest -> qa-artifacts/rc3/frame-device.log (extra argument: measurementLabel=rc3)
com.henry.chord.LibrarySelectionTest -> qa-artifacts/rc3/final-selection-device.log
```

`adb ... shell am force-stop com.henry.chord` preceded the cold-start suite. Frame sampling ran without a concurrent Gradle build. The final six-case selection/Home/Settings rerun followed the last library-only keyboard/icon-spacing polish; unchanged reader/engine regressions were already passing.

## Actual results

| Check | Result |
|---|---|
| Build/debug APK/test APK | **PASS**, [final build log](build.log) |
| JVM tests | **55 tests, 0 failures/errors/skips**, [JUnit XML](unit-results) |
| Lint | **0 errors, 13 warnings**, [report](lint.txt) |
| Native targeted UI | **9 passed**, [log](ui-device.log): 105 matching rows selected across the initial 100-row page, confirmation/cancel/delete, favorites, recreation, 1,005-record isolated bulk operations, five categories, atomic preferences, Home/artwork/shuffle, legacy capo inertness, full chord/label/preview and pixel-alignment checks |
| Native regressions | **13 passed**, [log](regression-device.log): timed scrolling/live speed/large text/landscape; native editor and real SAF backup export/Merge; isolated full restore/migrations; strict imports and icon; 5,007-record indexed corpus; real library, art fallback/repair and intercepted music intents |
| Cold process and real large library | **2 passed**, [log](cold-large-device.log): favorite/art/theme after restart, then 1,000 temporary original fixtures plus 331 existing arrangements with radios disabled |
| Controlled frame recording | **1 passed**, [log](frame-device.log): actual display frames recorded, not a smoothness acceptance test |
| Final affected rerun | **6 passed**, [log](final-selection-device.log), same six distinct tests already included above |

**25 distinct Android tests passed**; the final six were rerun after polish rather than counted as additional distinct coverage. This does not mean every historical phase test was rerun. Real recursive-folder/multiple-file/ZIP picker coverage from [rc2](../refinement/REPORT.md) remains historical; this iteration reran strict importer/backup/picker-related editor flows and changed only import headline typography.

Issues found and corrected during implementation: a new test initially selected its search field instead of the result; a wrap-cache label optimization hid state read through a stable callback; rapid full-preference saves could overwrite a previous margin adjustment; a Home artwork test needed the unmerged card semantics. Targeted regressions now cover the actual cache/preference defects. A missing test import was corrected before the final successful build. Initial failed attempts were not represented as passing runs.

## Performance observations and remaining limitations

[Scroll positions](scroll-results.json): slow `(1,16)→(1,49)`, fast `(1,49)→(4,55)` in list-index/pixel-offset coordinates. The test also changed speed **while playing** and asserted that both the playing state and forward motion continued.

[Indexed query observations](search-timings.json): 3–86 ms in a 5,007-record corpus. The [real 1,331-song UI](large-library-results.json) displayed its unique lyric result after **696 ms**, including debounce/rendering, with Wi-Fi and mobile data disabled. These are single-run observations, not guarantees.

**P2 — frame pacing remains open.** [Mixed-workload gfxinfo](reader-gfxinfo.txt) reports 1,076 frames, 571 janky (53.07%), median 17 ms and p95 57 ms. It includes gestures, rotation, dialogs and accessibility inspection. Separate [sustained-scroll samples](frame-samples.json), two 8-second intervals at 60 dp/s, recorded 499/497 frames, medians 14.45/14.36 ms and p95 24.07/23.91 ms; 235/231 exceeded 16.67 ms. This threshold count differs from Android's jank metric. Native layout medians were 0.097/0.062 ms. No causal percentage improvement is claimed: software-emulator scheduling is variable, and earlier experimental samples were not a reliable before/after benchmark. A recent hardware-accelerated AVD/physical Pixel 9 profile is still required. The memory snapshot is diagnostic, not a leak certification.

The reader profile preceded the final library-only keyboard/icon-padding change; reader code is identical. [APK metadata](apk-metadata.json) distinguishes the profiled build from the final installed one.

**P2 — coverage remains open:** no physical Pixel 9, recent Android runtime, live TalkBack service or modern themed-launcher runtime was tested. Native semantics, keyboard/back, large text and actual API-30 execution do not substitute for those audits. Compact chords use native touch-target expansion; exceptionally dense adjacent changes can require vertical lanes. No release signing/Play Store pipeline is included.

**P3 — bounded scope:** bulk actions are select/favorite/unfavorite/delete, not bulk metadata editing or selected-only export. Delete has confirmation but no undo. The documented ChordPro subset and existing backup limits remain. Additional instruments, audio, pedals, grid layout and a comprehensive guitar dictionary are outside this update.

## Data and screenshots

[Preservation check](data-preservation.json): **331 → 331**, canonical documents/UUIDs/fingerprints, catalog, favorite/notes, transpose offsets, artwork bytes, restored recent history/preferences and all legacy setlist occurrence details match. SQLite integrity `ok`, zero foreign-key violations, metadata/FTS counts both 331. [Original staged imports](import-journal-preservation.json) remain byte-for-byte exact in their two directories. Raw databases, logs containing owner data, keys and exported full-library ZIPs are excluded from Git.

[Captured logcat](logcat-summary.json): **0 fatal-exception markers, 0 app-ANR markers, empty crash buffer** in the saved QA buffer; not a lifetime reliability claim.

Final manual cold launch returned Android `Status: ok`, `LaunchState: COLD`, total 2,078 ms to the Activity's reported launch completion; [launch output](final-launch.txt). The fully loaded [Home](screenshots/37-final-home.png) and [331-song library](screenshots/38-final-preserved-library.png) were inspected afterward. Installed APK bytes exactly match the final build hash. Device configuration ended at physical 1080×2280 / 440 dpi, portrait and font scale 1.0. The app is left on Library.

All images in [screenshots](screenshots) are actual emulator captures. Notable additional evidence:

* [Roman](screenshots/flow-02-reader-roman.png), [Both](screenshots/flow-05-reader-both.png), [tapped Roman sounding D](screenshots/flow-03-roman-tap-piano-D.png).
* [Landscape](screenshots/flow-08-landscape-reader.png), [150% text](screenshots/flow-09-large-text-reader.png), [dark reader](screenshots/reader-14-reader-section-colors-dark.png).
* [Light library](screenshots/flow-28-library-light-1000-offline.png), [dark library](screenshots/flow-31-library-dark-1000-offline.png), [lyric result](screenshots/flow-29-lyric-search-offline.png).
* [Editor](screenshots/flow-12-line-anchor-editor-light.png), [dark editor](screenshots/flow-20-editor-dark.png), [real SAF backup save](screenshots/flow-16-native-backup-save-picker.png), [restore preview](screenshots/flow-17-backup-merge-preview.png).

## Phase/audit end check

| Phase | Current outcome |
|---|---|
| 1 — native foundation | Builds/installs/launches on the existing Android emulator |
| 2 — model/import | Existing model and journal data preserved; strict import regression passed. Earlier recursive SAF picker evidence linked above |
| 3 — theory/chords | Unit and current native symbols/Roman/Both, transpose and piano/guitar checks passed |
| 4 — library/search/art | New multi-select and random Home implemented; indexed corpus, 1,331-song offline UI, persistence and thumbnails tested |
| 5 — performance | Compact adjustable layout and exact wrapped anchors tested; continuous speed behavior passed; **frame-pacing sign-off pending** |
| 6 — editor/settings/backup | Five categories, atomic preferences, CRUD and versioned full restore passed; setlist UI intentionally removed per earlier request |
| 7 — emulator QA | Actual AVD/build/install/launch/tests/screenshots/logcat/data checks executed; **recent-device/live-accessibility coverage pending** |
| Final acceptance | Requested rc3 changes implemented and verified. **Not all production acceptance criteria can honestly be marked complete** while the P2 limitations above remain |

Installation on a physical phone: enable USB debugging, authorize the computer, then `adb devices -l`, `adb -s <serial> install -r "C:\path\app-debug.apk"`, and `adb -s <serial> shell am start -n com.henry.chord/.MainActivity`. A copied built APK needs no Java/Gradle on the installing machine. Preserve the signing key for upgrades; export a backup rather than uninstalling if signatures differ. Android Studio can build/run with JDK 17 and SDK 36. Data remains private under the package sandbox: `databases/chord.db`, `files/artwork`, `files/thumbnails`, `files/datastore`; SAF exports go to the location the user chooses. See [README](../../README.md) and [detailed import .txt](../../docs/SONG-IMPORT-GUIDE.txt).
