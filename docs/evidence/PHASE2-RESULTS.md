# Phase 2 validation — 22 October 2026

The native application was built, installed over the existing app **without uninstalling or clearing data**, and exercised on the owner's existing `Pixel_4_API_30` emulator (`emulator-5554`, Android 11/API 30, 1080×2280, density 440). No emulator was created, replaced or wiped.

## Results

| Check | Actual result | Evidence |
| --- | --- | --- |
| Upgrade existing Room v1 library | Six legacy records, every stored field and setlist order/reference preserved; zero foreign-key violations | [migration.json](migration.json) |
| JVM tests | **19 passed**: 12 canonical/parser/security tests + 7 original music tests | [JUnit XML](tests/TEST-com.henry.chord.CanonicalTest.xml), [music XML](tests/TEST-com.henry.chord.MusicTest.xml) |
| Android import/migration engine | **7 passed** | [engine output](engine-output.txt) |
| Original app regression suite | **2 passed** alongside the 7 engine tests | [regression output](regression-output.txt) |
| Actual system-picker workflow | **1 passed**; multiple files, recursive folder, 320-song ZIP, TXT review, duplicate comparison, repeat import, activity recreation | [picker output](pickers-output.txt), [counts](picker-report.json) |
| Sources unavailable + cold start | **1 passed**; managed original image, thumbnail, exact lyrics and reader verified in a new app process | [offline output](offline-output.txt), [assertions](offline-report.json) |
| Build and lint | Successful build; **0 lint errors, 15 warnings** | [build output](build-output.txt), [lint output](lint-output.txt) |

That is **19 JVM tests and 11 distinct device tests**, all passing on the final implementation. The combined engine/regression run reports nine tests; the full picker and source-unavailable cold-start tests add one each. The engine verifies that library lists contain summaries rather than full documents, and that play/favorite actions leave full sheet fingerprints unchanged. Export/re-import of two intentionally identical versions preserves both IDs, and a repeated archive import adds zero rows.

The real picker workflow began with six existing arrangements and ended with **331**: two selected files, two arrangements from a recursively selected folder, 320 from a nested ZIP, and one explicitly reviewed TXT. Repeating the ZIP added **zero** and reported **320 skipped**. The two intentionally invalid files in the ZIP were reported independently; valid files were still imported. Known fixture records from earlier test attempts were cleaned up only by their original test identities; legacy songs were retained.

The engine suite covers v1→v2 migration validation against exported Room schemas; 320-candidate persistence and repeat import; replacement retaining IDs/favorites/notes/offsets/setlist positions; explicit new versions and journal replay after a simulated database/journal crash gap; cancellation/resumption; mismatched IDs and within-pack duplicates; ZIP traversal/oversized-entry rejection; Unicode; copied artwork and 256×256 thumbnails; content export/re-import, including distinct identical versions.

Lint warnings concern available dependency/build-tool upgrades and Kotlin convenience extensions. Deprecated icon API/compiler notices are non-blocking. No warning was suppressed to obtain the result.

## Exact commands

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
$adb = 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
& $adb -s emulator-5554 install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
& $adb -s emulator-5554 push docs\demo-packs /sdcard/Documents/ChordroomPhase2
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2EngineTest,com.henry.chord.AppTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2PickerTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2EngineTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
```

For the source-unavailable test, the known generated source directory and unused destination were checked first. Only that fixture directory was renamed, the app was force-stopped, then the test launched a new activity/process. The directory was restored afterward:

```powershell
& $adb -s emulator-5554 shell mv /sdcard/Documents/ChordroomPhase2 /sdcard/Documents/ChordroomPhase2-source-hidden
& $adb -s emulator-5554 shell am force-stop com.henry.chord
& $adb -s emulator-5554 shell am instrument -w -e class com.henry.chord.Phase2OfflineReopenTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s emulator-5554 shell mv /sdcard/Documents/ChordroomPhase2-source-hidden /sdcard/Documents/ChordroomPhase2
```

The private migration baseline was checked using `tools/check_migration.py` against pre/post-upgrade SQLite copies. Only boolean/count evidence is committed; original app databases and backups are ignored.

## Screenshots

Sixteen actual full-device PNGs, plus UIAutomator hierarchies where captured, are in [screenshots](screenshots). No mockups or composited screens were used.

- [Multiple selection in Android DocumentsUI](screenshots/01-system-multiple-selection.png)
- [Recursive folder selection](screenshots/03-system-folder-selection.png)
- [Artwork and anchor preview](screenshots/05-artwork-and-anchors-preview.png)
- [320-arrangement preview](screenshots/06-bulk-320-preview.png)
- [Per-file errors](screenshots/07-per-file-errors.png)
- [Import summary](screenshots/08-bulk-summary.png)
- [Repeat summary: 320 skipped](screenshots/09b-repeat-summary.png)
- [Duplicate comparison](screenshots/09c-duplicate-comparison.png)
- [Explicit TXT position review](screenshots/10-txt-explicit-review.png)
- [Reader after sources were unavailable and a cold start](screenshots/13-cold-start-source-unavailable.png)
- [Managed artwork in the offline library](screenshots/14-offline-library-artwork.png)

The test writes these under private `files/phase2-evidence`. Retrieve using PowerShell 7.4+ binary-preserving redirection:

```powershell
& $adb -s emulator-5554 exec-out run-as com.henry.chord tar -cf - -C files phase2-evidence > phase2-evidence.tar
tar -xf phase2-evidence.tar
```

## Artifact and remaining boundaries

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `AB275198F80D1C55AA78E19445C740BA87EF2BEBC632FFF9250FABD6DDE2AD7D`.

The tested APK remains installed. The demo source folder was restored and the imported content remains in private storage. Portable collection export/re-import is implemented; full backup/restore of personal state, settings and setlists remains the later backup phase. Discovery interrupted before a complete staging journal needs source selection again; a completed staging/import session can resume without source files.

Additional Android versions, third-party/cloud document providers, physical devices, accessibility/font-scale combinations, and release signing/Play Store deployment were not claimed or completed in this phase. Advanced ChordPro constructs are retained as annotations rather than fully interpreted. No requested phase-2 action is represented by a placeholder control.
