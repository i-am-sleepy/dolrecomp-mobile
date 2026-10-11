package io.dolrecomp.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import java.io.File
import java.io.RandomAccessFile

object Runner {
    fun start(ctx: Context, store: ProjectStore, p: Project, args: List<String>, discUri: String? = null, discExt: String? = null) {
        store.logFile(p).writeText("")
        store.statusFile(p).writeText("starting")
        val i = Intent(ctx, RecompService::class.java).setAction(RecompService.ACTION_RUN)
            .putExtra(RecompService.EXTRA_ARGS, args.toTypedArray())
            .putExtra(RecompService.EXTRA_LOG, store.logFile(p).path)
            .putExtra(RecompService.EXTRA_STATUS, store.statusFile(p).path)
            .putExtra(RecompService.EXTRA_DISC_URI, discUri)
            .putExtra(RecompService.EXTRA_DISC_EXT, discExt)
        ctx.startForegroundService(i)
    }

    fun cancel(ctx: Context) {
        ctx.startService(Intent(ctx, RecompService::class.java).setAction(RecompService.ACTION_CANCEL))
    }

    private fun nativeAlive(ctx: Context): Boolean =
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .runningAppProcesses?.any { it.processName == ctx.packageName + ":native" } == true

    /** idle | starting | running | ok | failed:<code> | cancelled */
    fun status(ctx: Context, f: File): String {
        if (!f.exists()) return "idle"
        val s = f.readText().trim()
        val busy = s == "starting" || s == "running"
        if (busy && System.currentTimeMillis() - f.lastModified() > 6000 && !nativeAlive(ctx)) return "failed:process died"
        return s
    }

    fun tail(f: File, max: Int = 96 * 1024): String {
        if (!f.exists()) return ""
        RandomAccessFile(f, "r").use { r ->
            val len = r.length()
            val start = maxOf(0L, len - max)
            r.seek(start)
            val b = ByteArray((len - start).toInt())
            r.readFully(b)
            return String(b)
        }
    }
}
