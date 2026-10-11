package io.dolrecomp.android

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun Choice(label: String, value: String, choices: List<String>, allowDefault: Boolean = true, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value.ifEmpty { "tool default" }}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (allowDefault) DropdownMenuItem(text = { Text("(tool default)") }, onClick = { onPick(""); open = false })
            choices.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onPick(c); open = false }) }
        }
    }
}

@Composable
fun OptionRow(spec: OptionSpec, value: String, onValue: (String) -> Unit, onImport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (spec.kind) {
            Kind.SWITCH -> Row {
                Column(Modifier.weight(1f)) { Text(spec.label) ; if (spec.help.isNotEmpty()) Text(spec.help, style = MaterialTheme.typography.bodySmall) }
                Switch(checked = value == "true", onCheckedChange = { onValue(if (it) "true" else "") })
            }
            Kind.CHOICE -> Choice(spec.label, value, spec.choices) { onValue(it) }
            Kind.NUMBER -> OutlinedTextField(
                value, { onValue(it.filter(Char::isDigit)) }, label = { Text(spec.label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            Kind.TEXT -> OutlinedTextField(
                value, onValue, label = { Text(spec.label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Kind.FILE -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(spec.label)
                    Text(if (value.isEmpty()) "none" else File(value).name, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onImport) { Text("Import") }
                if (value.isNotEmpty()) TextButton(onClick = { onValue("") }) { Text("Clear") }
            }
        }
        if (spec.help.isNotEmpty() && spec.kind != Kind.SWITCH)
            Text(spec.help, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DetailScreen(store: ProjectStore, id: String, go: Go) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var p by remember(id) { mutableStateOf(store.load(id) ?: Project(id = id)) }
    fun update(n: Project) { p = n; store.save(n) }
    var advanced by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var pendingKey by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("idle") }

    LaunchedEffect(id) {
        while (true) {
            status = withContext(Dispatchers.IO) { Runner.status(ctx, store.statusFile(p)) }
            delay(800)
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val key = pendingKey
            val name = withContext(Dispatchers.IO) {
                Files.displayName(ctx, uri).also { Files.copyUri(ctx, uri, File(store.filesDir(p), it)) }
            }
            val dest = File(store.filesDir(p), name).path
            if (key == "__input__") update(p.copy(input = dest)) else update(p.copy(options = p.options + (key to dest)))
            msg = "Imported $name"
        }
    }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val tree = DocumentFile.fromTreeUri(ctx, uri)
            val dest = File(store.filesDir(p), "rels_" + (tree?.name ?: "folder").replace('/', '_'))
            val n = withContext(Dispatchers.IO) { if (tree != null) Files.copyRels(ctx, tree, dest) else 0 }
            if (n > 0) update(p.copy(input = dest.path))
            msg = if (n > 0) "Imported $n .rel files" else "No .rel files found in that folder"
        }
    }
    val pickDisc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val ext = "." + Files.displayName(ctx, uri).substringAfterLast('.', "").lowercase()
            if (ext != ".iso" && ext != ".wbfs") {
                msg = "DolRecomp only reads plain .iso or .wbfs images (this one is “$ext”). Compressed formats like .rvz, .gcz, .ciso or .nkit need converting to .iso first."
                return@rememberLauncherForActivityResult
            }
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            update(p.copy(discUri = uri.toString()))
            val wit = File(ctx.applicationInfo.nativeLibraryDir, "libwit.so")
            val args = buildList {
                add("extract")
                if (wit.exists()) { add("--wit"); add(wit.path) }
                add("{DISC}"); add(store.discDir(p).path)
            }
            Runner.start(ctx, store, p, args, uri.toString(), ext)
            go("console", id)
        }
    }

    val extractedDol = File(store.discDir(p), "sys/main.dol")
    val err = store.readyError(p)
    val command = "dolrecomp " + store.buildArgs(p).joinToString(" ")
    val busy = status == "running" || status == "starting"

    Scaffold(topBar = { Bar(p.name, { go("list", null) }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(p.name, { update(p.copy(name = it)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Choice("Console", p.platform.label, Platform.values().map { it.label }, allowDefault = false) { l ->
                update(p.copy(platform = Platform.values().first { it.label == l }))
            }

            Text("Input", style = MaterialTheme.typography.titleMedium)
            Text(if (p.input.isBlank()) "None chosen" else File(p.input).name)
            OutlinedButton(onClick = { pendingKey = "__input__"; pickFile.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Import .dol / .rel / .rpx") }
            OutlinedButton(onClick = { pickTree.launch(null) }, Modifier.fillMaxWidth()) { Text("Import a folder of .rel files") }
            OutlinedButton(onClick = { choosing = true }, Modifier.fillMaxWidth()) { Text("Choose from project files") }
            if (extractedDol.exists())
                OutlinedButton(onClick = { update(p.copy(input = extractedDol.path)) }, Modifier.fillMaxWidth()) { Text("Use extracted main.dol") }
            if (p.platform == Platform.WII)
                OutlinedTextField(p.titleId.uppercase(), { update(p.copy(titleId = it.take(6))) }, label = { Text("Wii title ID (6 chars)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Text("Disc image", style = MaterialTheme.typography.titleMedium)
            Text("Pick a GameCube .iso (extracted by the built-in reader) or a Wii .iso/.wbfs (needs the external “wit” tool, which this build doesn't include yet).", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { pickDisc.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Extract a disc image…") }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Options", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Advanced"); Switch(advanced, { advanced = it })
            }
            Options.groups.forEach { g ->
                val specs = Options.specs.filter { it.group == g && (advanced || !it.advanced) }
                if (specs.isNotEmpty()) {
                    Text(g, style = MaterialTheme.typography.labelLarge)
                    specs.forEach { s ->
                        OptionRow(
                            s, p.options[s.key].orEmpty(),
                            onValue = { v -> update(p.copy(options = p.options + (s.key to v))) },
                            onImport = { pendingKey = s.key; pickFile.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
            OutlinedTextField(p.extraArgs, { update(p.copy(extraArgs = it)) }, label = { Text("Extra arguments (raw)") }, modifier = Modifier.fillMaxWidth())

            Text("Command", style = MaterialTheme.typography.titleMedium)
            Text(command, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            TextButton(onClick = { clip.setText(AnnotatedString(command)); msg = "Copied" }) { Text("Copy command") }

            if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
            if (err != null) Text(err, color = MaterialTheme.colorScheme.error)
            Text("Status: $status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (err != null) msg = err else { Runner.start(ctx, store, p, store.buildArgs(p)); go("console", id) }
                }, enabled = !busy) { Text("Run") }
                OutlinedButton(onClick = { go("console", id) }) { Text("Console") }
                OutlinedButton(onClick = { go("output", id) }) { Text("Output") }
            }
            TextButton(onClick = { confirmDelete = true }) { Text("Delete project", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosing) {
        val base = store.dir(p)
        val cands = remember(choosing) { Files.inputCandidates(base) }
        AlertDialog(
            onDismissRequest = { choosing = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text("Close") } },
            title = { Text("Project files") },
            text = {
                if (cands.isEmpty()) Text("Nothing here yet. Import a file or extract a disc first.")
                else LazyColumn {
                    items(cands) { f ->
                        TextButton(onClick = { update(p.copy(input = f.path)); choosing = false }) { Text(f.relativeTo(base).path) }
                    }
                }
            },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${p.name}”?") },
        text = { Text("This removes its imported files, extracted disc and generated output.") },
        confirmButton = { TextButton(onClick = { store.delete(p); confirmDelete = false; go("list", null) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
