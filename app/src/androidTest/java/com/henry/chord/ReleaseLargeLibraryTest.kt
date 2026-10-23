package com.henry.chord

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ReleaseLargeLibraryTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val inst=InstrumentationRegistry.getInstrumentation(); private val device=UiDevice.getInstance(inst)
    private fun shot(name: String) { rule.waitForIdle(); Thread.sleep(350); val dir=File(inst.targetContext.filesDir,"release-evidence").apply { mkdirs() }; File(dir,"$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) } }
    private fun await(text: String) { rule.waitUntil(20000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    @Test fun thousandSongUiSearchScrollingAndOfflineThemes() {
        val context=inst.targetContext; val db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build(); val dao=db.dao()
        val before=runBlocking { dao.count() }; val prefs=runBlocking { Settings(context).flow.first() }; val ids=mutableListOf<Long>()
        val wifi=device.executeShellCommand("settings get global wifi_on").trim(); val data=device.executeShellCommand("settings get global mobile_data").trim()
        try {
            runBlocking { db.withTransaction {
                repeat(1000) { i ->
                    val doc=SheetParser.parse("{title: Release library %04d}\n{artist: Release Violet Trio}\n{key: C}\n[C]Unique firefly phrase $i [G]sings [F]home".format(i),"qa.cho").document.copy(album="Café session",tags=listOf("日本","rainbow"))
                    ids+=dao.store(doc)
                }
            }; Settings(context).save(prefs.copy(theme=ThemeMode.LIGHT,chordLabels=LabelMode.SYMBOLS,sheetDisplay=SheetDisplay.BOTH)) }
            device.executeShellCommand("svc wifi disable"); device.executeShellCommand("svc data disable")
            rule.activityRule.scenario.recreate(); await("Make room\nfor music."); shot("33-home-light"); rule.onNodeWithText("Library").performClick(); await("Your library"); await("${before+1000} songs · All offline"); shot("28-library-light-1000-offline")
            val started=SystemClock.elapsedRealtime(); rule.onNodeWithTag("library-search").performTextReplacement("firefly phrase 432")
            rule.waitUntil(10000) { rule.onAllNodesWithTag("song-${ids[432]}").fetchSemanticsNodes().isNotEmpty() }
            val latency=SystemClock.elapsedRealtime()-started; rule.onNodeWithTag("lyric-${ids[432]}",useUnmergedTree=true).assertTextContains("Unique firefly phrase 432",substring=true); shot("29-lyric-search-offline")
            rule.onNodeWithTag("favorite-${ids[432]}").performClick(); rule.onNodeWithText("Favorites").performClick(); rule.onNodeWithTag("song-${ids[432]}").assertExists()
            rule.onNodeWithTag("song-${ids[432]}").performClick(); await("KEY C"); rule.onNodeWithContentDescription("Back").performClick()
            rule.onNodeWithText("All songs").performClick(); rule.onNodeWithTag("library-search").performTextReplacement("Release Violet Trio")
            rule.waitUntil(10000) { rule.onAllNodesWithTag("song-${ids[0]}").fetchSemanticsNodes().isNotEmpty() }
            repeat(8) { rule.onNodeWithTag("library-list").performTouchInput { swipeUp(durationMillis=300) }; Thread.sleep(200) }
            shot("30-large-library-scrolled")
            rule.onNodeWithText("Settings").performClick(); rule.onNodeWithTag("theme-DARK").performClick(); rule.onNodeWithText("Home").performClick(); shot("34-home-dark"); rule.onNodeWithText("Library").performClick(); shot("31-library-dark-1000-offline")
            rule.onNodeWithTag("library-search").performTextReplacement("Café session"); rule.waitUntil(10000) { rule.onAllNodesWithTag("song-${ids[0]}").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("library-search").performTextReplacement("日本"); rule.waitUntil(10000) { rule.onAllNodesWithTag("song-${ids[0]}").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Settings").performClick(); rule.onNodeWithTag("theme-SYSTEM").performClick(); rule.waitUntil(5000) { runBlocking { Settings(context).flow.first().theme == ThemeMode.SYSTEM } }; shot("32-settings-system")
            File(context.filesDir,"release-evidence/large-library-results.json").writeText(JSONObject().put("uiSongCount",before+1000).put("originalFixtures",1000).put("lyricQueryToVisibleMs",latency).put("wifiDisabled",true).put("mobileDataDisabled",true).put("lightDarkSystem",true).toString(2))
        } catch (e: Throwable) { shot("large-library-failure"); throw e }
        finally {
            if (wifi=="1" || wifi=="2") device.executeShellCommand("svc wifi enable")
            if (data=="1") device.executeShellCommand("svc data enable")
            runBlocking { db.withTransaction { ids.forEach { id -> dao.song(id)?.let { dao.delete(it) } } }; Settings(context).save(prefs); assertEquals(before,dao.count()) }; db.close(); rule.activityRule.scenario.recreate()
        }
    }
}
