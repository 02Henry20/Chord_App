package com.henry.chord

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import androidx.room.withTransaction
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = db ?: Room.databaseBuilder(applicationContext, ChordDatabase::class.java, "chord.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { db = it }
        setContent { ChordApp(database) }
    }
    companion object { private var db: ChordDatabase? = null }
}

val Serif = FontFamily.Serif

@Composable
fun ChordApp(database: ChordDatabase) {
    val dao = database.dao()
    val context = LocalContext.current
    val settings = remember { Settings(context.applicationContext) }
    val prefs by settings.flow.collectAsState(Preferences(), context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val darkTheme = when (prefs.theme) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    SideEffect {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkTheme }
        )
    }
    val songs by remember(dao) { dao.librarySummaries() }.collectAsState(emptyList(), context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val setlists by remember(dao) { dao.setlists() }.collectAsState(emptyList(), context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val entries by remember(dao) { dao.entries() }.collectAsState(emptyList(), context = kotlinx.coroutines.Dispatchers.Main.immediate)
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        BackupManager(context,database).recoverSettings()
        val flags = context.getSharedPreferences("first_run", 0)
        if (!flags.getBoolean("seeded", false)) {
            database.withTransaction {
                if (dao.count() == 0) {
                    val ids = demoSongs.map { dao.insert(it) }
                    val set = dao.insertSetlist(Setlist(name = "The living room session"))
                    ids.take(3).forEachIndexed { i, id -> dao.addEntry(SetlistEntry(set, id, i)) }
                }
            }
            flags.edit().putBoolean("seeded", true).apply()
        }
        ready = true
    }
    MaterialTheme(colorScheme = if (darkTheme) dark else light) {
        val nav = rememberNavController()
        val back by nav.currentBackStackEntryAsState()
        val route = back?.destination?.route ?: "home"
        val destinations = listOf(Triple("home", "Home", Icons.Outlined.Home), Triple("library", "Library", Icons.Outlined.LibraryMusic), Triple("setlists", "Setlists", Icons.Outlined.QueueMusic), Triple("import", "Import", Icons.Outlined.FileDownload), Triple("settings", "Settings", Icons.Outlined.Tune))
        val topLevel = ready && back?.destination != null && destinations.any { it.first == route }
        val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
        val wideNavigation = windowSize.width > windowSize.height
        fun navigate(path: String) {
            if (!ready || nav.currentDestination == null) return
            nav.navigate(path) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
        }
        val open: (Song) -> Unit = { song -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.markPlayed(song.id) }; nav.navigate("reader/${song.id}") }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(Modifier.fillMaxSize()) {
                if (topLevel && wideNavigation) NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { (path, label, icon) -> NavigationRailItem(selected = route == path, onClick = { navigate(path) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) }, alwaysShowLabel = false) }
                }
            Scaffold(modifier = Modifier.weight(1f), containerColor = Color.Transparent, topBar={ if (topLevel) RainbowAccent() }, bottomBar = {
                if (topLevel && !wideNavigation) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEach { (path, label, icon) -> NavigationBarItem(selected = route == path, onClick = { navigate(path) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer)) }
                }
            }) { padding ->
                if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                    composable("home") { HomeScreen(songs, setlists, entries, { value -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.favorite(value.id, !value.favorite) } }, open, { nav.navigate("library") }, { nav.navigate("setlist/$it") }, { nav.navigate("editor/0") }, { nav.navigate("import") }) }
                    composable("library") { LibraryScreen(dao, songs, prefs, open, { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.favorite(it.id, !it.favorite) } }, { nav.navigate("editor/0") }) }
                    composable("setlists") { SetlistsScreen(setlists, entries, { nav.navigate("setlist/$it") }, { name -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { val id = dao.insertSetlist(Setlist(name = name)); nav.navigate("setlist/$id") } }) }
                    composable("setlist/{id}") { entry ->
                        val id = entry.arguments?.getString("id")?.toLongOrNull()
                        val set = setlists.find { it.id == id }
                        if (set != null) SetlistScreen(set, songs, entries.filter { it.setlistId == id }.sortedBy { it.position }, { nav.popBackStack() }, { selected -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.markPlayed(selected.songId) }; nav.navigate("reader/${selected.songId}?setlist=${set.id}&entry=${selected.entryId}") }, dao, { nav.popBackStack() })
                    }
                    composable("import") { ImportScreen(dao, { nav.navigate("library") }) }
                    composable("settings") { SettingsScreen(prefs, { scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.clearHistory() } }, database) { p -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { settings.save(p) } } }
                    composable("reader/{id}?setlist={setlist}&entry={entry}") { entry ->
                        val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        val song by remember(id) { dao.observeSong(id) }.collectAsState(null, context = kotlinx.coroutines.Dispatchers.Main.immediate)
                        val setId = entry.arguments?.getString("setlist")?.toLongOrNull()
                        song?.let { full -> ReaderScreen(full, prefs, setlists, entries, songs, setId, dao, { nav.popBackStack() }, { nav.navigate("editor/${full.id}") }, { next -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.markPlayed(next.id) }; nav.navigate("reader/${next.id}?setlist=$setId") { popUpTo("reader/{id}?setlist={setlist}&entry={entry}") { inclusive = true } } }, queueEntryId=entry.arguments?.getString("entry"), openQueue={ selected -> scope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) { dao.markPlayed(selected.songId) }; nav.navigate("reader/${selected.songId}?setlist=$setId&entry=${selected.entryId}") { popUpTo("reader/{id}?setlist={setlist}&entry={entry}") { inclusive=true } } }) }
                    }
                    composable("editor/{id}") { entry -> val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                        val full by remember(id) { dao.observeSong(id) }.collectAsState(null, context = kotlinx.coroutines.Dispatchers.Main.immediate)
                        if (id == 0L || full != null) EditorScreen(full, dao, { nav.popBackStack() }, { nav.navigate("library") { popUpTo("home"); launchSingleTop = true } })
                    }
                }
            }
            }
        }
    }
}

