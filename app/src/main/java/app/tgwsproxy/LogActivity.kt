package app.tgwsproxy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

class LogActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private val h = Handler(Looper.getMainLooper())
    private val file get() = File(filesDir, "proxy.log")

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        root.addView(Button(this).apply { text = "← Назад"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.addView(TextView(this).apply { text = "Логи"; textSize = 22f })
        val row = LinearLayout(this)
        row.addView(Button(this).apply {
            text = "Поделиться"
            setOnClickListener {
                val t = if (file.exists()) file.readText().takeLast(20000) else "лог пуст"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Лог"))
            }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "Очистить"; setOnClickListener { file.writeText(""); refresh() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row)
        log = TextView(this).apply {
            textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true); setPadding(16, 16, 16, 16)
        }
        scroll = ScrollView(this).apply { setBackgroundColor(0xFFDDDDDD.toInt()); addView(log) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = 16 })
        setContentView(root)
    }

    private fun refresh() {
        val t = if (file.exists()) file.readLines().takeLast(300).joinToString("\n") else ""
        if (t != log.text.toString()) {
            log.text = t.ifEmpty { "Лог пуст." }
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
    private val tick = object : Runnable { override fun run() { refresh(); h.postDelayed(this, 1500) } }
    override fun onResume() { super.onResume(); h.post(tick) }
    override fun onPause() { super.onPause(); h.removeCallbacks(tick) }
}
