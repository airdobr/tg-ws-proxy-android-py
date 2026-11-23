package app.tgwsproxy

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface

/** Ссылка на прокси для других устройств в той же сети (iPhone, ПК, другой телефон). */
class ShareLinkActivity : Activity() {
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (40 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        root.addView(Button(this).apply { text = "← Назад"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.addView(TextView(this).apply { text = "Ссылка для других устройств"; textSize = 22f })
        root.addView(TextView(this).apply {
            textSize = 14f; setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            text = "Другое устройство должно быть в той же Wi-Fi-сети (или подключено к точке доступа этого телефона). " +
                "На iPhone наведите камеру на QR-код — откроется Telegram с предложением включить прокси. " +
                "Этот телефон должен оставаться с запущенным прокси."
        })

        val host = Prefs.host(this)
        if (host != "0.0.0.0") {
            root.addView(TextView(this).apply {
                textSize = 14f; setTextColor(0xFFE64A19.toInt())
                text = "Сейчас прокси принимает подключения только с этого телефона ($host). " +
                    "Чтобы подключались другие устройства, нужно разрешить подключения из сети."
            })
            root.addView(Button(this).apply {
                text = "Разрешить подключения из сети"
                setOnClickListener { allowLan() }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        val ips = lanAddresses()
        if (ips.isEmpty()) {
            root.addView(TextView(this).apply {
                textSize = 15f; setPadding(0, (16 * dp).toInt(), 0, 0)
                text = "Телефон не подключён к Wi-Fi и не раздаёт точку доступа. " +
                    "По мобильной сети другие устройства подключиться не смогут."
            })
        }
        for ((label, ip) in ips) {
            val link = "https://t.me/proxy?server=$ip&port=${Prefs.port(this)}&secret=dd${Prefs.secret(this)}"
            root.addView(TextView(this).apply {
                text = "$label: $ip"; textSize = 16f; setPadding(0, (16 * dp).toInt(), 0, (4 * dp).toInt())
            })
            val qr = ImageView(this).apply { setImageBitmap(qr(link, (240 * dp).toInt())) }
            root.addView(qr, LinearLayout.LayoutParams((240 * dp).toInt(), (240 * dp).toInt()).apply { gravity = Gravity.CENTER_HORIZONTAL })
            root.addView(TextView(this).apply { text = link; textSize = 12f; setTextIsSelectable(true) })
            val row = LinearLayout(this)
            row.addView(Button(this).apply {
                text = "Копировать"
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("proxy", link))
                    Toast.makeText(this@ShareLinkActivity, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Поделиться"
                setOnClickListener {
                    startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link), "Ссылка на прокси"))
                }
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            root.addView(row)
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    /** Ставит 0.0.0.0 и, если прокси работает, перезапускает его. */
    private fun allowLan() {
        Prefs.put(this, "host", "0.0.0.0")
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val running = am.runningAppProcesses?.any { it.processName == "$packageName:proxy" } == true
        if (running) {
            startService(Intent(this, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
            Handler(Looper.getMainLooper()).postDelayed({
                startForegroundService(Intent(this, ProxyService::class.java))
                Toast.makeText(this, "Прокси перезапущен и доступен в сети", Toast.LENGTH_SHORT).show()
            }, 1500)
        } else {
            Toast.makeText(this, "Готово. Запустите прокси", Toast.LENGTH_SHORT).show()
        }
        render()
    }

    /** Локальные IPv4-адреса Wi-Fi / точки доступа / USB; мобильный IP оператора пропускаем. */
    private fun lanAddresses(): List<Pair<String, String>> {
        val res = mutableListOf<Pair<String, String>>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") || name.startsWith("dummy")) continue
                for (a in ni.inetAddresses) {
                    if (a !is Inet4Address || !a.isSiteLocalAddress) continue
                    val label = when {
                        name.startsWith("wlan") && res.none { it.first == "Wi-Fi" } -> "Wi-Fi"
                        name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") || name.startsWith("wlan") -> "Точка доступа"
                        name.startsWith("rndis") || name.startsWith("usb") -> "USB-модем"
                        else -> name
                    }
                    res += label to a.hostAddress!!
                }
            }
        } catch (_: Exception) {}
        return res
    }

    private fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { if (m[it % size, it / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }
}
