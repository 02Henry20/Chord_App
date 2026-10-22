# Music theory and chord interaction

Phase 3 is native Kotlin/Compose, entirely offline. Room remains at schema 2. App version 3 upgrades the installed app using `adb install -r`; no reset or destructive migration is used.

## Labels and key context

Settings → Chord labels offers **Chord symbols** (default), **Roman numerals**, and **Both**. The old Roman boolean preference migrates to the corresponding choice when the new preference is absent. The reader's bottom label button cycles modes for the current reader; it does not change the Settings default. Returning to Settings and choosing a default resets that local override. Instrument changes in chord details persist as the instrument setting.

Roman labels are **root scale degrees with chord quality**, not inferred functional analysis. Secondary dominants, tonicizations and modulations are not inferred. Each arrangement has one explicit tonal center and major/minor mode. A missing key or explicitly unknown mode shows **Key required · Set key / mode**, retains actual symbols, and offers an immediate key editor. The key editor updates metadata, preserving chord anchors and personal offsets.

The natural major/minor scales provide the diatonic baseline. Letter names determine degree; accidentals express deviation from that scale. Thus Bb in C major is bVII, whereas A# is #VI. In A minor, Am–Dm–E is i–iv–V; G#dim is #vii° against the natural-minor baseline. Raised minor-mode leading tones are explicit. Chord third quality controls case, including borrowed major/minor chords.

Slash numbers are **bass scale degrees**, not figured bass and not secondary-dominant notation: C/E in C major is I/3; G7/B is V7/7; D/F# in C major is II/#4. Diminished uses °, half-diminished seventh uses ø7, augmented uses +. Extensions/suspensions/additions are retained. b/# are readable ASCII accidentals; `Theory.roman(..., ascii = true)` supplies `dim`/`hdim` fallbacks for clients without those glyphs. Android's bundled text font supports the displayed symbols.

Explicit `tonalMode: null` now survives JSON encode/decode. Legacy JSON omitting that property retains Phase 2's key-suffix convention (Am means minor; C means major). Both `originalKey: "A", tonalMode: "minor"` and `"Am"/"minor"` work. An m-suffixed key cannot explicitly claim major mode. Content edits preserve an existing mode unless the key changes; the key editor always lets the user select the mode.

## Transposition and spelling

Canonical chord text is immutable during transposition. Offsets remain in `arrangement_user_state`, separate from content; favorites, notes, last played and setlist references are unchanged. The +/- buttons change sounding pitch one semitone. More → Reset original key resets the offset and capo aid. Per-setlist-entry transpose overrides are not implemented.

Current concert key = original tonal center + saved transpose. Both root and slash bass move. Chords are respelled with the same diatonic relationship to the selected current key, then Roman labels are calculated **in that current key**. C–G–F becomes D–A–G while I–V–IV remains invariant. Both mode reads D (I), A (V), G (IV).

Auto spelling uses common major/minor tonics. Settings can request sharp or flat tonic spellings. Diatonic chord relationships take priority over respelling every note to one accidental type; unusual keys/borrowed harmonies may need double or, for extreme forced spellings, multiple accidentals. Such spellings remain parseable. Original spelling is retained at zero transpose in Auto.

Capo is now explicitly a **guitar shape aid**. It does not change the concert key or piano pitches. A D chord with capo 2 offers C shapes; diagrams/fret numbers are relative to the capo. Existing capo values are retained. This makes the concert key, labels and actual sounding visualization consistent; the earlier reader described transposed symbols as shapes and added capo to its separate sounding-key line.

## Parsing and visualizations

`Theory.kt` contains pure Kotlin note spelling, chord parsing, pitch transposition, scale-degree mapping and display formatting. `Voicings.kt` contains deterministic piano suggestions and validated guitar shapes. `ChordDetails.kt` renders native Compose visuals. No JavaScript, WebView, native music library, network access or audio service is required.

The parser recognizes major/minor, dominant/major/minor sevenths, diminished/half-diminished, augmented, sus2/sus4, add, 6/9/11/13 extensions, b/# alterations, slash bass, Unicode accidentals and NC/N.C. Parenthesized alterations must be balanced. Unknown annotations remain raw, with an editor warning and no fabricated diagram or Roman label. The parser's conservative grammar intentionally does not interpret arbitrary ChordPro annotations, polychords or functional notation as chord symbols.

Tapping any chord label opens a Material 3 dialog with its actual sounding symbol, key context, spelled constituent notes and persistent instrument selection.

- Piano: actual chromatic keyboard covering at least two octaves, correctly placed black keys; green indicates chord pitch classes in the range, amber indicates the specific suggested MIDI notes. Written octave labels account for enharmonics (Cb4 = B3, B#3 = C4). Root/first/second inversions are provided for ordinary triads/sevenths; slash chords keep their prescribed bass. Extended chords offer a basic close shell and all tones, with omissions explicitly named. Suggestions describe pitch order, not a required fingering or hand assignment.
- Guitar: six-string EADGBE diagrams show muted/open strings, starting fret and actual fretted notes. Curated open shapes and movable E/A barre families offer alternatives where available. Every candidate must contain exactly the chord's pitch-class set, the specified lowest bass, at least three sounding strings, and a bounded fret span. Complex extensions without a supported playable template explicitly show unavailable. These are chord fingerings, not original-recording TAB.

Audio preview and a complete guitar-voicing catalog remain optional future work. See [actual verification evidence](phase3-evidence/RESULTS.md) for executed tests and device screenshots.
