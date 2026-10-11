package io.dolrecomp.android

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.system.Os
import java.io.File
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
            var pfd: ParcelFileDescriptor? = null
            try {
                // Disc images are opened by file descriptor so multi-GB files are never copied.
                // DolRecomp decides the format from the file extension, and /proc/self/fd/N has
                // none, so expose the descriptor through a symlink that ends in .iso / .wbfs.
                var link: File? = null
                val resolved = args.map { a ->
                    if (a.contains("{DISC}") && discUri != null) {
                        if (link == null) {
                            pfd = contentResolver.openFileDescriptor(Uri.parse(discUri), "r")
                            link = File(cacheDir, "disc-input$discExt").also { it.delete() }
                            Os.symlink("/proc/self/fd/${pfd!!.fd}", link!!.path)
                        }
                        a.replace("{DISC}", link!!.path)
                    } else a
                }
                rc = NativeBridge.nativeRun(resolved.toTypedArray(), log)
            } catch (t: Throwable) {
                File(log).appendText("\n[app] ${t}\n")
                rc = -2
            }
            status.writeText(if (rc == 0) "ok" else "failed:$rc")
            Process.killProcess(Process.myPid())
        }
    }

    private fun cancel() {
        statusPath?.let { File(it).writeText("cancelled") }
        Process.killProcess(Process.myPid())
    }
}
