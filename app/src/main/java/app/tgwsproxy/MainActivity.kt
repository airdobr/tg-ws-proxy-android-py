package app.tgwsproxy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var log: TextView
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "proxy.log").appendText("CRASH: ${android.util.Log.getStackTraceString(e)}\n") } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { f() }; root.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(TextView(this).apply { text = "TG WS Proxy"; textSize = 22f })
        root.addView(TextView(this).apply { text = "Версия ${BuildConfig.VERSION_NAME} · ядро Flowseal/tg-ws-proxy ${BuildConfig.UPSTREAM}" })
        val upd = Button(this).apply { visibility = android.view.View.GONE }
        root.addView(upd, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        Thread {
            Updater.check()?.let { u ->
                h.post {
                    upd.text = "Доступно обновление ${u.version} — скачать"
                    upd.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl))) }
                    upd.visibility = android.view.View.VISIBLE
                }
            }
        }.start()
        status = TextView(this).apply { textSize = 16f; setPadding(0, 24, 0, 8) }
        root.addView(status)
        btn("Запустить") {
            File(filesDir, "proxy.log").delete()
            startForegroundService(Intent(this, ProxyService::class.java))
        }
        btn("Настройки") { startActivity(Intent(this, SettingsActivity::class.java)) }
        btn("Остановить") { startService(Intent(this, ProxyService::class.java).setAction(ProxyService.ACTION_STOP)) }
        btn("Применить в Telegram") {
            val u = "tg://proxy?server=${Prefs.linkHost(this)}&port=${Prefs.port(this)}&secret=dd${Prefs.secret(this)}"
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
            catch (e: Exception) { Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show() }
        }
        btn("Поделиться логом") {
            val f = File(filesDir, "proxy.log")
            val t = if (f.exists()) f.readText().takeLast(20000) else "лог пуст"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Лог"))
        }
        log = TextView(this).apply {
            textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(16, 16, 16, 16)
        }
        root.addView(ScrollView(this).apply { setBackgroundColor(0xFFDDDDDD.toInt()); addView(log) },
            LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = 16 })
        setContentView(root)
    }

    private val tick = object : Runnable {
        override fun run() {
            status.text = if (Prefs.isRunning(this@MainActivity)) "Статус: работает" else "Статус: остановлен"
            val f = File(filesDir, "proxy.log")
            log.text = if (f.exists()) f.readLines().takeLast(60).joinToString("\n") else "Лог пуст. Нажмите «Запустить»."
            h.postDelayed(this, 1500)
        }
    }
    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
}
