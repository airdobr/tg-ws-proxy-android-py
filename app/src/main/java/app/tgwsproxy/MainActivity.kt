package app.tgwsproxy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var circle: GradientDrawable
    private lateinit var bigBtn: FrameLayout
    private val h = Handler(Looper.getMainLooper())
    private var pendingUntil = 0L   // пока идёт запуск/остановка, показываем «…»
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(filesDir, "proxy.log").appendText("CRASH: ${android.util.Log.getStackTraceString(e)}\n") } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        root.addView(TextView(this).apply { text = "TG WS Proxy"; textSize = 24f; gravity = Gravity.CENTER })
        root.addView(TextView(this).apply {
            text = "Версия ${BuildConfig.VERSION_NAME} · ядро Flowseal ${BuildConfig.UPSTREAM}"; textSize = 12f; gravity = Gravity.CENTER
        })
        val upd = Button(this).apply { visibility = View.GONE }
        root.addView(upd, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        Thread {
            Updater.check()?.let { u ->
                h.post {
                    upd.text = "Доступно обновление ${u.version} — скачать"
                    upd.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.apkUrl))) }
                    upd.visibility = View.VISIBLE
                }
            }
        }.start()

        // Большая круглая кнопка с самолётиком: старт/стоп.
        val size = (200 * dp).toInt()
        circle = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(GREY) }
        bigBtn = FrameLayout(this).apply {
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x55FFFFFF), circle, null)
            elevation = 8 * dp
            isClickable = true
            setOnClickListener { toggle() }
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_plane) },
                FrameLayout.LayoutParams((110 * dp).toInt(), (110 * dp).toInt(), Gravity.CENTER).apply { rightMargin = (8 * dp).toInt() })
        }
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(bigBtn, LinearLayout.LayoutParams(size, size))
        status = TextView(this).apply { textSize = 16f; gravity = Gravity.CENTER; setPadding(0, (20 * dp).toInt(), 0, 0) }
        root.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        fun btn(t: String, f: () -> Unit) = root.addView(Button(this).apply { text = t; setOnClickListener { f() } },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        btn("Применить в Telegram") {
            val u = "tg://proxy?server=${Prefs.linkHost(this)}&port=${Prefs.port(this)}&secret=dd${Prefs.secret(this)}"
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
            catch (e: Exception) { Toast.makeText(this, "Telegram не найден", Toast.LENGTH_SHORT).show() }
        }
        btn("Ссылка для других устройств") { startActivity(Intent(this, ShareLinkActivity::class.java)) }
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "Настройки"; setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "Логи"; setOnClickListener { startActivity(Intent(this@MainActivity, LogActivity::class.java)) } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)
    }

    private fun toggle() {
        if (proxyAlive()) {
            startService(Intent(this, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
        } else {
            File(filesDir, "proxy.log").delete()
            startForegroundService(Intent(this, ProxyService::class.java))
        }
        pendingUntil = System.currentTimeMillis() + 2500
        status.text = "…"
        h.removeCallbacks(tick); h.postDelayed(tick, 700)
    }

    /** Прокси работает в отдельном процессе app.tgwsproxy:proxy — смотрим, жив ли он. */
    private fun proxyAlive(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.runningAppProcesses?.any { it.processName == "$packageName:proxy" } == true
    }

    private val tick = object : Runnable {
        override fun run() {
            val on = proxyAlive()
            circle.setColor(if (on) BLUE else GREY)
            if (System.currentTimeMillis() > pendingUntil)
                status.text = if (on) "Прокси работает\n${Prefs.linkHost(this@MainActivity)}:${Prefs.port(this@MainActivity)}"
                              else "Прокси выключен\nнажмите, чтобы запустить"
            h.postDelayed(this, 1000)
        }
    }
    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }

    companion object {
        const val BLUE = 0xFFF4511E.toInt()  // «включено» — цвет иконки приложения
        const val GREY = 0xFF9E9E9E.toInt()
    }
}
