package hu.memomill.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.provider.Settings
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/** Az alkalmazászár beállításai: engedély, mód, arány, zárolt appok. */
class LockSettingsActivity : Activity() {

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun serviceOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any {
            val cn = ComponentName.unflattenFromString(it)
            cn != null && cn.packageName == packageName && cn.className == LockService::class.java.name
        }
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun build() {
        val a = this
        if (!LockStore.pickedInit(a)) {
            val inst = LockStore.launchable(a)
            LockStore.setPicked(a, LockStore.DISTRACT.filter { it in inst }.toSet())
        }
        val tint = ColorStateList.valueOf(Ui.ACCENT)
        val root = ScrollView(a).apply { setBackgroundColor(Ui.BG) }
        val box = Ui.column(a)
        root.addView(box)
        box.addView(Ui.title(a, "Alkalmazászár"))
        box.addView(Ui.text(a, "A kijelölt appok csak a napi ismétlés után nyílnak meg, utána pedig egy kártya megválaszolásával, a tanult időddel arányosan.", 15f, Ui.MUTED))

        val on = serviceOn()
        box.addView(Ui.text(a, if (on) "✓ A zár szolgáltatás engedélyezve." else "A működéshez kapcsold be a „MemoMill alkalmazászár” szolgáltatást a Kisegítő lehetőségek között.", 16f, if (on) Ui.OK else Ui.BAD))
        if (!on) box.addView(Ui.button(a, "Kisegítő lehetőségek megnyitása", true) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        box.addView(Switch(a).apply {
            text = "Zár bekapcsolva"
            textSize = 18f
            setTextColor(Ui.TEXT)
            isChecked = LockStore.enabled(a)
            thumbTintList = tint
            layoutParams = Ui.lp(a, 18)
            setOnCheckedChangeListener { _, v -> LockStore.setEnabled(a, v) }
        })

        val study = LockStore.studyMin(a)
        box.addView(Ui.text(a, "Ma: %.1f perc tanulás · cél %d perc · elérhető appidő %d perc".format(study, LockStore.goalMin(a), maxOf(0, LockStore.balance(a))), 14f, Ui.MUTED, center = false))

        val modeAll = LockStore.mode(a) == "all"
        val rg = RadioGroup(a).apply { layoutParams = Ui.lp(a, 16) }
        val r1 = RadioButton(a).apply { id = 1; text = "Csak a kijelölt appok zárolva"; setTextColor(Ui.TEXT); buttonTintList = tint }
        val r2 = RadioButton(a).apply { id = 2; text = "Minden app zárolva, kivéve az alapvetőket és a kijelölteket"; setTextColor(Ui.TEXT); buttonTintList = tint }
        rg.addView(r1)
        rg.addView(r2)
        rg.check(if (modeAll) 2 else 1)
        rg.setOnCheckedChangeListener { _, id ->
            LockStore.setMode(a, if (id == 2) "all" else "list")
            build()
        }
        box.addView(rg)

        box.addView(Ui.text(a, "Appidő tanult percenként (pl. 1 = 5 perc tanulás → 5 perc app)", 14f, Ui.MUTED, center = false))
        val ratio = Ui.input(a, "1", true).apply { setText(LockStore.ratio(a).toString()) }
        box.addView(ratio)
        box.addView(Ui.text(a, "Egy feloldás hossza (perc)", 14f, Ui.MUTED, center = false))
        val sess = Ui.input(a, "5", true).apply { setText(LockStore.sessionMin(a).toString()) }
        box.addView(sess)
        box.addView(Ui.button(a, "Számok mentése") {
            LockStore.setNumbers(a, ratio.text.toString().replace(',', '.').toFloatOrNull() ?: 1f, sess.text.toString().toIntOrNull() ?: 5)
            Toast.makeText(a, "Mentve", Toast.LENGTH_SHORT).show()
        })

        box.addView(Ui.title(a, if (modeAll) "Mindig engedélyezett appok" else "Zárolt appok", 20f).apply { setPadding(0, Ui.dp(a, 20), 0, 0) })
        if (modeAll) box.addView(Ui.text(a, "A telefon, üzenetek, kamera, óra, térkép, beállítások és a billentyűzet mindig nyitva marad.", 14f, Ui.MUTED, center = false))
        val picked = LockStore.picked(a).toMutableSet()
        val pm = packageManager
        val apps = LockStore.launchable(a).filter { it != packageName }.map { pkg ->
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            Pair(pkg, label)
        }.sortedWith(compareBy({ if (it.first in LockStore.DISTRACT) 0 else 1 }, { it.second.lowercase() }))
        apps.forEach { (pkg, label) ->
            box.addView(CheckBox(a).apply {
                text = label
                textSize = 16f
                setTextColor(Ui.TEXT)
                buttonTintList = tint
                isChecked = pkg in picked
                layoutParams = Ui.lp(a, 4)
                setOnCheckedChangeListener { _, v ->
                    if (v) picked.add(pkg) else picked.remove(pkg)
                    LockStore.setPicked(a, picked)
                }
            })
        }
        setContentView(root)
    }
}
