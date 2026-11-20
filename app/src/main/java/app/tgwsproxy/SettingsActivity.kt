package app.tgwsproxy

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class SettingsActivity : Activity() {
    private val getters = mutableListOf<() -> Unit>()
    private lateinit var root: LinearLayout
    private lateinit var secretEdit: EditText
    private val dp get() = resources.displayMetrics.density

    private fun section(t: String) {
        root.addView(TextView(this).apply { text = t; textSize = 16f; setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (20 * dp).toInt(), 0, (4 * dp).toInt()) })
    }
    private fun label(t: String) = root.addView(TextView(this).apply { text = t; textSize = 12f; setPadding(0, (8 * dp).toInt(), 0, 0) })
    private fun entry(key: String, def: String, number: Boolean = false, multiline: Boolean = false, hint: String? = null): EditText {
        val e = EditText(this).apply {
            setText(Prefs.str(this@SettingsActivity, key, def))
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
            if (multiline) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3 }
            this.hint = hint
        }
        root.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        getters += { Prefs.put(this, key, e.text.toString().trim()) }
        return e
    }
    private fun check(key: String, text: String, def: Boolean): CheckBox {
        val c = CheckBox(this).apply { this.text = text; isChecked = Prefs.bool(this@SettingsActivity, key, def) }
        root.addView(c)
        getters += { Prefs.put(this, key, c.isChecked) }
        return c
    }
    private fun domainRow(onKey: String, key: String, text: String, hint: String) {
        val c = check(onKey, text, false)
        val e = entry(key, "", hint = hint)
        e.isEnabled = c.isChecked
        c.setOnCheckedChangeListener { _, v -> e.isEnabled = v }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        root.addView(Button(this).apply { text = "← Назад"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        section("MTProto-прокси")
        label("IP-адрес (127.0.0.1 — только это устройство, 0.0.0.0 — все интерфейсы)")
        entry("host", "127.0.0.1")
        label("Порт")
        entry("port", "1443", number = true)
        label("Ключ (32 hex-символа)")
        secretEdit = EditText(this).apply { setText(Prefs.secret(this@SettingsActivity)) }
        val row = LinearLayout(this)
        row.addView(secretEdit, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "↺"; setOnClickListener { secretEdit.setText(Prefs.newSecret()) } })
        root.addView(row)
        getters += { Prefs.put(this, "secret", secretEdit.text.toString().trim().lowercase()) }

        section("Датацентры Telegram (DC → IP)")
        label("По одному правилу на строку, формат: номер:IP")
        entry("dc", Prefs.DEFAULT_DC, multiline = true)

        section("Cloudflare Proxy")
        val cf = check("cf", "Включить CF-прокси", true)
        val h2 = check("h2", "Мультиплексирование медиа (HTTP/2)", true)
        val ns = check("no_secure", "Выключить TLS для CF-прокси и CF-worker (всегда)", false)
        check("auto_mobile", "Автоматически выключать TLS в мобильной сети и включать на Wi-Fi", true)
        fun sync() { h2.isEnabled = cf.isChecked && !ns.isChecked }
        cf.setOnCheckedChangeListener { _, _ -> sync() }; ns.setOnCheckedChangeListener { _, _ -> sync() }; sync()
        domainRow("cf_user_on", "cf_user", "Свой домен", "домены через запятую")

        section("Cloudflare Worker")
        domainRow("cf_worker_on", "cf_worker", "Свой домен", "name.account.workers.dev, через запятую")

        section("Логи и производительность")
        check("verbose", "Подробное логирование (verbose)", false)
        label("Буфер, КБ (по умолчанию 256)"); entry("buf_kb", "256", number = true)
        label("Пул WebSocket-сессий (по умолчанию 4)"); entry("pool", "4", number = true)
        label("Макс. размер лога, МБ (по умолчанию 5)"); entry("log_mb", "5", number = true)

        root.addView(Button(this).apply {
            text = "Сохранить"; setOnClickListener { save() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = (24 * dp).toInt() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun err(m: String) { Toast.makeText(this, m, Toast.LENGTH_LONG).show() }

    private fun save() {
        getters.forEach { it() }
        val port = Prefs.str(this, "port", "").toIntOrNull()
        if (port == null || port !in 1..65535) return err("Порт должен быть числом 1-65535")
        val sec = Prefs.str(this, "secret", "")
        if (!Regex("[0-9a-f]{32}").matches(sec)) return err("Ключ: ровно 32 hex-символа (0-9, a-f)")
        for (l in Prefs.str(this, "dc", "").lines().map { it.trim() }.filter { it.isNotEmpty() })
            if (!Regex("\\d+:[0-9a-fA-F.:]+").matches(l)) return err("Неверный формат DC:IP: $l")
        for (k in listOf("buf_kb", "pool", "log_mb"))
            if (Prefs.str(this, k, "").toIntOrNull() == null) return err("Числовое поле заполнено неверно")
        err(if (Prefs.isRunning(this)) "Сохранено. Остановите и запустите прокси заново" else "Сохранено")
        finish()
    }
}