@Composable
fun Eyebrow(text: String) { Text(text.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
@Composable
fun PageTitle(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontFamily = Serif, fontSize = 36.sp, letterSpacing = (-1).sp); Spacer(Modifier.height(6.dp)); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
        action?.invoke()
    }
}
@Composable
fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, fontFamily = Serif, fontSize = 23.sp, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick) { Text(action, fontSize = 12.sp) } }
}
@Composable
fun HomeScreen(songs: List<Song>, sets: List<Setlist>, entries: List<SetlistEntry>, favorite: (Song) -> Unit, open: (Song) -> Unit, library: () -> Unit, openSet: (Long) -> Unit, newSong: () -> Unit, onImport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().widthIn(max = 760.dp).padding(horizontal = 24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(8.dp)); Text("chordroom", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                Spacer(Modifier.weight(1f)); Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(100.dp)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.OfflineBolt, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(5.dp)); Text("ALL OFFLINE", fontSize = 9.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary) } }
            }
            Eyebrow("A little practice. A little progress.")
            Text("Make room\nfor music.", fontFamily = Serif, fontSize = 46.sp, lineHeight = 49.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp))
            Text("Your songs, right where you left them.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            val recent = songs.maxByOrNull { it.lastOpened }
            if (recent != null) Surface(onClick = { open(recent) }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("PICK UP & PLAY", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.weight(1f)); Icon(Icons.Outlined.NorthEast, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(19.dp)) }
                    Spacer(Modifier.height(24.dp)); Text(recent.title, fontFamily = Serif, fontSize = 28.sp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(6.dp)); Text("${recent.artist}   ·   Key ${KeyContext.from(recent.key,recent.tonalMode)?.let { Theory.currentKey(it,recent.transpose).name } ?: Music.transpose(recent.key, recent.transpose)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(22.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.PlayCircleOutline, null, tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)); Text(if (recent.lastOpened > 0) "Continue playing" else "Open song", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction("Write a song", Icons.Outlined.EditNote, Modifier.weight(1f), newSong)
                QuickAction("Import songs", Icons.Outlined.FileDownload, Modifier.weight(1f), onImport, warm = true)
            }
            SectionTitle("Your collection", "View all", library)
        }
        items(songs.sortedByDescending { it.lastOpened }.take(3), key = { it.id }) { SongRow(it, { open(it) }, { favorite(it) }) }
        if (songs.isEmpty()) item { EmptyState("Your first song starts here", "Write an original or import a chord sheet.", "Write a song", newSong) }
        item { if (sets.isNotEmpty()) { SectionTitle("Ready for a session"); val set = sets.first(); Surface(onClick = { openSet(set.id) }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.QueueMusic, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(set.name, fontWeight = FontWeight.Medium); Text("${entries.count { it.setlistId == set.id }} songs · Your order, your pace", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }; Icon(Icons.Outlined.ChevronRight, null) } } }; Spacer(Modifier.height(28.dp)); Eyebrow("Made for the music, free of distractions.") }
    }
}
@Composable
fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit, warm: Boolean = false) {
    val background=if (warm) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val foreground=if (warm) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    OutlinedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.outlinedCardColors(containerColor = background,contentColor=foreground)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(21.dp), tint = foreground); Spacer(Modifier.width(8.dp)); Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium) } }
}
@Composable
fun EmptyState(title: String, body: String, action: String, onClick: () -> Unit) { Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)); Text(title, fontFamily = Serif, fontSize = 24.sp, modifier = Modifier.padding(top = 16.dp)); Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp)); TextButton(onClick) { Text(action) } } }
