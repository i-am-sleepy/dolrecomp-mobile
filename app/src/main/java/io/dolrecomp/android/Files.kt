package io.dolrecomp.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Files {
    fun displayName(ctx: Context, uri: Uri): String =
        (ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "file_${System.currentTimeMillis()}").replace('/', '_')

    fun copyUri(ctx: Context, uri: Uri, dest: File) {
        dest.parentFile?.mkdirs()
        ctx.contentResolver.openInputStream(uri)?.use { i ->
            dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) }
        }
    }

    /** Copies every .rel under a picked folder into [dest], keeping sub-folders. */
    fun copyRels(ctx: Context, tree: DocumentFile, dest: File): Int {
        var n = 0
        for (f in tree.listFiles()) {
            val name = f.name ?: continue
            if (f.isDirectory) n += copyRels(ctx, f, File(dest, name))
            else if (name.endsWith(".rel", ignoreCase = true)) { copyUri(ctx, f.uri, File(dest, name)); n++ }
        }
        return n
    }

    /** Inputs DolRecomp can take: .dol/.rpx files and folders that hold .rel modules. */
    fun inputCandidates(base: File): List<File> {
        val all = base.walkTopDown()
            .filter { it.isFile && !it.path.contains("/out/") && it.extension.lowercase() in setOf("dol", "rel", "rpx") }
            .toList()
        val relDirs = all.filter { it.extension.equals("rel", true) }.mapNotNull { it.parentFile }.distinct()
        return all.filter { !it.extension.equals("rel", true) } + relDirs
    }

    fun zipTo(ctx: Context, dir: File, out: Uri) {
        ctx.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                dir.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry(f.relativeTo(dir).path))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    fun human(n: Long): String = when {
        n >= 1L shl 30 -> "%.1f GB".format(n / (1L shl 30).toDouble())
        n >= 1L shl 20 -> "%.1f MB".format(n / (1L shl 20).toDouble())
        n >= 1L shl 10 -> "%.1f KB".format(n / 1024.0)
        else -> "$n B"
    }
}
