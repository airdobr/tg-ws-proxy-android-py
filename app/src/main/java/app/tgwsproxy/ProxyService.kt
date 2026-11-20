package app.tgwsproxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class ProxyService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.running(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Ядро работает в потоке Python; самый надёжный стоп — завершить процесс.
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!Prefs.isRunning(this)) {
            Prefs.running(this, true)
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgwsproxy:proxy").also { it.acquire() }
            val argv = Prefs.argv(this).toTypedArray()
            val log = File(filesDir, "proxy.log").absolutePath
            File(filesDir, "proxy.log").appendText("Сервис запущен, старт Python... (${Prefs.argv(this).filterIndexed { i, _ -> i == 0 || Prefs.argv(this)[i - 1] != "--secret" }.joinToString(" ")})\n")
            Thread({
                try {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                    Python.getInstance().getModule("android_main").callAttr("run", argv, log)
                } catch (e: Throwable) {
                    File(filesDir, "proxy.log").appendText("FATAL: ${e}\n")
                    File(filesDir, "proxy.log").appendText("Ядро остановилось\n")
                } finally {
                    Prefs.running(this, false)
                }
            }, "python-proxy").start()
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("proxy", "Прокси", NotificationManager.IMPORTANCE_LOW))
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "proxy")
            .setContentTitle("TG WS Proxy работает")
            .setContentText("${Prefs.linkHost(this)}:${Prefs.port(this)}")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() { wake?.release(); super.onDestroy() }

    companion object { const val ACTION_STOP = "stop" }
}
