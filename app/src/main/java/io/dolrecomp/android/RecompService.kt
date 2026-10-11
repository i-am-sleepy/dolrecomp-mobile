package io.dolrecomp.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Runs in the separate ":native" process. One job per process: when the job ends the
 * process is killed, so DolRecomp's global state can never leak between runs.
 * Status is reported through files (run.status / run.log) so it survives process death.
 */
class RecompService : Service() {
    companion object {
        const val ACTION_RUN = "io.dolrecomp.android.RUN"
        const val ACTION_CANCEL = "io.dolrecomp.android.CANCEL"
        const val EXTRA_ARGS = "args"
        const val EXTRA_LOG = "log"
        const val EXTRA_STATUS = "status"
        const val EXTRA_DISC_URI = "disc_uri"
        const val EXTRA_DISC_EXT = "disc_ext"

        @Volatile private var statusPath: String? = null
        @Volatile private var tempDisc: File? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val i = intent ?: return START_NOT_STICKY
        when (i.action) {
            ACTION_CANCEL -> cancel()
            ACTION_RUN -> startJob(i)
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("run", "Recompilation", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "run")
            .setContentTitle("DolRecomp").setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).build()
    }

    private fun startJob(i: Intent) {
        val n = notification("Running…")
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)

        val args = i.getStringArrayExtra(EXTRA_ARGS) ?: emptyArray()
        val log = i.getStringExtra(EXTRA_LOG) ?: return
        val status = File(i.getStringExtra(EXTRA_STATUS) ?: return)
        val discUri = i.getStringExtra(EXTRA_DISC_URI)
        val discExt = i.getStringExtra(EXTRA_DISC_EXT) ?: ".iso"
        statusPath = status.path

        val wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dolrecomp:run")
        wl.acquire(6 * 60 * 60 * 1000L)

        status.writeText("running")
        thread(name = "dolrecomp") {
            var rc: Int
            try {
                val resolved = args.map { a ->
                    if (a.contains("{DISC}") && discUri != null) {
                        if (tempDisc == null) tempDisc = copyDisc(Uri.parse(discUri), discExt, log)
                        a.replace("{DISC}", tempDisc!!.path)
                    } else a
                }
                rc = NativeBridge.nativeRun(resolved.toTypedArray(), log)
            } catch (t: Throwable) {
                File(log).appendText("\n[app] ${t}\n")
                rc = -2
            }
            tempDisc?.delete()
            status.writeText(if (rc == 0) "ok" else "failed:$rc")
            Process.killProcess(Process.myPid())
        }
    }

    /**
     * DolRecomp needs a real file path. Re-opening the picked document through /proc/self/fd
     * is blocked by Android's storage layer, so make a temporary copy in app storage
     * (deleted when the job ends).
     */
    private fun copyDisc(uri: Uri, ext: String, log: String): File {
        val logf = File(log)
        val dest = File(cacheDir, "disc-input$ext").also { it.delete() }
        val total = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        if (total > 0 && cacheDir.usableSpace < total + (64L shl 20))
            throw IOException("Not enough free storage: need about ${total shr 20} MB for a temporary copy of the disc, only ${cacheDir.usableSpace shr 20} MB free")
        logf.appendText("[app] copying the disc image to temporary storage (${total shr 20} MB)…\n")
        var done = 0L
        var mark = 10
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open the picked disc image")
        input.use { i ->
            dest.outputStream().use { o ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    val r = i.read(buf)
                    if (r < 0) break
                    o.write(buf, 0, r)
                    done += r
                    if (total > 0 && done * 100 / total >= mark) { logf.appendText("[app] copied $mark%\n"); mark += 10 }
                }
            }
        }
        logf.appendText("[app] copy finished, extracting…\n")
        return dest
    }

    private fun cancel() {
        statusPath?.let { File(it).writeText("cancelled") }
        tempDisc?.delete()
        Process.killProcess(Process.myPid())
    }
}
