package com.henry.chord

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

private object Phase4Session {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context get() = instrumentation.targetContext
    val stage get() = File(context.filesDir, "phase4-ui-session.json")
    fun database() = Room.databaseBuilder(context, ChordDatabase::class.java, "chord.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    fun cleanup() = runBlocking {
        if (!stage.isFile) return@runBlocking
        val data = JSONObject(stage.readText()); val db = database(); val dao = db.dao()
        try {
            val art = mutableSetOf<String>()
            val ids = data.getJSONArray("ids")
            for (i in 0 until ids.length()) dao.song(ids.getLong(i))?.let { song -> song.artworkPath?.let { art += it }; dao.delete(song) }
            art += data.getString("initialArtwork")
            dao.deleteSetlist(Setlist(data.getLong("setlist"), "Phase 4 session"))
            val used = dao.artworkUsage().mapNotNull { it.artworkPath }.toSet()
            art.filter { it !in used && it.startsWith("artwork/") }.forEach { path -> File(context.filesDir, path).delete(); ArtworkStore(context).thumbnailPath(path, "").delete() }
            val p = data.getJSONObject("prefs")
            Settings(context).save(if (data.has("preferencesV1")) PreferenceFormat.decode(data.getJSONObject("preferencesV1")) else Preferences(instrument = p.getString("instrument"), fontSize = p.getInt("fontSize"), keepAwake = p.getBoolean("keepAwake"), chordLabels = LabelMode.valueOf(p.getString("labels")), spelling = Spelling.valueOf(p.getString("spelling")), theme = ThemeMode.valueOf(p.getString("theme"))))
            context.contentResolver.delete(Uri.parse(data.getString("pickerImage")), null, null)
            assertEquals(data.getInt("baseline"), dao.count())
            stage.delete()
        } finally { db.close() }
    }
}

