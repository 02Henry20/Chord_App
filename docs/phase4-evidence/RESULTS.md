# Phase 4 — actual validation, 22 October 2026

The existing **Pixel_4_API_30** AVD (`emulator-5554`, Android 11/API 30) was used, temporarily configured to **1080×2424 at 420 dpi**, approximately 411×923 dp, for Pixel 9-like portrait layout. Landscape and **150% system text** were also checked. This was not a physical Pixel 9 or an Android 12+ dynamic-color test. Original emulator display/text settings were restored afterward.

The installed app was upgraded with `adb install -r`; it was never uninstalled or cleared. It is now **4.0-phase4**, versionCode **4**, Room schema **3**.

## Results

| Check | Actual result |
|---|---|
| JVM tests | **31 passed**: 12 canonical/parser, 8 theory, 7 existing helpers, 4 library/links/Unicode |
| Gradle app/test APK builds | **Passed** |
| Android lint | **0 errors, 16 warnings**: dependency/tool-update and KTX style suggestions |
| Phase4LibraryTest | **1 passed**, 46.537 s |
| Phase4ColdStartTest, separate invocation after force-stop | **1 passed**, 8.769 s |
| AppTest + Phase3InteractionTest | **3 passed**, 47.391 s |
| Phase4SearchTest + Phase2EngineTest | **10 passed**, 99.704 s |
| Captured logcat | **0 fatal exceptions, 0 app ANRs, 0 SQLite exceptions** |
| Original library | **331 → 331 arrangements**, canonical JSON/fingerprints unchanged |
| Saved data | Favorites, notes, transposition/capo, setlists/entries and managed artwork unchanged |
| SQLite checks | **integrity: ok; foreign-key violations: 0** |
| Derived index after upgrade/cleanup | 331 metadata rows, 331 FTS rows, **0 orphan FTS rows** |

The 15 Android tests ran through direct ADB instrumentation so Gradle's connected-test cleanup could not remove the installed app or library. Temporary test songs, setlist, image fixtures and benchmark database were removed. Regression tests opening existing demo songs can advance their recent-view timestamps. The original Guitar preference was restored; new installations default to Piano.

## Real UI checks

The UI suite searched by **River Trio** (artist), **ening lant** (interior title fragment), **fireflies shimmer** (unique lyric phrase with actual highlighted AnnotatedString ranges), **cafe sessions** (accent-normalized album), and **日本** (Unicode tag). It tested no results, sorting/filter application, favorites, and saved query context. A list position at row 75 survived entering and returning from the reader.

It selected a real generated PNG through Android DocumentsUI, using a content URI, saved it into managed artwork storage, and verified the changed thumbnail. Imported artwork appeared in library/search, Favorites, setlists and reader. Missing and corrupt artwork produced generated fallbacks. The reader header showed album, year, original key and BPM; fullscreen hid the header. Portrait/dark/landscape/large-text screenshots were visually inspected.

Spotify/YouTube ACTION_VIEW intents were intercepted by an Android `ActivityMonitor`, with syntactically valid **fake** track/video IDs. No external app or service was called. The tests checked approved normalized URLs, the browsable intent category, and separately labeled search actions when links were absent. Unit tests reject dangerous schemes, spoofed hosts, userinfo, invalid ports, malformed identities and duplicate YouTube video parameters.

The app was then force-stopped and started by a separate instrumentation invocation. The favorite, changed artwork and dark theme remained available. That invocation also tested 150% system text and restored the original text scale before cleaning up its fixtures.

## On-disk search benchmark

The final test used a separate **on-disk Room database containing 5,007 songs**: 5,000 synthetic original songs with roughly 216-word lyric fixtures, plus seven ranking fixtures. It tested exact/prefix/contains-title ranking, artist, album/tag and lyric matches; 1/2-character and Unicode fragments; filters; favorites/recents; updates; history clearing; deletion; lightweight result projections; bounded excerpts; and both migrations. The database was removed afterward.

