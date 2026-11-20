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
    @Volatile private var pythonReady = false
    @Volatile private var lastMobile: Boolean? = null
    private var netCb: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "proxy.log").appendText("CRASH (сервис): ${android.util.Log.getStackTraceString(e)}\n") } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.running(this, false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            File(filesDir, "proxy.log").appendText("Прокси остановлен\n")
            // Сервис живёт в отдельном процессе :proxy, поэтому завершаем только его, окно приложения остаётся.
            android.os.Process.killProcess(android.os.Process.myPid())
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!Prefs.isRunning(this)) {
            Prefs.running(this, true)
            wake = try {
                (getSystemService(POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tgwsproxy:proxy").also { it.acquire() }
            } catch (e: Exception) {
                File(filesDir, "proxy.log").appendText("WakeLock недоступен: $e\n"); null
            }
            val argv = Prefs.argv(this).toTypedArray()
            val log = File(filesDir, "proxy.log").absolutePath
            File(filesDir, "proxy.log").appendText("Сервис запущен, старт Python... (${Prefs.argv(this).filterIndexed { i, _ -> i == 0 || Prefs.argv(this)[i - 1] != "--secret" }.joinToString(" ")})\n")
            Thread({
                try {
                    if (!Python.isStarted()) Python.start(AndroidPlatform(this))
                    pythonReady = true
                    Python.getInstance().getModule("android_main").callAttr("run", argv, log)
                } catch (e: Throwable) {
                    File(filesDir, "proxy.log").appendText("FATAL: ${e}\n")
                } finally {
                    File(filesDir, "proxy.log").appendText("Ядро остановилось\n")
                    Prefs.running(this, false)
                }
            }, "python-proxy").start()
            watchNetwork()
        }
        return START_STICKY
    }

    /** Следим за типом сети: в мобильной — без TLS (порт 80), на Wi-Fi — как в настройках. */
    private fun watchNetwork() {
        lastMobile = Prefs.isMobile(this)
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: android.net.Network, caps: android.net.NetworkCapabilities) {
                val mobile = Prefs.isMobile(caps)
                if (mobile == lastMobile) return
                lastMobile = mobile
                onNetworkTypeChanged(mobile)
            }
        }
        cm.registerDefaultNetworkCallback(cb)
        netCb = cb
    }

    private fun onNetworkTypeChanged(mobile: Boolean) {
        if (!Prefs.bool(this, "auto_mobile", true) || !pythonReady) return
        val noSecure = Prefs.noSecureFor(this, mobile)
        val changed = try {
            Python.getInstance().getModule("android_main").callAttr("set_no_secure", noSecure).toBoolean()
        } catch (e: Throwable) {
            File(filesDir, "proxy.log").appendText("Ошибка переключения сети: $e\n"); false
        }
        startForegroundCompat()
        if (changed) notifySwitch(mobile, noSecure)
    }

    private fun notifySwitch(mobile: Boolean, noSecure: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("net", "Смена сети", NotificationManager.IMPORTANCE_DEFAULT))
        val title = if (mobile) "Мобильная сеть: TLS выключен" else "Wi-Fi: TLS включён"
        val text = if (noSecure) "Cloudflare через порт 80, HTTP/2 для медиа отключён"
                   else "Cloudflare через порт 443, HTTP/2 для медиа включён"
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        nm.notify(2, Notification.Builder(this, "net")
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(R.drawable.ic_plane).setContentIntent(pi)
            .setAutoCancel(true).setTimeoutAfter(15000).build())
    }

    private fun startForegroundCompat() {
        val mob = lastMobile ?: Prefs.isMobile(this)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("proxy", "Прокси", NotificationManager.IMPORTANCE_LOW))
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "proxy")
            .setContentTitle("TG WS Proxy работает")
            .setContentText("${Prefs.linkHost(this)}:${Prefs.port(this)} · " +
                (if (mob) "мобильная сеть" else "Wi-Fi") + ", TLS " +
                (if (Prefs.noSecureFor(this, mob)) "выкл" else "вкл"))
            .setSmallIcon(R.drawable.ic_plane)
            .setContentIntent(pi).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    override fun onDestroy() {
        try { wake?.release() } catch (_: Exception) {}
        try { netCb?.let { (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) } } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object { const val ACTION_STOP = "stop" }
}