class Phase4LibraryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val device = UiDevice.getInstance(Phase4Session.instrumentation)
    private fun await(text: String) { rule.waitUntil(20_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun screenshot(name: String, native: Boolean = false) {
        if (!native) rule.waitForIdle()
        device.waitForIdle(); Thread.sleep(400)
        val dir = File(Phase4Session.context.filesDir, "phase4-evidence").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { Phase4Session.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun search(text: String) {
        rule.onNodeWithTag("library-search").performTextReplacement(text)
        rule.waitUntil(10_000) { rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text) and hasTestTag("result-count")).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle(); Thread.sleep(200)
    }
    private fun assertArtwork(title: String, state: String) {
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Artwork for $title") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun nativeFile(name: String): UiObject {
        device.waitForIdle()
        val file = device.findObject(UiSelector().resourceId("android:id/title").text(name))
        if (!file.waitForExists(5_000)) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(name))
        check(file.waitForExists(5_000)) { "Picker missing $name" }
        return file
    }
    @Test fun librarySearchArtworkFavoritesLinksAndResponsiveLayouts() {
        if (Phase4Session.stage.exists()) Phase4Session.cleanup()
        val context = Phase4Session.context; val db = Phase4Session.database(); val dao = db.dao()
        val original = runBlocking { Settings(context).flow.first() }
        val baseline = runBlocking { dao.count() }
        val bitmap = Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(45,91,108))
        val paint = android.graphics.Paint().apply { color = Color.rgb(233,198,119); textSize = 65f; isAntiAlias = true }
        canvas.drawCircle(240f,210f,120f,paint); paint.color = Color.rgb(45,91,108); canvas.drawText("C",217f,232f,paint)
        val source = File(context.cacheDir,"phase4-cover.png"); source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        val art = ArtworkStore(context).commit(source, "phase4"); source.delete()
        val imageName = "Phase4-manual-${System.currentTimeMillis()}.png"
        val media = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,imageName); put(MediaStore.MediaColumns.MIME_TYPE,"image/png"); put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/ChordroomPhase4") })!!
        canvas.drawColor(Color.rgb(119,71,93)); paint.color = Color.rgb(249,219,170); canvas.drawCircle(240f,240f,150f,paint)
        context.contentResolver.openOutputStream(media)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        val ids = runBlocking {
            val corrupt = "artwork/phase4-corrupt-${Canonical.uuid()}.png"
            File(context.filesDir,corrupt).writeText("not an image")
            fun doc(title: String, artist: String, body: String) = SheetParser.parse("{title:$title}\n{artist:$artist}\n{key:C}\n$body", "fixture.cho").document
            val main = doc("Evening lanterns", "River Trio", "{start_of_verse: Verse}\n[C]Unique fireflies shimmer [G]over café windows\n[F]Bring your friends [C]and sing").let { it.copy(album = "Café Sessions",year = 2026,tags = listOf("piano","日本語"),artwork = ArtworkRef(art.path), links = MusicLinks("https://open.spotify.com/track/0123456789ABCDEFGHIJKL", "https://youtu.be/FAKEvideo01"),arrangements = listOf(it.arrangements.single().copy(tempoBpm = 96.0))) }
            listOf(dao.store(main), dao.store(doc("Missing cover study", "Quiet Friends", "[Am]A quiet room").copy(artwork = ArtworkRef("artwork/phase4-missing-${Canonical.uuid()}.png"))), dao.store(doc("Linkless study", "Quiet Friends", "[C]No links required").copy(artwork = ArtworkRef(corrupt))))
        }
        val set = runBlocking { dao.insertSetlist(Setlist(name = "Phase 4 session")).also { dao.addEntry(SetlistEntry(it,ids[0],0)) } }
        val settings = JSONObject().put("instrument",original.instrument).put("fontSize",original.fontSize).put("keepAwake",original.keepAwake).put("labels",original.chordLabels.name).put("spelling",original.spelling.name).put("theme",original.theme.name)
        Phase4Session.stage.writeText(JSONObject().put("ids",org.json.JSONArray(ids)).put("setlist",set).put("baseline",baseline).put("initialArtwork",art.path).put("pickerImage",media.toString()).put("prefs",settings).put("preferencesV1",PreferenceFormat.encode(original)).toString())
        val captured = CopyOnWriteArrayList<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_VIEW) { captured += Intent(intent); return Instrumentation.ActivityResult(Activity.RESULT_OK,null) }
                return null
            }
        }
        Phase4Session.instrumentation.addMonitor(monitor)
        var passed = false
        try {
            runBlocking { Settings(context).save(original.copy(theme = ThemeMode.LIGHT, instrument = "Piano", chordLabels = LabelMode.SYMBOLS)) }
            rule.activityRule.scenario.recreate(); await("Make room\nfor music.")
            rule.onNodeWithText("Library").performClick(); await("Your library"); screenshot("01-library-light")
            val scrollTarget = runBlocking { dao.libraryResults(LibrarySearch.query("",LibraryFilter(),LibrarySort.TITLE)).first()[75].song }
            try {
                rule.onNodeWithTag("library-list").performScrollToIndex(75)
                rule.onNodeWithTag("song-${scrollTarget.id}").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithContentDescription("Back").performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithTag("song-${scrollTarget.id}").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithTag("song-${scrollTarget.id}").assertIsDisplayed()
            } finally { runBlocking { dao.markPlayed(scrollTarget.id,scrollTarget.lastOpened) } }
            search("River Trio"); rule.onNodeWithTag("song-${ids[0]}").assertExists(); assertArtwork("Evening lanterns","Local artwork"); screenshot("02-artist-search")
            search("ening lant"); rule.onNodeWithTag("song-${ids[0]}").assertExists(); screenshot("03-title-fragment")
            search("fireflies shimmer"); rule.waitUntil(10_000) { rule.onAllNodesWithTag("lyric-${ids[0]}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            val text = rule.onNodeWithTag("lyric-${ids[0]}",useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].first()
            assertTrue(text.spanStyles.isNotEmpty()); screenshot("04-lyric-highlight")
            search("cafe sessions"); rule.onNodeWithTag("song-${ids[0]}").assertExists()
            search("日本"); rule.onNodeWithTag("song-${ids[0]}").assertExists(); screenshot("05-unicode-tag")
            rule.onNodeWithTag("library-options").performClick()
            rule.onNodeWithText("Artist / title").performClick()
            rule.onNodeWithText("Artist (exact name)").performScrollTo().performTextInput("River Trio")
            rule.onNodeWithText("Key (e.g. C, Am)").performScrollTo().performTextInput("C")
            rule.onNodeWithText("Apply").performClick(); rule.onNodeWithTag("song-${ids[0]}").assertExists(); screenshot("05b-filters")
            rule.onNodeWithTag("library-options").performClick(); rule.onNodeWithText("Clear filters").performScrollTo().performClick(); rule.onNodeWithText("Apply").performClick()
            search("not-a-song-unheard"); await("No matches yet"); screenshot("06-no-results")
            search("Evening lanterns"); rule.onNodeWithTag("favorite-${ids[0]}").performClick()
            rule.waitUntil(5_000) { runBlocking { dao.song(ids[0])!!.favorite } }
            rule.activityRule.scenario.recreate(); await("Your library")
            rule.onNodeWithTag("library-search").assertTextContains("Evening lanterns")
            rule.onNodeWithTag("favorite-${ids[0]}").assertIsOn()
            rule.onNodeWithText("Favorites").performClick(); assertArtwork("Evening lanterns","Local artwork"); screenshot("07-favorites")
            rule.onNodeWithTag("song-${ids[0]}").performClick(); await("KEY C"); assertArtwork("Evening lanterns","Local artwork"); screenshot("08-reader-header")
            rule.onNodeWithContentDescription("Song links").performClick(); rule.onNodeWithText("Open Spotify track").performClick(); rule.onNodeWithText("Open YouTube video").performClick()
            assertEquals(listOf("https://open.spotify.com/track/0123456789ABCDEFGHIJKL","https://www.youtube.com/watch?v=FAKEvideo01"),captured.map { it.dataString })
            assertTrue(captured.all { it.action == Intent.ACTION_VIEW && it.hasCategory(Intent.CATEGORY_BROWSABLE) }); screenshot("09-provided-links")
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithContentDescription("Edit song").performClick(); rule.onNodeWithText("Album, tags, artwork & links").performScrollTo().performClick()
            rule.onNodeWithText("Change artwork").performScrollTo().performClick()
            val roots = device.findObject(UiSelector().description("Show roots")); assertTrue(roots.waitForExists(10_000)); roots.click()
            nativeFile("sdk_gphone_x86").click(); nativeFile("Pictures").click(); nativeFile("ChordroomPhase4").click()
            var file = device.findObject(UiSelector().descriptionStartsWith("$imageName,"))
            if (!file.waitForExists(2_000)) file=nativeFile(imageName)
            check(file.waitForExists(5_000)) { "Image picker did not show $imageName" }; screenshot("10-native-artwork-picker",true); file.click()
            await("New artwork ready. Save to apply.")
            rule.onNodeWithText("Save").performScrollTo().performClick(); await("KEY C")
            rule.waitUntil(10_000) { runBlocking { dao.song(ids[0])!!.artworkPath != art.path } }; assertArtwork("Evening lanterns","Local artwork"); screenshot("11-manual-artwork")
            rule.onNodeWithContentDescription("Fullscreen reader").performClick(); screenshot("12-performance-mode"); rule.onNodeWithContentDescription("Exit fullscreen").performClick()
            rule.onNodeWithContentDescription("Back").performClick(); rule.onNodeWithText("All songs").performClick(); search("Missing cover study"); assertArtwork("Missing cover study","Generated artwork"); screenshot("13-missing-artwork-fallback")
            search("Linkless study"); assertArtwork("Linkless study","Generated artwork"); rule.onNodeWithTag("song-${ids[2]}").performClick(); await("KEY C"); rule.onNodeWithContentDescription("Song links").performClick()
            rule.onNodeWithText("Search Spotify").performClick(); rule.onNodeWithText("Search YouTube").performClick(); assertTrue(captured.takeLast(2).all { it.dataString!!.contains("search") }); screenshot("14-search-links")
            rule.onNodeWithText("Done").performClick(); rule.onNodeWithContentDescription("Back").performClick()
            rule.onNodeWithText("Settings").performClick(); rule.onNodeWithTag("category-Appearance").performScrollTo().performClick(); rule.onNodeWithTag("theme-DARK").performClick()
            rule.waitUntil(5_000) { runBlocking { Settings(context).flow.first().theme == ThemeMode.DARK } }
            rule.onNodeWithText("Library").performClick(); search("Evening lanterns"); screenshot("16-library-dark")
            device.setOrientationLeft(); Thread.sleep(700); await("Your library"); screenshot("17-landscape-library")
            rule.onNodeWithTag("song-${ids[0]}").performClick(); await("KEY C"); screenshot("18-landscape-reader")
            device.setOrientationNatural(); Thread.sleep(500); rule.onNodeWithContentDescription("Back").performClick()
            passed = true
        } catch (error: Throwable) {
            screenshot("failure",true)
            device.dumpWindowHierarchy(File(context.filesDir,"phase4-evidence/failure.xml"))
            throw error
        } finally {
            device.setOrientationNatural(); device.unfreezeRotation()
            Phase4Session.instrumentation.removeMonitor(monitor)
            db.close()
            if (!passed) Phase4Session.cleanup()
        }
    }
}