| Query | Observed query time |
|---|---:|
| firefly, ranking fixtures | 69 ms |
| IREFL, interior fragment | 5 ms |
| cafe, accent-normalized album | 35 ms |
| 日本, tag fragment | 29 ms |
| an / a | 23 / 24 ms |
| unique lyric phrase with song number | 17 ms |
| empty query | 19 ms |
| no results | 4 ms |
| edited title / edited lyric | 3 / 21 ms |

These are individual measured Room-query durations in this test, not percentile or cold-start guarantees. Interactive typing also includes the intentional 160 ms debounce and rendering. See [raw timing data](search-timings.json).

## Actual commands

PowerShell from `E:\Henry_Luu\Programming\Chord_App`, using the installed JDK 17, SDK 36 and cached Gradle dependencies:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --console=plain
$adb = 'A:\AI\runtimes\android-sdk\platform-tools\adb.exe'
& $adb install -r app/build/outputs/apk/debug/app-debug.apk
& $adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb shell wm size 1080x2424
& $adb shell wm density 420
& $adb shell am instrument -w -e class com.henry.chord.Phase4LibraryTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am force-stop com.henry.chord
& $adb shell am instrument -w -e class com.henry.chord.Phase4ColdStartTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.henry.chord.AppTest,com.henry.chord.Phase3InteractionTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb shell am instrument -w -e class com.henry.chord.Phase4SearchTest,com.henry.chord.Phase2EngineTest com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner
& $adb logcat -d
& $adb shell wm size reset
& $adb shell wm density reset
& $adb shell am start -n com.henry.chord/.MainActivity
& 'A:\AI\runtimes\conda\python.exe' tools/check_phase3_upgrade.py .local-backup/phase4/before .local-backup/phase4/after
```

Screenshots were taken using Android UiAutomation after frames settled, retrieved with `adb exec-out run-as ... tar`, and inspected. The final installed APK SHA-256 is in [logcat-check.json](logcat-check.json). Private app-data backups and raw device logs remain ignored; only test output and aggregate preservation checks are committed.

## Evidence

- [Build](build.log), [lint](lint.txt), [JVM library tests](TEST-com.henry.chord.LibraryTest.xml)
- [Library UI](library-device.log), [cold restart](cold-device.log), [UI regressions](ui-regressions.log), [search/import regressions](data-regressions.log)
- [Preserved data](data-preservation.json), [derived indexes](derived-index-check.json), [logcat/APK checks](logcat-check.json)
- [Light library](screenshots/01-library-light.png), [artist search](screenshots/02-artist-search.png), [title fragment](screenshots/03-title-fragment.png), [lyric highlight](screenshots/04-lyric-highlight.png)
- [Unicode tag](screenshots/05-unicode-tag.png), [filters](screenshots/05b-filters.png), [no results](screenshots/06-no-results.png), [favorites](screenshots/07-favorites.png)
- [Reader metadata](screenshots/08-reader-header.png), [provided links](screenshots/09-provided-links.png), [native image picker](screenshots/10-native-artwork-picker.png), [changed artwork](screenshots/11-manual-artwork.png)
- [Performance mode](screenshots/12-performance-mode.png), [artwork fallback](screenshots/13-missing-artwork-fallback.png), [search links](screenshots/14-search-links.png), [setlist art](screenshots/15-setlist-artwork.png)
- [Dark library](screenshots/16-library-dark.png), [landscape library/rail](screenshots/17-landscape-library.png), [landscape reader](screenshots/18-landscape-reader.png)
- [Cold-start favorites](screenshots/19-cold-favorites.png), [150% library text](screenshots/20-large-text-library.png), [150% reader text](screenshots/21-large-text-reader.png), [preserved 331-song library](screenshots/22-preserved-library.png)

Early validation found an Android FTS grammar compatibility issue and picker selectors that assumed visible filename text in image-grid mode. Both were fixed before the final successful runs. Visual review led to the compact landscape header/navigation rail. The committed screenshots and logs represent the passing implementation; failed-run screenshots are excluded.

Optional grid view and online enrichment remain outside this phase. Android 12+ dynamic colors and physical Pixel 9 behavior require additional device coverage. No mandatory Phase 4 work is left blocked.
