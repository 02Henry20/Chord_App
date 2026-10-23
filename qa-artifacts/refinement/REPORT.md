# Chordroom rc2 interface and import audit

Executed 23 October 2026. Package **com.henry.chord**, activity **com.henry.chord/.MainActivity**, version **1.0.0-rc2 (8)**. Native Kotlin/Compose/Room; no WebView, Internet permission or broad storage permission. Room schema remains 4, preferences use DataStore. APK: `app/build/outputs/apk/debug/app-debug.apk`; see [APK identity](apk-metadata.txt).

This report supersedes the removed UI described in the [historical rc1 audit](../REPORT.md). It records executed work, not a test plan or unconditional production sign-off.

## Requested changes: final check

| Request | Implemented and checked |
|---|---|
| 1: status-bar overlap | Root safe-drawing insets, including reader/performance; native bounds assertion and final cold-launch screenshot |
| 2: remove sessions/setlists | Navigation, creation, controls and seed setlist removed. Existing legacy database/backup records preserved exactly |
| 3: collapsed Settings | Eight categories, initially collapsed, one open at a time; expanded state survives recreation |
| 4/4.1: Pick up & play | Random song on entering/resuming Home; upper-right Shuffle chooses another when possible; label stays Open song |
| 4: key clutter | Key badges removed from home/library/favorites/search; original/current key retained inside song |
| 5/9: filler | Requested descriptive filler removed |
| 6: import controls | 12 dp separation between file/ZIP and folder buttons; native bounds checked |
| 7/23: reader simplification | Always chords and lyrics; no display-mode controls, return/jump/share/JSON-export overflow. Single ChordPro export remains |
| 8: live examples | Typography, spacing, colors, spelling and label previews; instrument example and working scroll-speed example |
| 10: actions | Write a song / Import songs use 16 sp semibold text |
| 12/13: sections | Consistent type-colored headings and tinted groups, including blank/chord-only instrumental lines |
| 14/16: reader/key controls | Piano has no capo control; set-key/mode shortcut and concert-key explanation removed. Metadata remains editable in editor |
| 15/22: centralized preferences | Instrument and Symbols/Roman/Both configured in Settings, with no reader/dialog switch |
| 17/19: piano | One full root-position chord, including extensions and slash bass; selected octave notes only, no inversions/shells/green legend |
| 18: import format | [Detailed .txt guide](../../docs/SONG-IMPORT-GUIDE.txt), copyable tested JSON; strict syntax/schema/type/chord/art validation before staging and saving, actionable per-file errors |
| 20: guitar | One validated fingering or unavailable state; alternate-voicing controls and tuning/legend prose removed |
| 21: links | Spotify and YouTube native vector marks alongside verified stored links or explicitly labeled searches |
| New icon | Owner's exact supplied purple PNG used for adaptive launcher/round icon, plus monochrome themed resource; installed Android icon inspected and source hash tested |
| Light/dark colors | Colorful theme/accent strip retained; section shades, text contrast and live Settings checked in both themes |

No original arrangement, artwork, favorite, note, preference or legacy setlist was deleted. The compatibility tables are intentionally retained without active setlist UI.

## Actual environment and build

Existing **Pixel_4_API_30**, Android 11/API 30 x86, `sdk_gphone_x86`, headless SwiftShader. Physical screen 1080×2280 / 440 dpi; most portrait tests used 1080×2424 / 420 dpi (Pixel 9-like logical width), plus landscape and 150% system text. Overrides reset to physical configuration and text scale 1.0 afterward. Boot completion was checked as `1`; no AVD wipe or owner-data clear.

Build SDK: `A:\AI\runtimes\android-sdk` platform 36; JDK `A:\AI\runtimes\jdk\microsoft-17.0.20`; Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21. Existing emulator/system images live under `C:\Android\sdk` and AVDs under `C:\Android\.android\avd`.

Executed commands, with PowerShell environment matching this machine:

