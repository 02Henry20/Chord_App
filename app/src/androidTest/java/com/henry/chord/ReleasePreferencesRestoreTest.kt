package com.henry.chord

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import java.io.File
import androidx.room.Room
import androidx.room.withTransaction
import org.json.JSONArray

/** Optional final QA cleanup. Fixture is a logical snapshot of this installation's pre-audit preferences. */
class ReleasePreferencesRestoreTest {
    @Test fun restoreOwnerPreferencesFromPrivateQaSnapshot(): Unit = runBlocking {
        assumeTrue("QA maintenance requires an explicit restoreOwnerSnapshot argument.", InstrumentationRegistry.getArguments().getString("restoreOwnerSnapshot") == "true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fixture=File(context.filesDir,"qa-original-preferences.json")
        check(fixture.isFile) { "Provide the private pre-audit preference snapshot first." }
        val expected=PreferenceFormat.decode(JSONObject(fixture.readText()))
        Settings(context).save(expected); assertEquals(expected,Settings(context).flow.first())
        fixture.delete()
        val history=File(context.filesDir,"qa-original-history.json")
        if (history.isFile) {
            val rows=JSONArray(history.readText())
            val db=Room.databaseBuilder(context,ChordDatabase::class.java,"chord.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
            try { db.withTransaction { for (i in 0 until rows.length()) { val row=rows.getJSONArray(i); check(db.dao().song(row.getLong(0)) != null); db.dao().markPlayed(row.getLong(0),row.getLong(1)) } } }
            finally { db.close() }
            history.delete()
        }
    }
}
