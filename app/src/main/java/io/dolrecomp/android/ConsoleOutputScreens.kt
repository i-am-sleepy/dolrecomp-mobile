package io.dolrecomp.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConsoleScreen(store: ProjectStore, id: String, go: Go) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val p = remember(id) { store.load(id) ?: Project(id = id) }
    var log by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("idle") }
    val scroll = rememberScrollState()

    LaunchedEffect(id) {
        while (true) {
            val (l, s) = withContext(Dispatchers.IO) { Runner.tail(store.logFile(p)) to Runner.status(ctx, store.statusFile(p)) }
            log = l; status = s
            delay(700)
        }
    }
    LaunchedEffect(log.length) { scroll.scrollTo(scroll.maxValue) }
    val busy = status == "running" || status == "starting"

    Scaffold(topBar = { Bar("Console · ${p.name}", { go("detail", null) }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    busy -> "Running… (keeps going with the screen off)"
                    status == "ok" -> "Finished successfully"
                    status == "cancelled" -> "Cancelled"
                    status.startsWith("failed") -> "Failed (${status.removePrefix("failed:")})"
                    else -> "Idle"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Runner.cancel(ctx) }, enabled = busy) { Text("Cancel") }
                OutlinedButton(onClick = { clip.setText(AnnotatedString(log)) }) { Text("Copy log") }
                if (status == "ok") OutlinedButton(onClick = { go("output", id) }) { Text("Output") }
            }
            SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    log.ifEmpty { "(no output yet)" },
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                    modifier = Modifier.verticalScroll(scroll),
                )
            }
        }
    }
}

@Composable
fun OutputScreen(store: ProjectStore, id: String, go: Go) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = remember(id) { store.load(id) ?: Project(id = id) }
    var files by remember { mutableStateOf(listOf<java.io.File>()) }
    var msg by remember { mutableStateOf("") }
    fun refresh() { files = store.outDir(p).walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList() }
    LaunchedEffect(id) { withContext(Dispatchers.IO) { refresh() } }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            msg = "Exporting…"
            withContext(Dispatchers.IO) { Files.zipTo(ctx, store.outDir(p), uri) }
            msg = "Exported."
        }
    }
    val total = files.sumOf { it.length() }

    Scaffold(topBar = { Bar("Output · ${p.name}", { go("detail", null) }) }) { pad ->
        Column(Modifier.padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${files.size} files · ${Files.human(total)}", style = MaterialTheme.typography.titleMedium)
            Text("Folder: ${store.outDir(p).path}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { export.launch("${p.name.replace(' ', '_')}-generated.zip") }, enabled = files.isNotEmpty()) { Text("Export as .zip") }
                OutlinedButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { store.outDir(p).deleteRecursively(); store.outDir(p); refresh() }
                        msg = "Output cleared"
                    }
                }) { Text("Clear") }
            }
            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
            LazyColumn(Modifier.weight(1f)) {
                items(files.take(500)) { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(f.relativeTo(store.outDir(p)).path, Modifier.weight(1f), fontSize = 12.sp)
                        Text(Files.human(f.length()), fontSize = 12.sp)
                    }
                }
                if (files.size > 500) item { Text("…and ${files.size - 500} more (all included in the zip)") }
            }
        }
    }
}