```powershell
$env:JAVA_HOME='A:\AI\runtimes\jdk\microsoft-17.0.20'
$env:ANDROID_HOME='A:\AI\runtimes\android-sdk'
$env:GRADLE_USER_HOME='A:\AI\cache\gradle'
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon '-Pkotlin.compiler.execution.strategy=in-process' --console=plain

& 'C:\Android\sdk\emulator\emulator.exe' -list-avds
# Existing AVD was booted without loading/saving snapshots or clearing data:
$env:ANDROID_AVD_HOME='C:\Android\.android\avd'
$env:ANDROID_HOME='C:\Android\sdk'
$env:ANDROID_SDK_ROOT='C:\Android\sdk'
Start-Process -WindowStyle Hidden -FilePath 'C:\Android\sdk\emulator\emulator.exe' -ArgumentList '-avd','Pixel_4_API_30','-no-window','-no-snapshot-load','-no-snapshot-save','-no-audio','-gpu','swiftshader_indirect','-no-boot-anim','-skin','1080x2280'

$adb='A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
$env:ADB_VENDOR_KEYS='C:\Android\.android\adbkey'
& $adb -P 5038 start-server
& $adb -P 5038 connect 127.0.0.1:5555
& $adb -P 5038 devices -l
& $adb -P 5038 -s 127.0.0.1:5555 wait-for-device
& $adb -P 5038 -s 127.0.0.1:5555 shell getprop sys.boot_completed
& $adb -P 5038 -s 127.0.0.1:5555 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -P 5038 -s 127.0.0.1:5555 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -P 5038 -s 127.0.0.1:5555 shell am start -W -n com.henry.chord/.MainActivity
```

The machine's older ADB repeatedly occupied port 5037; a dedicated modern ADB server on 5038 reused the existing emulator through localhost, with its existing trusted key. Initial boot attempts using the build SDK root and an unavailable decorative skin failed; the installed emulator SDK root and numeric skin resolved them. A Windows Kotlin/Gradle JAR lock was resolved by stopping our build daemons and using in-process Kotlin with `--no-daemon`. No owner files or AVDs were removed.

## Tests actually passed

Build: **PASS**, [log](build.log). JVM: **52 tests, 0 failures/errors/skips**, [JUnit XML](unit-results). Lint: **0 errors, 13 warnings**, [report](lint.txt). These warnings remain recorded; a successful build does not mean a warning-free release.

**29 distinct functional Android tests passed** on the running emulator. Direct instrumentation was used to avoid Gradle device-test uninstall cleanup. Every public run below ends in the actual JUnit `OK` summary; exit code alone was not considered sufficient. Tests for removed setlist/inversion/display-switch UI were replaced by current coverage rather than reported as passing unchanged.

| Log | Count | Exercised |
|---|---:|---|
| [engine](engine-device.log) | 18 | Schema migrations, stable IDs, Room import, Unicode, 320-candidate batch, repeat import, cancellation/recovery, ZIP traversal/size defense, artwork, full backup into empty isolated database, merge/replace safety, strict validation, installed icon |
| [new UI](ui-device.log) | 3 | Insets, Home shuffle/label, import gap, accordion/live examples/persistence, colored/instrumental sections, full C13 piano, Roman +2 with persisted D, one guitar diagram, service icons, fullscreen |
| [performance](performance-device.log) | 1 | Real-time slow/fast motion, pause/resume, manual/chord/background pause, end stop, Space, awake, landscape and 150% text, Symbols/Roman/Both |
| [editor and backup](editor-backup-device.log) | 1 | Multiline anchors/cursor, save/reload, original-key edit, unsaved state, favorites, real SAF export and restore preview/Merge, Replace acknowledgement |
| [system pickers](picker-device.log) | 1 | JSON, multiple ChordPro, nested folder/art, ZIP, malformed/duplicates/traversal, offline copy after source becomes unavailable |
| [large library](large-library-device.log) | 1 | 1,000 original generated fixtures plus 331 existing arrangements; real offline library/search/scroll/favorite and Light/Dark/System |
| [startup/empty](startup-empty-device.log) | 2 | Ten rapid launch/navigation attempts, isolated empty-library state |
| [library](library-device.log) | 1 | Title/artist/lyrics/tag/album, excerpts, filters, paging/scroll restoration, favorite, thumbnail fallback/repair/real picker, intercepted verified/search intents, landscape/themes |
| [cold process](cold-device.log) | 1 | Separate run after force-stop: favorite, thumbnail, theme persistence and large text |

For the runs above, the exact runner template was:

```powershell
.\tools\run_device_suite.ps1 -Adb $adb -Serial 127.0.0.1:5555 -ServerPort 5038 -Classes $classes -Output $log
```

Executed `$classes` / `$log` pairs under `qa-artifacts/refinement/`:

