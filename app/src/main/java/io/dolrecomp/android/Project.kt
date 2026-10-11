package io.dolrecomp.android

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = "New project",
    val platform: Platform = Platform.GAMECUBE,
    /** Absolute path to .dol/.rel/.rpx or a folder of .rel files. */
    val input: String = "",
    val titleId: String = "",
    val options: Map<String, String> = emptyMap(),
    val extraArgs: String = "",
    /** Content URI of the last disc image picked for extraction. */
    val discUri: String = "",
    /** Where the last disc extraction went (next to the ISO, or the app folder when blank). */
    val extractDir: String = "",
)

class ProjectStore(private val ctx: Context) {
    val root: File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "projects").apply { mkdirs() }

    fun dir(p: Project) = File(root, p.id).apply { mkdirs() }
    fun filesDir(p: Project) = File(dir(p), "files").apply { mkdirs() }
    fun outDir(p: Project) = File(dir(p), "out").apply { mkdirs() }
    fun discDir(p: Project) = File(dir(p), "disc")
    fun logFile(p: Project) = File(dir(p), "run.log")
    fun statusFile(p: Project) = File(dir(p), "run.status")

    fun list(): List<Project> =
        (root.listFiles() ?: emptyArray()).mapNotNull { load(it.name) }.sortedBy { it.name.lowercase() }

    fun load(id: String): Project? = runCatching {
        val j = JSONObject(File(File(root, id), "project.json").readText())
        val o = j.optJSONObject("options") ?: JSONObject()
        Project(
            id = id,
            name = j.optString("name"),
            platform = runCatching { Platform.valueOf(j.optString("platform")) }.getOrDefault(Platform.GAMECUBE),
            input = j.optString("input"),
            titleId = j.optString("titleId"),
            options = o.keys().asSequence().associateWith { o.getString(it) },
            extraArgs = j.optString("extraArgs"),
            discUri = j.optString("discUri"),
            extractDir = j.optString("extractDir"),
        )
    }.getOrNull()

    fun save(p: Project) {
        val j = JSONObject()
            .put("name", p.name).put("platform", p.platform.name).put("input", p.input)
            .put("titleId", p.titleId).put("extraArgs", p.extraArgs).put("discUri", p.discUri).put("extractDir", p.extractDir)
            .put("options", JSONObject(p.options))
        File(dir(p), "project.json").writeText(j.toString(2))
    }

    fun delete(p: Project) { File(root, p.id).deleteRecursively() }

    /** The exact argument list for a recompile run. */
    fun buildArgs(p: Project): List<String> {
        val a = mutableListOf<String>()
        for (s in Options.specs) {
            val v = p.options[s.key]?.trim().orEmpty()
            if (v.isEmpty()) continue
            when (s.kind) {
                Kind.SWITCH -> if (v == "true") a += s.flag
                Kind.FILE -> a += listOf(s.flag, v)
                else -> a += listOf(s.flag, if (s.relPath && !v.startsWith("/")) File(dir(p), v).path else v)
            }
        }
        if (p.platform == Platform.GAMECUBE) a += "--gamecube"
        if (p.platform == Platform.WIIU && p.options["cpu"].isNullOrBlank()) a += listOf("--cpu", "espresso")
        a += tokenize(p.extraArgs)
        a += p.input
        if (p.platform == Platform.WII) a += p.titleId
        a += outDir(p).path
        return a
    }

    fun readyError(p: Project): String? = when {
        p.input.isBlank() -> "Choose an input (.dol, .rel, .rpx or a REL folder) first."
        !File(p.input).exists() -> "The chosen input no longer exists."
        p.platform == Platform.WII && p.titleId.length != 6 -> "Wii projects need a 6-character title ID (e.g. SUKE01)."
        else -> null
    }
}
