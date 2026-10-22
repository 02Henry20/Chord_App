# Chord

An offline Android songbook for practice and performance, written in Kotlin with Jetpack Compose and Material 3.

## Included

- Home with a resume-playing card, recent songs, and a session shortcut.
- Library with Room FTS4 search across titles, artists, and lyrics; favorites; alphabetical/recent sorting.
- Song creation, editing, deletion, personal notes, and optional external source links.
- Reader with chord-over-lyric layout, section headings, transposition (including slash bass), capo, relative Roman numerals, text sizing, fullscreen, keep-awake, and adjustable auto-scroll.
- Guitar diagrams for common stored voicings and piano chord-tone diagrams. Unavailable guitar voicings are identified explicitly.
- Named setlists with song selection, reordering, removal, renaming, deletion, and previous/next song controls.
- Android document-picker import of ChordPro/text files, ZIPs, and nested folders. Exact duplicate songs are skipped. Individual song export uses the system document picker.
- Persistent light/dark appearance, preferred instrument, reader text size, Roman notation, and keep-awake settings using DataStore.

Six **original demo songs** are included on first launch. No account, backend, paid API, Internet permission, or runtime storage permission is required. File access uses Android's Storage Access Framework.

## Build and install

Requirements: JDK 17, Android SDK platform 36, and an existing Android device/emulator running API 26 or newer.

Set `ANDROID_HOME` to your SDK directory, or add your own ignored `local.properties` with `sdk.dir`. The Gradle wrapper is included.

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.henry.chord/.MainActivity
```

## Tests

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

Local tests cover transposition, Roman degrees, piano tones, ChordPro round trips, invalid input, and safe FTS queries. Device tests cover Room/FTS synchronization, file/ZIP imports, duplicate handling, saved song properties, setlist cascade deletion, and the app's principal Compose workflows. The UI test creates and removes its own song and setlist.

The UI test writes actual full-device screenshots to the app's private `files/screenshots` directory. Retrieve them from a debug installation:

```powershell
adb exec-out run-as com.henry.chord tar -cf - -C files screenshots > docs\screenshots.tar
tar -xf docs\screenshots.tar -C docs
```

PowerShell 7.4+ preserves native binary output for this redirect. Screenshots are checked into [docs/screenshots](docs/screenshots).

## Song format

```text
{title: My original song}
{artist: Your name}
{key: C}

{comment:Verse}
[C]Put a chord before the [G]lyric it belongs to
[Am]Write the next line [F]here
```

Supported suffixes: `.cho`, `.chopro`, `.chordpro`, `.pro`, `.txt`. Text must be UTF-8. Plain lyrics are accepted; inline bracket notation enables chord features. Imports are bounded to 100 songs, 16 MB total, 2 MB per song, and eight folder levels. ZIPs are read in memory without extracting paths to the filesystem.

Capo is independent of transposition: the reader shows the chord shapes to play and identifies the sounding key in the capo controls. Roman degrees are measured relative to the selected tonic. Source links open in the user's browser.

## Current boundaries

- This is a local songbook. Cloud sync, audio playback, recording, web scraping, and paid song catalogs are outside this implementation.
- Common ChordPro section directives are supported; advanced tablature grids and arbitrary ChordPro extensions are not rendered.
- Guitar voicings are a curated subset. Piano diagrams cover common major/minor, suspended, diminished, augmented, sixth, seventh, and ninth chord tones; they are not a complete music-theory engine.
- Export currently covers individual song text, not a complete database/settings/setlist backup.
- Release signing, Play Store packaging, and testing across additional Android versions remain release work.

See [device validation](docs/VALIDATION.md) for the machine-specific emulator setup, evidence, and remaining verification limits.
