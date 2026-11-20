package app.tgwsproxy

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Проверка новой версии в GitHub Releases этого репозитория. */
object Updater {
    data class Update(val version: String, val apkUrl: String, val pageUrl: String)

    private fun parse(v: String) = v.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
    private fun newer(a: String, b: String): Boolean {
        val x = parse(a); val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrNull(i) ?: 0) - (y.getOrNull(i) ?: 0)
            if (d != 0) return d > 0
        }
        return false
    }

    /** Блокирующий вызов, запускать не в UI-потоке. null — обновлений нет или сеть недоступна. */
    fun check(): Update? = try {
        val c = URL("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = j.getString("tag_name")
        if (!newer(tag, BuildConfig.VERSION_NAME)) null else {
            val assets = j.getJSONArray("assets")
            var apk = j.getString("html_url")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
            }
            Update(tag, apk, j.getString("html_url"))
        }
    } catch (e: Exception) { null }
}
