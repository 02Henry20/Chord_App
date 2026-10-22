# Phase 3 validation — 22 October 2026

Implemented and tested as a native offline Android update. The existing **Pixel_4_API_30** AVD (`emulator-5554`, Android 11/API 30, 1080×2280) was used. The installed app was upgraded with `install -r`, never uninstalled or cleared. Room stays at schema 2.

## Executed checks

| Check | Actual result |
|---|---|
| `testDebugUnitTest` | **27 passed**: 8 theory, 7 existing music/import helpers, 12 canonical/parser tests |
| `assembleDebug` | **Passed**, APK produced |
| `assembleDebugAndroidTest` | **Passed**, test APK produced |
| `lintDebug` | **Passed**, 0 errors, 15 warnings (dependency/tool updates and style suggestions) |
| `Phase3InteractionTest` on existing AVD | **1 passed**, 27.782 seconds |
| `AppTest` + `Phase2EngineTest` on existing AVD | **9 passed** |
| App-data comparison before/after | **331 → 331** arrangements; content, artwork and personal data retained |
| SQLite integrity / foreign keys | **ok / 0 violations** |
| Cold force-stop/relaunch | **Passed**, library shows 331 songs |

The device interaction test verifies all three persistent Settings label modes, quick reader cycling without modifying the default, instrument persistence, C→D transposition persistence across Activity recreation, and unchanged canonical JSON. A tapped Roman I opens actual **D**, with **D4 F#4 A4**. Both shows **D (I), A (V), G (IV)**. Slash I/3 opens D/F# with a low F#3. An explicitly unknown mode retains Am/Dm/E and the key-required action; setting A minor gives i/iv/V, and tapping V opens E/G#/B. Piano inversion and alternative guitar diagrams are exercised. Capo 2 offers C guitar shapes for concert D while piano remains D/F#/A. The editor preserves and warns about `Cweird!`.

Unit coverage includes C/G major, A minor, borrowed/non-diatonic and enharmonic spellings, diminished/half-diminished/augmented qualities, altered and extended chords, Unicode accidentals, slash bass, NC, malformed/unknown chords, explicit unknown-mode JSON round trips, preference migration, accurate written octave labels, close-position spacing and added-ninth inversion basses. Degree invariance is checked for 49 transpose offsets × 3 spelling preferences × 10 chord types. Guitar suggestions are checked against their actual sounding pitch sets and lowest bass.

The nine regression tests cover the full original UI session, Room 1→2 migration, real persistence/search, a 320-candidate batch, duplicate/replacement/version semantics, safe cancellation/resumption, ZIP defenses and artwork collection round trips. The Phase 2 DocumentsUI picker suite was not rerun in Phase 3; its earlier evidence remains in `docs/evidence/PHASE2-RESULTS.md`.

## Commands used

PowerShell, workspace `E:\Henry_Luu\Programming\Chord_App`; installed JDK 17, SDK 36 and existing Gradle cache. All builds used `--offline`.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain

$adb = 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb logcat -c
& $adb shell am instrument -w -e class com.henry.chord.Phase3InteractionTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.henry.chord.AppTest,com.henry.chord.Phase2EngineTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb logcat -d
& $adb shell am force-stop com.henry.chord
& $adb shell am start -n com.henry.chord/.MainActivity

# Compare private, ignored app-data copies; output contains counts/booleans only.
& 'A:\AI\runtimes\conda\python.exe' tools/check_phase3_upgrade.py .local-backup/phase3/before .local-backup/phase3/after
```

App-private screenshots were captured with Android `UiAutomation.takeScreenshot()` after Compose/SurfaceFlinger settled, retrieved using `adb exec-out run-as ... tar`, and visually inspected. The screenshots below are real emulator output, not mockups. Raw app-data backups remain ignored in `.local-backup/phase3/`; no private database/lyrics backup is committed. The regression test's instrument change was restored to the original Guitar preference.

## Evidence

- [Build log](build.log), [JUnit theory XML](TEST-com.henry.chord.TheoryTest.xml), [lint report](lint.txt)
- [Phase 3 device test](interactions.log), [regression device tests](regressions.log), [logcat inspection](logcat-check.json)
- [Data preservation comparison](data-preservation.json)
- [Symbols](screenshots/01-symbols.png), [Roman](screenshots/07-roman.png), [Both](screenshots/10-both.png)
- [Piano root](screenshots/02-piano-root.png), [first inversion](screenshots/03-piano-inversion.png), [Roman tap → sounding D](screenshots/09-roman-tap-sounding-D.png)
- [Guitar](screenshots/04-guitar.png), [alternative fingering](screenshots/05-guitar-alternative.png), [capo 2 / concert D](screenshots/15-capo-shape-concert-D.png)
- [Settings](screenshots/06-settings-roman.png), [saved transposition](screenshots/08-roman-transposed.png), [slash bass](screenshots/11-slash-bass.png)
- [Unknown mode](screenshots/12-key-required.png), [A minor degrees](screenshots/13-minor-key.png), [minor dominant](screenshots/14-minor-dominant.png), [editor warning](screenshots/16-editor-warning.png)
- [Cold-start library, 331 songs](screenshots/17-cold-library.png)

Early verification caught an extreme enharmonic spelling outside the parser's initial accidental limit, plus ambiguous/stale search selectors in the new UI test. Those were corrected before the successful runs. Review also corrected half-diminished extensions above 7, close-position spacing and added-ninth inversion labeling. The committed logs and screenshots represent the final passing implementation.

## Limits

Verified on the owner's existing API 30 emulator, not on additional physical devices/API levels. The app provides scale-degree labels, not automatic functional analysis. Unknown chords remain editable raw text. Guitar coverage uses validated curated/movable families and explicitly reports unavailable diagrams for unsupported fingerings. Audio preview and per-setlist-entry transpose overrides are optional future work. No production permission or network dependency was added.
