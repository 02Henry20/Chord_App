package com.henry.chord

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

val Context.preferences by preferencesDataStore("preferences")
data class Preferences(val dark: Boolean = false, val instrument: String = "Guitar", val fontSize: Int = 19, val keepAwake: Boolean = true, val chordLabels: LabelMode = LabelMode.SYMBOLS, val spelling: Spelling = Spelling.AUTO)
class Settings(private val context: Context) {
    val flow = context.preferences.data.map { Preferences(it[booleanPreferencesKey("dark")] ?: false, it[stringPreferencesKey("instrument")] ?: "Guitar", it[intPreferencesKey("fontSize")] ?: 19, it[booleanPreferencesKey("keepAwake")] ?: true,
        labelPreference(it[stringPreferencesKey("chordLabels")], it[booleanPreferencesKey("roman")] ?: false),
        runCatching { Spelling.valueOf(it[stringPreferencesKey("spelling")] ?: "AUTO") }.getOrDefault(Spelling.AUTO)) }
    suspend fun save(p: Preferences) { context.preferences.edit { it[booleanPreferencesKey("dark")] = p.dark; it[stringPreferencesKey("instrument")] = p.instrument; it[intPreferencesKey("fontSize")] = p.fontSize; it[booleanPreferencesKey("keepAwake")] = p.keepAwake; it[stringPreferencesKey("chordLabels")] = p.chordLabels.name; it[stringPreferencesKey("spelling")] = p.spelling.name } }
    companion object {
        fun labelPreference(stored: String?, legacyRoman: Boolean): LabelMode = runCatching { LabelMode.valueOf(stored ?: "") }.getOrDefault(if (legacyRoman) LabelMode.ROMAN else LabelMode.SYMBOLS)
    }
}

val demoSongs = listOf(
    Song(title = "A little closer", artist = "Chord Originals", key = "C", favorite = true, body = "{comment:Verse 1}\n[C]Morning gathers [G]at the door\n[Am]Light spills gently [F]on the floor\n[C]Every mile and [G]every turn\n[F]Brings me somewhere [G]I can learn\n\n{comment:Chorus}\n[F]Stay a little [C]closer\n[Am]Let the quiet [G]in\n[F]Every road leads [C]homeward\n[G]Let us start [C]again\n\n{comment:Verse 2}\n[C]Fold the map and [G]take my hand\n[Am]There is time to [F]understand\n[C]All the words we [G]could not say\n[F]Find their place in [G]us today\n\n{comment:Chorus}\n[F]Stay a little [C]closer\n[Am]Let the quiet [G]in\n[F]Every road leads [C]homeward\n[G]Let us start [C]again\n\n{comment:Outro}\n[F]Every road leads [C]homeward\n[G]Let us start [C]again"),
    Song(title = "Blue hour", artist = "Chord Originals", key = "G", body = "{comment:Verse}\n[G]Windows glow along the [D]street\n[Em]Evening moves beneath our [C]feet\n[G]Leave a little room for [D]wonder\n[C]Let the day fall [G]under\n\n{comment:Chorus}\n[C]Blue hour, [G]slow down\n[D]Keep this light [Em]around\n[C]One more song, [G]one more sound\n[D]Before the sun goes [G]down"),
    Song(title = "Paper boats", artist = "Chord Originals", key = "D", favorite = true, body = "{comment:Verse}\n[D]Paper boats on [A]silver streams\n[Bm]Carry all our [G]little dreams\n[D]Past the bridge and [A]out of view\n[G]I will follow [D]you\n\n{comment:Chorus}\n[G]Let them sail, [D]let them go\n[A]Where the quiet [Bm]waters flow\n[G]Every bend a [D]different blue\n[A]I will follow [D]you"),
    Song(title = "Sunday kind of slow", artist = "Chord Originals", key = "A", body = "{comment:Verse}\n[A]Coffee cooling [E]by the window\n[F#m]Nothing on the [D]radio\n[A]We have nowhere [E]else to go\n[D]Sunday kind of [A]slow\n\n{comment:Chorus}\n[D]Take your time and [A]stay\n[E]Let the hours drift [F#m]away\n[D]There is all we [A]need to know\n[E]Sunday kind of [A]slow"),
    Song(title = "The open road", artist = "Chord Originals", key = "E", body = "{comment:Verse}\n[E]Pack the car at [B]break of day\n[C#m]Watch the rooftops [A]fade away\n[E]Every skyline [B]something new\n[A]Open road and [E]you\n\n{comment:Chorus}\n[A]Drive until the [E]stars arrive\n[B]Sing to keep the [C#m]night alive\n[A]Every mile a [E]different view\n[B]Open road and [E]you"),
    Song(title = "Wildflower", artist = "Chord Originals", key = "F", body = "{comment:Verse}\n[F]Between the stones a [C]flower grows\n[Dm]How it finds the [Bb]light, who knows\n[F]Quiet strength and [C]nothing more\n[Bb]Waiting by the [F]door\n\n{comment:Chorus}\n[Bb]Wildflower, [F]hold your ground\n[C]Let the rain come [Dm]falling down\n[Bb]There is beauty [F]to be found\n[C]Even in this [F]town")
)
