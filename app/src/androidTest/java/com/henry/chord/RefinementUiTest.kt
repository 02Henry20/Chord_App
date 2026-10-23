package com.henry.chord

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Exercises the requested simplified experience against the installed native app. */
class RefinementUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChordDatabase
    private lateinit var original: Preferences
    private var users=emptyList<SongUserState>()
    private var id=0L; private var count=0
    @Before fun setup()=runBlocking {
        db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
        original=Settings(context).flow.first(); users=db.dao().snapshotUsers(); count=db.dao().count()
        Settings(context).save(original.copy(theme=ThemeMode.LIGHT,instrument="Piano",fontSize=19,lineSpacing=1.35f,chordLabels=LabelMode.SYMBOLS,sheetDisplay=SheetDisplay.LYRICS,spelling=Spelling.AUTO))
        id=db.dao().insert(Song(title="Refinement original",artist="Original QA",key="C",body="{start_of_intro: Intro}\n[C] [G] [F]\n{start_of_verse: Verse 1}\nUn[C]der the [G]evening lights\n{start_of_chorus: Chorus}\n[C]Stay here [F]with me\n{start_of_verse: Verse 2}\n[C]Another [G]line\n{start_of_solo: Piano solo}\n[C13] [F#m7b5]\n{start_of_outro: Outro}\n[C] [G] [C]"))
        await("Library")
    }
    @After fun cleanup()=runBlocking {
        rule.mainClock.autoAdvance=true
        db.withTransaction { db.dao().song(id)?.let { db.dao().delete(it) }; users.forEach { db.dao().saveUserState(it) } }
        Settings(context).save(original); assertEquals(count,db.dao().count()); db.close()
    }
    private fun await(text: String) { rule.waitUntil(20000) { runCatching { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) } }
    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(450)
        val dir=File(context.filesDir,"refinement-evidence").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun category(name: String) { rule.onNodeWithTag("category-$name").performScrollTo().performClick() }
    private fun openFixture() {
        rule.onNodeWithText("Library").performClick(); await("Your library")
        rule.onNodeWithTag("library-search").performTextReplacement("Refinement original")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("song-$id").performClick(); await("KEY C")
    }
    private fun set(value: (Preferences)->Preferences)=runBlocking { Settings(context).save(value(Settings(context).flow.first())) }
    @Test fun safeInsetsRandomShuffleCleanHomeAndImportSpacing() {
        await("Make room\nfor music.")
        val header=rule.onNodeWithText("chordroom").fetchSemanticsNode().boundsInRoot
        var status=0
        rule.runOnUiThread { status=ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0 }
        assertTrue("Header must be below status bar/cutout: ${header.top} >= $status",header.top>=status)
        rule.onNodeWithText("Setlists").assertDoesNotExist(); rule.onNodeWithText("Continue playing").assertDoesNotExist()
        rule.onNodeWithText("Your songs, right where you left them.").assertDoesNotExist()
        rule.waitUntil(10000) { runCatching { rule.onAllNodesWithTag("picked-song",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        val picked=rule.onNodeWithTag("picked-song",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        rule.onNodeWithContentDescription("Shuffle song").performClick()
        rule.waitUntil(5000) { rule.onNodeWithTag("picked-song",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()!=picked }
        rule.onNodeWithTag("pick-up-play").performClick(); await("Paused")
        rule.onNodeWithContentDescription("Back").performClick(); await("Make room\nfor music.")
        rule.onNodeWithText("Open song").assertExists(); rule.onNodeWithText("Continue playing").assertDoesNotExist(); shot("01-home-light")
        rule.onNodeWithText("Import").performClick(); await("Bring your songs.")
        rule.waitUntil(15000) { listOf("Choose files or ZIP","New import","Discard staged import").any { text -> rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
        if (rule.onAllNodesWithText("New import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("New import").performClick()
        if (rule.onAllNodesWithText("Discard staged import").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("Discard staged import").performClick()
        await("Choose files or ZIP")
        val files=rule.onNodeWithText("Choose files or ZIP").fetchSemanticsNode().boundsInRoot
        val folder=rule.onNodeWithText("Choose a folder").fetchSemanticsNode().boundsInRoot
        assertTrue("Picker buttons need a visible gap",folder.top-files.bottom>20)
        rule.onNodeWithText("Chordroom · A library that stays with you.").assertDoesNotExist(); shot("02-import-gap")
        set { it.copy(theme=ThemeMode.DARK) }; rule.onNodeWithText("Home").performClick(); shot("03-home-dark")
    }
    @Test fun accordionLiveTypographySpellingAndLabelPreferences() {
        rule.onNodeWithText("Settings").performClick(); await("Make it yours.")
        rule.onNodeWithTag("theme-LIGHT").assertDoesNotExist(); rule.onNodeWithTag("font-size").assertDoesNotExist(); shot("04-settings-collapsed-light")
        category("Song reader"); rule.onNodeWithTag("font-size").assertExists()
        val before=rule.onNodeWithTag("settings-live-example").fetchSemanticsNode().boundsInRoot.height
        rule.onNodeWithTag("font-size").performTouchInput { click(Offset(width*.9f,height/2f)) }
        rule.waitUntil(5000) { runBlocking { Settings(context).flow.first().fontSize>=27 } }
        rule.onNodeWithTag("settings-live-example").performScrollTo()
        val after=rule.onNodeWithTag("settings-live-example").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Preview must respond to font size",after>before); shot("05-settings-large-live-example")
        rule.onNodeWithTag("line-spacing").performScrollTo().performTouchInput { click(Offset(width*.9f,height/2f)) }
        rule.waitUntil(5000) { runBlocking { Settings(context).flow.first().lineSpacing>1.8f } }
        category("Note spelling"); rule.onNodeWithTag("font-size").assertDoesNotExist()
        rule.onNodeWithTag("spelling-FLATS").performClick(); rule.onNodeWithTag("settings-live-example").performScrollTo()
        rule.onAllNodesWithText("Db",useUnmergedTree=true).onFirst().assertExists(); shot("06-settings-flat-preview")
        category("Chord labels"); rule.onNodeWithTag("label-ROMAN").performClick()
        rule.onNodeWithTag("settings-live-example").performScrollTo(); shot("07-settings-roman-preview")
        category("Appearance"); rule.onNodeWithTag("label-ROMAN").assertDoesNotExist(); rule.onNodeWithTag("theme-DARK").performClick(); shot("08-settings-dark")
        rule.activityRule.scenario.recreate(); await("Make it yours.")
        assertEquals(LabelMode.ROMAN,runBlocking { Settings(context).flow.first().chordLabels })
        assertEquals(ThemeMode.DARK,runBlocking { Settings(context).flow.first().theme })
    }
    @Test fun combinedSheetSectionsRootOnlyDiagramsAndServiceIcons() {
        openFixture()
        rule.onNodeWithContentDescription("Add to setlist").assertDoesNotExist(); rule.onNodeWithContentDescription("Reader options").assertDoesNotExist()
        rule.onNodeWithContentDescription("Chord labels: Chord symbols. Change for this reader").assertDoesNotExist()
        // Legacy lyrics-only preference is intentionally ignored: instrumental chords are still visible.
        rule.onNodeWithText("Intro").assertExists()
        rule.onAllNodesWithContentDescription("Chord C. Sounding C").onFirst().assertExists(); shot("09-reader-section-colors-light")
        rule.onNodeWithText("More").performClick(); rule.onNodeWithText("Capo").assertDoesNotExist(); rule.onNodeWithText("Set key / mode").assertDoesNotExist()
        rule.onNodeWithText("More").performClick()
        rule.onNodeWithContentDescription("Chord C13. Sounding C13").performScrollTo().performClick()
        rule.onNodeWithTag("voiced-notes").assertTextEquals("C4  E4  G4  Bb4  D5  F5  A5")
        rule.onNodeWithText("1st inversion").assertDoesNotExist(); rule.onNodeWithText("Piano").assertDoesNotExist(); rule.onNodeWithText("Guitar").assertDoesNotExist(); shot("10-full-piano-chord")
        rule.onNodeWithText("Done").performClick()
        set { it.copy(chordLabels=LabelMode.ROMAN) }
        rule.onNodeWithContentDescription("Transpose up").performClick(); rule.onNodeWithContentDescription("Transpose up").performClick(); await("KEY D")
        rule.waitUntil(5000) { runBlocking { db.dao().song(id)?.transpose==2 } }
        rule.onAllNodesWithContentDescription("Chord I. Sounding D").onFirst().performClick(); rule.onNodeWithTag("voiced-notes").assertTextEquals("D4  F#4  A4"); shot("11-roman-sounding-root-D")
        rule.onNodeWithText("Done").performClick(); set { it.copy(instrument="Guitar") }
        rule.onAllNodesWithContentDescription("Chord I. Sounding D").onFirst().performClick(); rule.onNodeWithTag("guitar-fretboard").assertExists()
        rule.onNodeWithText("Voicing 1").assertDoesNotExist(); rule.onNodeWithText("Standard tuning",substring=true).assertDoesNotExist(); shot("12-single-guitar-diagram")
        rule.onNodeWithText("Done").performClick(); rule.onNodeWithContentDescription("Song links").performClick()
        rule.onNodeWithTag("service-SPOTIFY",useUnmergedTree=true).assertExists(); rule.onNodeWithTag("service-YOUTUBE",useUnmergedTree=true).assertExists(); shot("13-service-icons")
        rule.onNodeWithText("Done").performClick(); set { it.copy(theme=ThemeMode.DARK,instrument="Piano",chordLabels=LabelMode.BOTH) }
        rule.onNodeWithText("Chorus").performScrollTo(); shot("14-reader-section-colors-dark")
        rule.onNodeWithContentDescription("Fullscreen reader").performClick(); shot("15-fullscreen-combined")
        rule.onNodeWithContentDescription("Exit fullscreen").performClick()
    }
}
