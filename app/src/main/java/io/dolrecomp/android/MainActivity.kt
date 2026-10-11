package io.dolrecomp.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

typealias Go = (String, String?) -> Unit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val store = ProjectStore(this)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AppRoot(store) }
            }
        }
    }
}

@Composable
fun AppRoot(store: ProjectStore) {
    var screen by remember { mutableStateOf("list") }
    var id by remember { mutableStateOf("") }
    val go: Go = { s, i -> screen = s; if (i != null) id = i }
    BackHandler(enabled = screen != "list") {
        go(if (screen == "console" || screen == "output") "detail" else "list", null)
    }
    when (screen) {
        "list" -> ListScreen(store, go)
        "detail" -> DetailScreen(store, id, go)
        "console" -> ConsoleScreen(store, id, go)
        "output" -> OutputScreen(store, id, go)
        "about" -> AboutScreen(go)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("‹ Back") } },
        actions = actions,
    )
}

@Composable
fun ListScreen(store: ProjectStore, go: Go) {
    var projects by remember { mutableStateOf(store.list()) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { Bar("DolRecomp", null) { TextButton(onClick = { go("about", null) }) { Text("About") } } },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }) { Text("+ New project") } },
    ) { pad ->
        if (projects.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(24.dp)) {
                Text("No projects yet. Tap “New project”, choose the console, then import a .dol or extract a disc image.")
            }
        } else {
            LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(projects, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth().clickable { go("detail", p.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${p.platform.label} · " + if (p.input.isBlank()) "no input yet" else File(p.input).name,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var plat by remember { mutableStateOf(Platform.GAMECUBE) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Game name") }, singleLine = true)
                    Platform.values().forEach { pl ->
                        Row { RadioButton(plat == pl, { plat = pl }); Text(pl.label, Modifier.padding(top = 12.dp)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = Project(name = name.ifBlank { "Untitled" }, platform = plat)
                    store.save(p); projects = store.list(); creating = false; go("detail", p.id)
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AboutScreen(go: Go) {
    Scaffold(topBar = { Bar("About", { go("list", null) }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("DolRecomp for Android", style = MaterialTheme.typography.titleLarge)
            Text("A front end for DolRecomp (github.com/ExpansionPak/DolRecomp), a static recompiler for GameCube / Wii / Wii U CPU code. DolRecomp is built into this app unmodified.")
            Text("License: GPL-3.0. This app and DolRecomp are free software; the full license text ships with the source repository.")
            Text("What this app does: runs DolRecomp on your own game files and gives you the generated C code. DolRecomp is CPU-only: it does not include the graphics/audio/input runtime a playable port needs.")
            Text("Not included: the LLVM backend (needs LLVM 19/20 for Android) and the “wit” tool that Wii disc extraction needs. You need to supply your own game files; this app contains none.")
        }
    }
}
