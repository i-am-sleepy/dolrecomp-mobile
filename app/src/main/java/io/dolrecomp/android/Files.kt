package io.dolrecomp.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
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
            .filter { it.isFile && !it.path.contains("/out/") && !it.path.contains("-recompiled/") && it.extension.lowercase() in setOf("dol", "rel", "rpx") }
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

    fun hasStorageAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun volumePath(docId: String): String {
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (vol.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$vol"
        return File(base, rel).path
    }

    /** Best-effort real file path for a picked document; null if it can't be worked out. */
    fun realPath(uri: Uri): String? = runCatching {
        when (uri.authority) {
            "com.android.externalstorage.documents" -> volumePath(DocumentsContract.getDocumentId(uri))
            "com.android.providers.downloads.documents" -> {
                val id = DocumentsContract.getDocumentId(uri)
                if (id.startsWith("raw:")) id.removePrefix("raw:") else null
            }
            else -> null
        }
    }.getOrNull()

    /** Real folder path for a picked folder (document tree); null if it can't be worked out. */
    fun realTreePath(uri: Uri): String? = runCatching {
        if (uri.authority == "com.android.externalstorage.documents")
            volumePath(DocumentsContract.getTreeDocumentId(uri))
        else null
    }.getOrNull()
}
