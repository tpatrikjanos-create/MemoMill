package hu.memomill.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Zárképernyő: tanulásra küld, vagy egy kártya helyes megválaszolásával felold. */
class LockActivity : Activity() {
    private var pkg = ""
    private var card: Pair<String, String>? = null
    private var wrong = false
    private var countdown = 0
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra("pkg") ?: ""
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        pkg = i.getStringExtra("pkg") ?: pkg
        card = null
        wrong = false
    }

    override fun onResume() {
        super.onResume()
        if (countdown <= 0) build()
    }

    private fun appName(p: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(p, 0)).toString()
    } catch (_: Exception) {
        p
    }

    private fun build() {
        val a = this
        val root = ScrollView(a).apply { setBackgroundColor(Ui.BG); isFillViewport = true }
        val box = Ui.column(a).apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(box)
        box.addView(ImageView(a).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(Ui.dp(a, 72), Ui.dp(a, 72)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        box.addView(Ui.title(a, "${appName(pkg)} zárolva"))
        val study = LockStore.studyMin(a)
        val goal = LockStore.goalMin(a)
        val bal = LockStore.balance(a)
        box.addView(Ui.text(a, "Mai tanulás: %.1f / %d perc".format(study, goal), 15f, Ui.MUTED))

        when {
            !LockStore.goalMet(a) -> {
                val left = max(1, ceil(goal - study).toInt())
                box.addView(Ui.text(a, "Előbb ismételj még $left percet a MemoMillben, utána ez az app is megnyílik.", 18f))
                box.addView(Ui.button(a, "Ismétlés most", true) { openApp() })
            }
            bal < 1 -> {
                box.addView(Ui.text(a, "Elfogyott a mai appidőd. Minden tanult perc %.1f perc appidőt ad.".format(LockStore.ratio(a)), 18f))
                box.addView(Ui.button(a, "Tanulás most", true) { openApp() })
            }
            else -> cardUi(box, bal)
        }

        if (LockStore.emergencyAvailable(a)) {
            box.addView(Ui.button(a, "Vészkijárat (naponta egyszer, 15 perc)") { emergency() })
        }
        box.addView(Ui.button(a, "Kezdőképernyő") { goHome() })
        setContentView(root)
    }

    private fun cardUi(box: LinearLayout, bal: Int) {
        val a = this
        val grantMin = min(LockStore.sessionMin(a), bal)
        box.addView(Ui.text(a, "Elérhető appidő: $bal perc. Válaszolj egy kártyára, és $grantMin percre feloldom.", 16f, Ui.MUTED))
        val c = card ?: LockStore.randomCard(a).also { card = it }
        if (c == null) {
            box.addView(Ui.button(a, "Feloldás $grantMin percre", true) { unlock(grantMin) })
            return
        }
        box.addView(Ui.title(a, c.first, 22f).apply { setPadding(0, Ui.dp(a, 18), 0, 0) })
        if (wrong) {
            box.addView(Ui.text(a, "Nem talált. A helyes válasz: ${c.second}", 16f, Ui.BAD))
            box.addView(Ui.button(a, "Másik kártya", true) {
                card = null
                wrong = false
                build()
            })
            return
        }
        val inp = Ui.input(a, "A válasz…")
        val check = {
            if (LockStore.matches(inp.text.toString(), c.second)) unlock(grantMin)
            else {
                wrong = true
                build()
            }
        }
        inp.imeOptions = EditorInfo.IME_ACTION_DONE
        inp.setOnEditorActionListener { _, _, _ -> check(); true }
        box.addView(inp)
        box.addView(Ui.button(a, "Ellenőrzés", true) { check() })
        inp.requestFocus()
    }

    private fun unlock(minutes: Int) {
        LockStore.grant(this, minutes)
        Toast.makeText(this, "Feloldva $minutes percre", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun emergency() {
        countdown = 30
        val a = this
        val root = Ui.column(a).apply { setBackgroundColor(Ui.BG); gravity = Gravity.CENTER }
        val t = Ui.title(a, "30", 56f)
        root.addView(Ui.text(a, "Vészkijárat: várj 30 másodpercet. Biztosan szükséged van rá most?", 18f))
        root.addView(t)
        root.addView(Ui.button(a, "Mégse") {
            countdown = 0
            h.removeCallbacksAndMessages(null)
            build()
        })
        setContentView(root)
        val tick = object : Runnable {
            override fun run() {
                if (countdown <= 0) return
                countdown--
                t.text = countdown.toString()
                if (countdown == 0) {
                    LockStore.useEmergency(a)
                    Toast.makeText(a, "Feloldva 15 percre", Toast.LENGTH_SHORT).show()
                    finish()
                } else h.postDelayed(this, 1000)
            }
        }
        h.postDelayed(tick, 1000)
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).putExtra("review", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
