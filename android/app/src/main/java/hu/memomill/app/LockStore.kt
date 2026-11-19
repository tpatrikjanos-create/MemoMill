package hu.memomill.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Az alkalmazászár állapota és szabályai.
 *
 * - A webes app jelenti a mai tanulási időt (az összes eszközről, szinkron után).
 * - Amíg a napi cél (alapból 5 perc) nincs meg, a zárolt appok nem nyílnak meg.
 * - Utána minden tanult perc [ratio] perc appidőt ad; egy feloldáshoz egy kártyát
 *   kell helyesen megválaszolni, és egy feloldás [sessionMin] percig tart.
 * - Vészkijárat: naponta egyszer 15 perc, 30 másodperc várakozás után.
 */
object LockStore {
    val DISTRACT = listOf(
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.instagram.android",
        "com.facebook.katana", "com.facebook.lite", "com.google.android.youtube",
        "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage",
        "com.netflix.mediaclient", "com.pinterest", "tv.twitch.android.app"
    )
    private val ALWAYS = setOf(
        "android", "com.android.systemui", "com.android.settings",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.google.android.gms", "com.android.phone", "com.android.server.telecom"
    )

    private fun p(c: Context) = c.getSharedPreferences("memomill_lock", Context.MODE_PRIVATE)
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun enabled(c: Context) = p(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = p(c).edit().putBoolean("enabled", v).apply()

    /** "list": csak a kijelöltek zároltak; "all": minden zárolt, kivéve az alapvetőket és a kijelölteket */
    fun mode(c: Context): String = p(c).getString("mode", "list") ?: "list"
    fun setMode(c: Context, v: String) = p(c).edit().putString("mode", v).apply()

    fun picked(c: Context): Set<String> = HashSet(p(c).getStringSet("picked", emptySet()) ?: emptySet())
    fun setPicked(c: Context, s: Set<String>) =
        p(c).edit().putStringSet("picked", HashSet(s)).putBoolean("pickedInit", true).apply()
    fun pickedInit(c: Context) = p(c).getBoolean("pickedInit", false)

    fun ratio(c: Context): Float = p(c).getFloat("ratio", 1f)
    fun sessionMin(c: Context): Int = p(c).getInt("session", 5)
    fun setNumbers(c: Context, ratio: Float, session: Int) =
        p(c).edit().putFloat("ratio", max(0.1f, ratio)).putInt("session", max(1, session)).apply()

    fun saveStatus(c: Context, json: String) {
        try {
            val o = JSONObject(json)
            p(c).edit()
                .putFloat("todaySec", o.optDouble("todaySec", 0.0).toFloat())
                .putInt("goalMin", o.optInt("goalMin", 5))
                .putString("statDay", o.optString("day", today()))
                .apply()
        } catch (_: Exception) {
        }
    }

    fun saveCards(c: Context, json: String) = p(c).edit().putString("cards", json).apply()

    fun randomCard(c: Context): Pair<String, String>? {
        return try {
            val a = JSONArray(p(c).getString("cards", "[]"))
            if (a.length() == 0) return null
            val o = a.getJSONObject((0 until a.length()).random())
            Pair(o.optString("front"), o.optString("back"))
        } catch (_: Exception) {
            null
        }
    }

    fun goalMin(c: Context): Int = p(c).getInt("goalMin", 5)
    fun studyMin(c: Context): Float =
        if (p(c).getString("statDay", "") == today()) p(c).getFloat("todaySec", 0f) / 60f else 0f
    fun goalMet(c: Context) = studyMin(c) >= goalMin(c)
    fun usedMin(c: Context): Int = if (p(c).getString("usedDay", "") == today()) p(c).getInt("used", 0) else 0
    fun balance(c: Context): Int = floor(studyMin(c) * ratio(c)).toInt() - usedMin(c)
    fun unlockUntil(c: Context): Long = p(c).getLong("until", 0L)

    fun grant(c: Context, minutes: Int) {
        p(c).edit()
            .putString("usedDay", today())
            .putInt("used", usedMin(c) + minutes)
            .putLong("until", System.currentTimeMillis() + minutes * 60000L)
            .apply()
    }

    fun emergencyAvailable(c: Context) = p(c).getString("emDay", "") != today()
    fun useEmergency(c: Context) {
        p(c).edit().putString("emDay", today()).putLong("until", System.currentTimeMillis() + 15 * 60000L).apply()
    }

    fun launchable(c: Context): Set<String> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return c.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }.toHashSet()
    }

    fun imes(c: Context): Set<String> {
        val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.map { it.packageName }.toHashSet()
    }

    fun essentials(c: Context): Set<String> {
        val s = HashSet<String>(ALWAYS)
        s.add(c.packageName)
        val pm = c.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        )
        intents.forEach { i ->
            try {
                pm.queryIntentActivities(i, 0).forEach { s.add(it.activityInfo.packageName) }
            } catch (_: Exception) {
            }
        }
        s.addAll(imes(c))
        return s
    }

    fun isLockedPkg(c: Context, pkg: String, ess: Set<String>, launch: Set<String>): Boolean {
        if (!enabled(c) || pkg in ess) return false
        return if (mode(c) == "all") pkg in launch && pkg !in picked(c) else pkg in picked(c)
    }

    /* ékezet, kis-nagybetű és írásjel nélküli összevetés, mint a webes appban */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun matches(answer: String, expected: String): Boolean {
        val a = fold(answer)
        val b = fold(expected)
        if (a.isEmpty()) return false
        if (a == b) return true
        val d = lev(a, b)
        return 1.0 - d.toDouble() / max(a.length, b.length) >= 0.8
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
