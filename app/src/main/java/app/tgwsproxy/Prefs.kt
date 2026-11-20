package app.tgwsproxy

import android.content.Context
import java.security.SecureRandom

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun port(c: Context) = p(c).getInt("port", 1443)
    fun setPort(c: Context, v: Int) = p(c).edit().putInt("port", v).apply()
    fun isRunning(c: Context) = p(c).getBoolean("running", false)
    fun running(c: Context, v: Boolean) = p(c).edit().putBoolean("running", v).apply()
    fun secret(c: Context): String {
        p(c).getString("secret", null)?.let { return it }
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val s = b.joinToString("") { "%02x".format(it) }
        p(c).edit().putString("secret", s).apply()
        return s
    }
}