```text
com.henry.chord.RefinementImportTest,com.henry.chord.ReleaseEngineTest,com.henry.chord.Phase2EngineTest,com.henry.chord.Phase4SearchTest,com.henry.chord.AppTest -> engine-device.log
com.henry.chord.RefinementUiTest -> ui-device.log
com.henry.chord.ReleasePerformanceTest -> performance-device.log
com.henry.chord.ReleaseEditorTest -> editor-backup-device.log
com.henry.chord.ReleasePickerTest -> picker-device.log
com.henry.chord.ReleaseLargeLibraryTest -> large-library-device.log
com.henry.chord.ReleaseColdTapTest,com.henry.chord.ReleaseEmptyStateTest -> startup-empty-device.log
com.henry.chord.Phase4LibraryTest -> library-device.log
com.henry.chord.Phase4ColdStartTest -> cold-device.log
```

Cold-start command preceded its run: `& $adb -P 5038 -s 127.0.0.1:5555 shell am force-stop com.henry.chord`. An additional **one maintenance test**, [restoration](restoration-device.log), restored this installation's private pre-test preferences/history with explicit `restoreOwnerSnapshot=true`; it is not counted in the 29 functional tests. Raw owner snapshots and backup ZIPs are private and excluded from Git.

Failures found and fixed before these final passing runs: Android ICU rejected an unescaped closing brace in the strict ChordPro directive regex; rapid transpose taps lost a step through stale persistence; Home's resume/load race could temporarily omit the random card. Current native tests cover all three. Outdated selectors were updated for the new collapsed categories and removed controls.

## Observed behavior and reliability

* [Scroll evidence](scroll-results.json): slow `(1,13)→(1,43)` and fast `(1,43)→(2,273)` in item-index/pixel-offset coordinates. Actual Android frame time drove motion. Manual drag, background, chord sheet and end-stop assertions passed.
* [Search timings](search-timings.json): 3–31 ms for queries in a 5,007-record indexed test corpus. The real 1,331-song UI showed its unique lyric result after **795 ms**, including debounce/UI, with Wi-Fi and mobile data disabled; [result](large-library-results.json). These are single-run observations, not benchmark guarantees.
* [Gfxinfo](reader-gfxinfo.txt): **817 frames, 312 janky (38.19%), median 12 ms, p95 85 ms** during automated gestures/dialogs/rotation on SwiftShader. **P2 remains open:** this is substantial jank and does not meet a smooth-performance release sign-off. Profile on a hardware-accelerated recent AVD and physical Pixel 9. [Memory snapshot](reader-meminfo.txt) is diagnostic, not a leak certification.
* [Logcat summary](logcat-summary.txt): captured QA log contains **0 fatal exceptions**, **0 app ANR markers**; crash buffer empty. Earlier failed assertions/caught import errors are not represented as passing tests. Raw logs are kept private to avoid exposing library content.
* Final manual cold launch returned Android `Status: ok`, `LaunchState: COLD`, 2,583 ms reported total time. Library showed **331 songs**, [final capture](screenshots/17-preserved-library-final.png), with headers below system icons.

## Preservation

[Before/after check](data-preservation.json): **331 → 331 arrangements**, canonical documents/fingerprints/UUIDs/catalog exact, favorites/notes/key offsets exact, managed-artwork bytes exact, restored history/preferences exact. Legacy setlist IDs/order/occurrence notes/overrides/full records exact. SQLite integrity `ok`, no foreign-key violations, metadata index and FTS both 331. [Import journal check](import-journal-preservation.json): original two staged-import directories restored byte-for-byte. Test-created records removed; no uninstall, app-data clear or AVD wipe.

## Emulator screenshots

All **56 PNGs** in [screenshots](screenshots) are actual emulator captures, not mockups. Notable views:

