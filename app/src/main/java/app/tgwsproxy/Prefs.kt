package app.tgwsproxy

import android.content.Context
import java.security.SecureRandom

object Prefs {
    const val DEFAULT_DC = "2:149.154.167.220\n4:149.154.167.220"
    private fun p(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    // Читаем через all[], чтобы старые значения другого типа (порт-число из 0.1.x) не роняли приложение.
    fun str(c: Context, k: String, d: String) = p(c).all[k]?.toString() ?: d
    fun bool(c: Context, k: String, d: Boolean) = when (val v = p(c).all[k]) {
        is Boolean -> v; is String -> v.toBooleanStrictOrNull() ?: d; else -> d
    }
    fun put(c: Context, k: String, v: Any) = p(c).edit().apply {
        when (v) { is Boolean -> putBoolean(k, v); else -> putString(k, v.toString()) }
    }.apply()

    fun port(c: Context) = str(c, "port", "1443").toIntOrNull() ?: 1443
    fun host(c: Context) = str(c, "host", "127.0.0.1")
    fun linkHost(c: Context) = host(c).let { if (it == "0.0.0.0" || it.isBlank()) "127.0.0.1" else it }
    fun isRunning(c: Context) = p(c).getBoolean("running", false)
    fun running(c: Context, v: Boolean) = p(c).edit().putBoolean("running", v).apply()

    fun newSecret(): String {
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return b.joinToString("") { "%02x".format(it) }
    }
    fun secret(c: Context): String {
        p(c).getString("secret", null)?.let { return it }
        return newSecret().also { put(c, "secret", it) }
    }

    private fun domains(s: String) = s.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Аргументы командной строки ядра, собранные из настроек (как в десктопной версии). */
    fun argv(c: Context): List<String> {
        val a = mutableListOf("--host", host(c), "--port", port(c).toString(), "--secret", secret(c))
        val dc = str(c, "dc", DEFAULT_DC).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (dc.isEmpty()) a += "--dc-ip" else dc.forEach { a += listOf("--dc-ip", it) }
        if (bool(c, "verbose", false)) a += "-v"
        a += listOf("--buf-kb", str(c, "buf_kb", "256"), "--pool-size", str(c, "pool", "4"),
            "--log-max-mb", str(c, "log_mb", "5"))
        if (!bool(c, "cf", true)) a += "--no-cfproxy"
        if (!bool(c, "h2", true)) a += "--no-h2"
        if (bool(c, "no_secure", false)) a += "--no-secure"
        if (bool(c, "cf_user_on", false)) domains(str(c, "cf_user", "")).forEach { a += listOf("--cfproxy-domain", it) }
        if (bool(c, "cf_worker_on", false)) domains(str(c, "cf_worker", "")).forEach { a += listOf("--cfproxy-worker-domain", it) }
        return a
    }
}
