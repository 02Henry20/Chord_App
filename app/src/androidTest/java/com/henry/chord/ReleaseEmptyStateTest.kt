package com.henry.chord

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class ReleaseEmptyStateTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun emptyLibraryAndNoResultsUseIsolatedDatabase() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val empty=Room.inMemoryDatabaseBuilder(context,ChordDatabase::class.java).build()
        try {
            rule.runOnUiThread { rule.activity.setContent { ChordApp(empty) } }
            rule.waitUntil(10000) { rule.onAllNodesWithText("Library").fetchSemanticsNodes().isNotEmpty() }
            runBlocking { empty.dao().clearLibrary() } // Only this private in-memory test database.
            rule.onNodeWithText("Library").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText("0 songs").fetchSemanticsNodes().isNotEmpty() }
            fun shot(name: String) {
                rule.waitForIdle(); Thread.sleep(350)
                val folder=File(context.filesDir,"release-evidence").apply { mkdirs() }
                File(folder,"$name.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
            }
            shot("35-empty-library")
            rule.onNodeWithTag("library-search").performTextInput("Nothing here")
            rule.waitUntil(10000) { rule.onAllNodesWithText("No matches yet").fetchSemanticsNodes().isNotEmpty() }
            shot("36-empty-search")
        } finally { rule.activityRule.scenario.recreate(); empty.close() }
    }
}
