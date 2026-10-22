package com.henry.chord

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Uses the real installed app. Adds/removes only its own fixtures and restores preferences. */
class Phase3InteractionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun await(text: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(450); rule.waitForIdle(); Thread.sleep(150)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = File(rule.activity.filesDir, "phase3-evidence").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun open(title: String) {
        rule.onNodeWithText("Library").performClick(); await("Your library")
        rule.onNode(hasSetTextAction()).performTextReplacement(title)
        rule.waitUntil(15_000) { rule.onAllNodes(hasText(title) and !hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText(title) and !hasSetTextAction()).performClick()
    }
    private fun choose(mode: LabelMode) {
        rule.onNodeWithText("Settings").performClick(); await("Make it yours.")
        rule.onNodeWithTag("label-${mode.name}").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { Settings(rule.activity).flow.first().chordLabels == mode } }
    }

    @Test fun realReaderModesVoicingsKeyEditAndPersistence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, ChordDatabase::class.java, "chord.db").addMigrations(MIGRATION_1_2).build()
        val dao = database.dao()
        val prefs = runBlocking { Settings(context).flow.first() }
        val count = runBlocking { dao.count() }
        val ids = mutableListOf<Long>()
        val title = "Harmony study"
        val missingTitle = "Minor study"
        try {
            runBlocking {
                Settings(context).save(prefs.copy(chordLabels = LabelMode.SYMBOLS, instrument = "Piano", spelling = Spelling.AUTO, dark = false))
                ids += dao.insert(Song(title = title, artist = "Original theory fixture", key = "C", body = "{comment:Verse}\n[C]Morning [G]music [F]rises\n[Am7]Keep it [C/E]close\n[Bbmaj7]Borrowed [Bdim]colors\n[F#m7b5]Listen [G7sus4]again\n[Cweird!]An unsupported annotation"))
                val doc = SheetParser.parse("{title:$missingTitle}\n[Am]Quiet [Dm]night [E]shines", "unknown.cho").document
                ids += dao.store(doc.copy(arrangements = listOf(doc.arrangements.single().copy(originalKey = "A", tonalMode = null))))
            }
            rule.activityRule.scenario.recreate(); await("Make room\nfor music.")
            open(title); await("KEY C"); shot("01-symbols")
            rule.onNodeWithContentDescription("Chord labels: Chord symbols. Change for this reader").performClick()
            rule.onAllNodesWithText("I").onFirst().assertExists()
            rule.onNodeWithContentDescription("Chord labels: Roman numerals. Change for this reader").performClick()
            rule.onAllNodesWithText("C (I)").onFirst().assertExists()
            rule.onNodeWithContentDescription("Chord labels: Both. Change for this reader").performClick()
            assertEquals(LabelMode.SYMBOLS, runBlocking { Settings(context).flow.first().chordLabels })
            val canonicalBefore = runBlocking { dao.song(ids[0])!!.documentJson }
            rule.onAllNodesWithText("C").onFirst().performClick()
            rule.onNodeWithTag("actual-chord").assertTextEquals("C")
            rule.onNodeWithTag("voiced-notes").assertTextEquals("C4  E4  G4")
            rule.onNodeWithTag("piano-keyboard").assertExists(); shot("02-piano-root")
            rule.onNodeWithText("1st inversion").performClick()
            rule.onNodeWithTag("voiced-notes").assertTextEquals("E4  G4  C5"); shot("03-piano-inversion")
            rule.onNodeWithText("Guitar").performClick()
            rule.onNodeWithTag("guitar-fretboard").assertExists(); shot("04-guitar")
            rule.onNodeWithText("Voicing 2").performClick(); shot("05-guitar-alternative")
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithContentDescription("Back").performClick()
            choose(LabelMode.ROMAN); shot("06-settings-roman")
            rule.activityRule.scenario.recreate(); await("Make it yours.")
            rule.waitUntil(5_000) { runBlocking { Settings(context).flow.first().instrument == "Guitar" } }
            open(title); await("KEY C")
            rule.onAllNodesWithText("I").onFirst().assertExists(); shot("07-roman")
            repeat(2) { rule.onNodeWithContentDescription("Transpose up").performClick(); await(if (it == 0) "KEY Db" else "KEY D") }
            rule.onAllNodesWithText("I").onFirst().assertExists(); shot("08-roman-transposed")
            rule.activityRule.scenario.recreate(); await("KEY D")
            rule.onAllNodesWithText("I").onFirst().performClick()
            rule.onNodeWithTag("actual-chord").assertTextEquals("D")
            rule.onNodeWithText("Piano").performClick()
            rule.onNodeWithTag("voiced-notes").assertTextEquals("D4  F#4  A4"); shot("09-roman-tap-sounding-D")
            rule.onNodeWithText("Done").performClick()
            assertEquals(2, runBlocking { dao.song(ids[0])!!.transpose })
            assertEquals(canonicalBefore, runBlocking { dao.song(ids[0])!!.documentJson })
            rule.onNodeWithContentDescription("Back").performClick()
            choose(LabelMode.BOTH); open(title); await("KEY D")
            rule.onAllNodesWithText("D (I)").onFirst().assertExists()
            rule.onAllNodesWithText("A (V)").onFirst().assertExists(); shot("10-both")
            rule.onAllNodesWithText("D/F# (I/3)").onFirst().performClick()
            rule.onNodeWithTag("actual-chord").assertTextEquals("D/F#")
            rule.onNodeWithTag("voiced-notes").assertTextContains("F#3", substring = true); shot("11-slash-bass")
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("More").performClick()
            repeat(2) { rule.onNodeWithContentDescription("Raise capo").performClick(); rule.waitUntil(5_000) { runBlocking { dao.song(ids[0])!!.capo == it + 1 } } }
            rule.onAllNodesWithText("D (I)").onFirst().performClick()
            rule.onNodeWithTag("actual-chord").assertTextEquals("D")
            rule.onNodeWithText("Guitar").performClick()
            rule.onNodeWithText("Capo 2 · play C shapes for D", substring = true).assertExists()
            rule.onNodeWithTag("guitar-fretboard").assertExists(); shot("15-capo-shape-concert-D")
            rule.onNodeWithText("Piano").performClick()
            rule.onNodeWithTag("voiced-notes").assertTextEquals("D4  F#4  A4")
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("Reset original key").performClick(); await("KEY C")
            assertEquals(canonicalBefore, runBlocking { dao.song(ids[0])!!.documentJson })
            rule.onNodeWithContentDescription("Edit song").performClick()
            rule.onNodeWithText("Unrecognized chords kept as written:", substring = true).performScrollTo().assertExists(); shot("16-editor-warning")
            rule.onNodeWithContentDescription("Cancel editing").performScrollTo().performClick(); await("KEY C")
            rule.onNodeWithContentDescription("Back").performClick()
            choose(LabelMode.ROMAN); open(missingTitle); await("Key required · Set key / mode")
            rule.onAllNodesWithText("Am").onFirst().assertExists(); shot("12-key-required")
            rule.onNodeWithText("Key required · Set key / mode").performClick()
            rule.onNodeWithText("Minor").performClick(); rule.onNodeWithText("Save key").performClick()
            await("KEY Am")
            rule.onAllNodesWithText("i").onFirst().assertExists()
            rule.onAllNodesWithText("iv").onFirst().assertExists()
            rule.onAllNodesWithText("V").onFirst().assertExists(); shot("13-minor-key")
            rule.activityRule.scenario.recreate(); await("KEY Am")
            rule.onAllNodesWithText("V").onFirst().performClick()
            rule.onNodeWithTag("actual-chord").assertTextEquals("E")
            rule.onNodeWithTag("voiced-notes").assertTextEquals("E4  G#4  B4"); shot("14-minor-dominant")
            rule.onNodeWithText("Done").performClick()
        } finally {
            runBlocking {
                ids.forEach { id -> dao.song(id)?.let { dao.delete(it) } }
                Settings(context).save(prefs)
                assertEquals("Existing library retained", count, dao.count())
            }
            database.close()
            rule.activityRule.scenario.recreate()
        }
    }
}
