package com.henry.chord

import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Collections

/** Controlled real-display samples, separate from gesture/dialog correctness tests. */
class ReaderFrameTest {
    @Test fun sustainedReaderScrollFrameSamples(): Unit = runBlocking {
        val inst=InstrumentationRegistry.getInstrumentation()
        val context=inst.targetContext
        val device=UiDevice.getInstance(inst)
        val name=InstrumentationRegistry.getArguments().getString("measurementLabel") ?: "current"
        require(name.matches(Regex("[a-z0-9-]{1,32}")))
        val db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
        val dao=db.dao(); val count=dao.count(); val prefs=Settings(context).flow.first()
        var id=0L
        val thread=HandlerThread("reader-frame-samples").apply { start() }
        try {
            Settings(context).save(prefs.copy(theme=ThemeMode.LIGHT,chordLabels=LabelMode.BOTH,instrument="Piano",scrollSpeed=60f,leadIn=0,fontSize=19,lineSpacing=1.35f,keepAwake=true))
            id=dao.insert(Song(title="Frame sample original",artist="QA original",key="C",body="{start_of_verse: Verse}\n"+(0 until 160).joinToString("\n") { "[C]Under the [G]evening lights écho 日本 [F]line $it [F#m7b5]again" }))
            val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            fun click(selector: BySelector) {
                val found=device.wait(Until.findObject(selector),10000) ?: error("Missing $selector")
                val candidates=device.findObjects(selector).filter { it.className!="android.widget.EditText" }
                (candidates.lastOrNull() ?: found).click()
                Thread.sleep(300)
            }
            click(By.text("Library"))
            val search=device.wait(Until.findObject(By.clazz("android.widget.EditText")),5000)!!
            search.text="Frame sample original"; Thread.sleep(700)
            if (device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) { device.pressBack(); Thread.sleep(300) }
            click(By.text("Frame sample original"))
            click(By.desc("Fullscreen reader"))
            device.wait(Until.findObject(By.text("Got it")),1500)?.click()
            click(By.desc("Start auto-scroll")); Thread.sleep(2500)
            click(By.desc("Pause auto-scroll"))
            val samples=JSONArray()
            repeat(2) { sample ->
                val frames=Collections.synchronizedList(mutableListOf<LongArray>())
                val listener=android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                    frames.add(longArrayOf(metrics.getMetric(FrameMetrics.TOTAL_DURATION),metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),metrics.getMetric(FrameMetrics.DRAW_DURATION)))
                }
                inst.runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener,Handler(thread.looper)) }
                click(By.desc("Start auto-scroll"))
                Thread.sleep(8000)
                inst.runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
                click(By.desc("Pause auto-scroll"))
                val copy=synchronized(frames) { frames.toList() }
                assertTrue("Actual frames were recorded",copy.size>100)
                fun percentile(index: Int,p: Double): Double {
                    val sorted=copy.map { it[index] }.sorted()
                    return sorted[((sorted.size-1)*p).toInt()]/1_000_000.0
                }
                samples.put(JSONObject().put("sample",sample+1).put("frames",copy.size)
                    .put("over16_67ms",copy.count { it[0]>16_666_667 })
                    .put("totalMedianMs",percentile(0,.5)).put("totalP95Ms",percentile(0,.95))
                    .put("layoutMedianMs",percentile(1,.5)).put("layoutP95Ms",percentile(1,.95))
                    .put("drawMedianMs",percentile(2,.5)).put("drawP95Ms",percentile(2,.95)))
                Thread.sleep(500)
            }
            val result=JSONObject().put("label",name).put("version",context.packageManager.getPackageInfo(context.packageName,0).versionName)
                .put("api",android.os.Build.VERSION.SDK_INT).put("speedDpPerSecond",60).put("sampleSeconds",8)
                .put("viewport",device.executeShellCommand("wm size").trim()).put("density",device.executeShellCommand("wm density").trim())
                .put("samples",samples)
            File(context.filesDir,"reader-frame-evidence").apply { mkdirs() }.resolve("$name.json").writeText(result.toString(2))
        } finally {
            thread.quitSafely(); thread.join(3000)
            device.pressBack(); device.pressBack()
            if (id!=0L) dao.song(id)?.let { dao.delete(it) }
            Settings(context).save(prefs)
            assertEquals("Original library count preserved",count,dao.count())
            db.close()
        }
    }
}
