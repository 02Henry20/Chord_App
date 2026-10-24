package com.henry.chord

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.Assert.*
import java.io.File

class LibrarySelectionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ChordDatabase
    private lateinit var prefs: Preferences
    private val ids=mutableListOf<Long>()
    private var count=0
    @Before fun setup()=runBlocking {
        db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
        count=db.dao().count(); prefs=Settings(context).flow.first()
        Settings(context).save(prefs.copy(theme=ThemeMode.LIGHT,chordLabels=LabelMode.SYMBOLS,instrument="Guitar",fontSize=19))
    }
    @After fun cleanup()=runBlocking {
        db.dao().deleteSelected(ids); Settings(context).save(prefs)
        assertEquals(count,db.dao().count()); db.close()
    }
    private fun await(text: String) { rule.waitUntil(15000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(300)
        val dir=File(context.filesDir,"rc3-evidence").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun longPressSelectAllBeyondFirstPageFavoriteAndDeleteConfirmation()=runBlocking {
        db.withTransaction { repeat(105) { ids+=db.dao().insert(Song(title="Selection QA %03d".format(it),artist="Original fixture",key="C",body="[C]Under [G]evening [F]lights")) } }
        // The fixture uses a separate Room instance; restart collection for a fresh total count.
        rule.activityRule.scenario.recreate()
        await("Library"); rule.onNodeWithText("Library").performClick(); await("Your library")
        rule.onNodeWithTag("library-search").performTextReplacement("Selection QA")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("song-${ids.first()}").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("song-${ids.first()}").performTouchInput { longClick() }
        await("1 selected")
        rule.onNodeWithText("Select all 105").performClick(); await("105 selected"); shot("01-multi-select-light")
        rule.activityRule.scenario.recreate(); await("105 selected")
        Settings(context).save(Settings(context).flow.first().copy(theme=ThemeMode.DARK)); shot("01b-multi-select-dark")
        rule.onNodeWithText("Favorite").performClick()
        rule.waitUntil(10000) { runBlocking { ids.all { db.dao().song(it)?.favorite==true } } }
        rule.onNodeWithTag("song-${ids.first()}").performTouchInput { longClick() }
        rule.onNodeWithText("Select all 105").performClick(); await("105 selected")
        rule.onNodeWithText("Unfavorite").performClick()
        rule.waitUntil(10000) { runBlocking { ids.all { db.dao().song(it)?.favorite==false } } }
        rule.onNodeWithTag("song-${ids.first()}").performTouchInput { longClick() }
        rule.onNodeWithText("Select all 105").performClick(); await("105 selected")
        rule.onNodeWithText("Delete").performClick(); shot("02-delete-confirmation")
        rule.onNodeWithText("Cancel").performClick(); assertEquals(count+105,db.dao().count())
        rule.onNodeWithText("Delete").performClick(); rule.onNodeWithText("Delete selected").performClick()
        rule.waitUntil(15000) { runBlocking { db.dao().count()==count } }
        await("No matches yet"); shot("03-selection-deleted")
    }
    @Test fun fiveIconCategoriesAndMarginsPersist()=runBlocking {
        await("Settings"); rule.onNodeWithText("Settings").performClick(); await("Make it yours.")
        listOf("Appearance","Song reader","Chords & instrument","Performance","Library & backup").forEach { rule.onNodeWithTag("category-icon-$it",useUnmergedTree=true).assertExists() }
        shot("04-five-settings-light")
        rule.onNodeWithTag("category-Song reader").performClick()
        listOf("sheet-margin","song-line-gap","chord-line-gap","section-gap").forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(width*.75f,height/2f)) }
        }
        rule.onNodeWithTag("settings-live-example").performScrollTo(); shot("05-spacing-preview")
        rule.waitUntil(5000) { runBlocking { Settings(context).flow.first().sheetMargin>25f && Settings(context).flow.first().sectionGap>20f } }
        val expected=Settings(context).flow.first()
        rule.activityRule.scenario.recreate(); await("Make it yours.")
        assertEquals(expected,Settings(context).flow.first())
        Settings(context).save(expected.copy(theme=ThemeMode.DARK)); shot("06-five-settings-dark")
    }
    @Test fun bulkFavoritesChunkBeyondSqliteParameterLimit()=runBlocking {
        val isolated=Room.inMemoryDatabaseBuilder(context,ChordDatabase::class.java).build()
        try {
            val localIds=isolated.withTransaction { (0..1004).map { isolated.dao().insert(Song(title="Bulk $it",artist="QA",key="C",body="[C]Original")) } }
            assertEquals(localIds,isolated.dao().matchingLibraryIds(LibrarySearch.query("Bulk",LibraryFilter(),LibrarySort.TITLE,idsOnly=true)))
            isolated.dao().favoriteSelected(localIds,true)
            assertTrue(isolated.dao().librarySummaries().first().all { it.favorite })
            isolated.dao().deleteSelected(localIds); assertEquals(0,isolated.dao().count())
        } finally { isolated.close() }
    }
    @Test fun homeArtworkAndShuffleRefreshBothRandomSelections() {
        await("Make room\nfor music.")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("pick-up-artwork",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("pick-up-artwork",useUnmergedTree=true).assertExists()
        val title=rule.onNodeWithTag("picked-song",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        fun collection(): String {
            rule.onNodeWithTag("home-list").performTouchInput { swipeUp() }
            val nodes=rule.onAllNodes(SemanticsMatcher("collection rows") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("home-collection-")==true },useUnmergedTree=true).fetchSemanticsNodes()
            return nodes.first().config[SemanticsProperties.TestTag]
        }
        val before=collection(); shot("07-home-random-collection")
        rule.onNodeWithContentDescription("Shuffle song").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onNodeWithTag("picked-song",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()!=title }
        shot("08-home-thumbnail-shuffled")
        assertNotEquals(before,collection())
    }
    @Test fun legacyCapoIsInertAndFullscreenShowsDelta()=runBlocking {
        val id=db.dao().insert(Song(title="Legacy capo QA",artist="Original QA",key="C",body="{start_of_verse: Verse}\n[C]Under the [G]evening [F]lights")); ids+=id
        db.dao().saveOffsets(ArrangementUserState(id,0,5))
        await("Library"); rule.onNodeWithText("Library").performClick(); rule.onNodeWithTag("library-search").performTextReplacement("Legacy capo QA")
        rule.waitUntil(10000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("song-$id").performClick(); await("KEY C")
        rule.onNodeWithText("More").assertDoesNotExist(); rule.onNodeWithText("Capo",substring=true).assertDoesNotExist()
        rule.onAllNodesWithContentDescription("Chord C. Sounding C").onFirst().performClick()
        rule.onNodeWithContentDescription("Six string guitar: -1, 3, 2, 0, 1, 0. Starting fret 1").assertExists()
        rule.onNodeWithText("Capo",substring=true).assertDoesNotExist(); rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("Transpose up").performClick(); rule.onNodeWithContentDescription("Transpose up").performClick()
        rule.onNodeWithContentDescription("Fullscreen reader").performClick(); rule.onNodeWithTag("fullscreen-transpose").assertTextEquals("+2 semitones"); shot("09-compact-fullscreen-delta")
        assertEquals(5,db.dao().song(id)!!.capo)
    }
    @Test fun concurrentSettingsChangesKeepAllFields()=runBlocking {
        coroutineScope {
            launch { Settings(context).update { it.copy(sheetMargin=31f) } }
            launch { Settings(context).update { it.copy(songLineGap=19f) } }
            launch { Settings(context).update { it.copy(chordLineGap=9f) } }
            launch { Settings(context).update { it.copy(sectionGap=27f) } }
        }
        val saved=Settings(context).flow.first()
        assertEquals(31f,saved.sheetMargin,0f); assertEquals(19f,saved.songLineGap,0f)
        assertEquals(9f,saved.chordLineGap,0f); assertEquals(27f,saved.sectionGap,0f)
    }
}