| View | Captures |
|---|---|
| Supplied installed icon | [Android App Info](screenshots/16-installed-icon.png) |
| Home / import gap | [Light](screenshots/01-home-light.png), [Dark](screenshots/03-home-dark.png), [Import](screenshots/02-import-gap.png) |
| Accordion / live Settings | [Collapsed light](screenshots/04-settings-collapsed-light.png), [Large text](screenshots/05-settings-large-live-example.png), [Spelling](screenshots/06-settings-flat-preview.png), [Roman](screenshots/07-settings-roman-preview.png), [Dark](screenshots/08-settings-dark.png) |
| Colored reader | [Light](screenshots/09-reader-section-colors-light.png), [Dark](screenshots/14-reader-section-colors-dark.png) |
| Chord detail / links | [Full C13 piano](screenshots/10-full-piano-chord.png), [Roman actual D](screenshots/11-roman-sounding-root-D.png), [Guitar](screenshots/12-single-guitar-diagram.png), [Service icons](screenshots/13-service-icons.png) |
| Label modes | [Symbols](screenshots/performance-01-reader-symbols.png), [Roman](screenshots/performance-02-reader-roman.png), [Both](screenshots/performance-05-reader-both.png) |
| Performance | [Playing](screenshots/performance-07-fullscreen-playing.png), [Landscape](screenshots/performance-08-landscape-reader.png), [Large text](screenshots/performance-09-large-text-reader.png) |
| Library / search | [Light offline](screenshots/large-library-28-library-light-1000-offline.png), [Dark offline](screenshots/large-library-31-library-dark-1000-offline.png), [Lyric excerpt](screenshots/large-library-29-lyric-search-offline.png) |
| Import | [Folder/art preview](screenshots/import-22-folder-artwork-preview.png), [Summary](screenshots/import-23-folder-import-summary.png), [Syntax error](screenshots/import-24-malformed-file-report.png), [Traversal](screenshots/import-26-path-defense.png) |
| Editor / backup | [Anchor editor](screenshots/editor-12-line-anchor-editor-light.png), [Dark editor](screenshots/editor-20-editor-dark.png), [SAF export](screenshots/editor-16-native-backup-save-picker.png), [Restore preview](screenshots/editor-17-backup-merge-preview.png) |

No new setlist screenshot is expected: that functionality was removed at the user's request. Historical screenshots remain in the rc1 report.

## Remaining limitations and final phase check

**P2:** physical Pixel 9, API 35/36 runtime behavior, modern launcher themed-icon rendering and live TalkBack were unavailable. Native semantics/large-text/keyboard checks and target-36 compilation do not replace those device audits. The only installed compatible AVD was API 30. Rendering jank remains open as reported above.

**P3:** ChordPro is a documented subset and strict new imports reject unknown/invalid fields and chords with an error; older stored library remains readable. Full identity/images/personal-state fidelity uses native JSON/full ZIP, not ChordPro. Piano intentionally has one full root-position display and guitar one validated fingering; exhaustive guitar voicings/audio/metronome/pedals/grid remain out of scope. Undo is session-local; draft cache may be evicted. No release-store signing pipeline is included.

| Phase | End check |
|---|---|
| 1: native foundation | Reviewed, builds, installs and cold-launches on existing emulator |
| 2: data/import | Revalidated, strict syntax strengthened, real SAF and 320-candidate batch tested |
| 3: theory/chords | Unit/native checks passed; requested simplified root piano/guitar/Settings behavior applied |
| 4: library/search/art | Indexed 5,007-record engine and 1,331-song offline UI tested, persistence/fallback/intents checked |
| 5: performance | Functional tests passed; **rendering-performance sign-off pending** |
| 6: editor/settings/backup | Native save/reload/anchors/live Settings/SAF and isolated restore passed; **setlist UI intentionally removed** |
| 7: emulator QA | Executed, issues fixed, screenshots/logs/data proof saved; **modern-device/live-accessibility coverage pending** |
| Final acceptance audit | All checklist areas reviewed against current requested scope. Current changes implemented and verified; **not all production acceptance criteria can honestly be marked complete** because of the remaining P2 items |

## Install on Pixel 9 / another machine

Copy the built APK to any machine with ADB; Java/Gradle are unnecessary when installing an already-built APK. Enable phone USB debugging, authorize that machine, then `adb devices -l`, `adb -s <serial> install -r "C:\path\app-debug.apk"`. Launch `adb -s <serial> shell am start -n com.henry.chord/.MainActivity`. Upgrades require the same signing key; do not uninstall to work around a signature mismatch if you need the existing library. Export a full ZIP backup first. Android Studio can also build/run the app with JDK 17 and SDK 36.

Private data: `databases/chord.db`, `files/artwork`, `files/thumbnails`, `files/datastore` in the app sandbox; exports go to the SAF location you select. See [README](../../README.md) for complete setup and [song import guide](../../docs/SONG-IMPORT-GUIDE.txt).