/** Run in a separate instrumentation invocation AFTER adb force-stop, for true process-restart proof. */
class Phase4ColdStartTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun favoriteArtworkAndThemeSurviveProcessRestart() {
        check(Phase4Session.stage.exists()) { "Run Phase4LibraryTest first" }
        val data = JSONObject(Phase4Session.stage.readText()); val id = data.getJSONArray("ids").getLong(0)
        val device = UiDevice.getInstance(Phase4Session.instrumentation)
        val scale = device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        try {
            rule.waitUntil(20_000) { rule.onAllNodesWithText("Library").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(ThemeMode.DARK,runBlocking { Settings(Phase4Session.context).flow.first().theme })
            rule.onNodeWithText("Library").performClick(); rule.onNodeWithTag("library-search").performTextReplacement("Evening lanterns"); rule.onNodeWithText("Favorites").performClick()
            rule.waitUntil(15_000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("favorite-$id").assertIsOn()
            rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Artwork for Evening lanterns") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Local artwork"),useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle(); Thread.sleep(500)
            val dir = File(Phase4Session.context.filesDir,"phase4-evidence").apply { mkdirs() }
            File(dir,"19-cold-favorites.png").outputStream().use { Phase4Session.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
            device.executeShellCommand("settings put system font_scale 1.5"); Thread.sleep(800)
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("song-$id").fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle(); Thread.sleep(350)
            File(dir,"20-large-text-library.png").outputStream().use { Phase4Session.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
            rule.onNodeWithTag("song-$id").performClick()
            rule.waitUntil(10_000) { rule.onAllNodesWithText("KEY C").fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle(); Thread.sleep(350)
            File(dir,"21-large-text-reader.png").outputStream().use { Phase4Session.instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally {
            device.executeShellCommand("settings put system font_scale $scale"); Phase4Session.cleanup()
            if (data.has("preferencesV1")) assertEquals(PreferenceFormat.decode(data.getJSONObject("preferencesV1")),runBlocking { Settings(Phase4Session.context).flow.first() })
        }
    }
}
