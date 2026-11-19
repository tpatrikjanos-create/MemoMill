package hu.memomill.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Figyeli, melyik app kerül előtérbe, és zárolt appnál a zárképernyőt mutatja. */
class LockService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var ess: Set<String> = emptySet()
    private var launch: Set<String> = emptySet()
    private var imes: Set<String> = emptySet()
    private var cacheAt = 0L
    private var cur = ""
    private val recheck = Runnable { if (cur.isNotEmpty()) check(cur) }

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (ess.isNotEmpty() && now - cacheAt < 10 * 60000L) return
        ess = LockStore.essentials(this)
        launch = LockStore.launchable(this)
        imes = LockStore.imes(this)
        cacheAt = now
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        refresh()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        refresh()
        if (pkg in imes || pkg == "com.android.systemui") return
        cur = pkg
        if (pkg == packageName) {
            h.removeCallbacks(recheck)
            return
        }
        check(pkg)
    }

    private fun check(pkg: String) {
        h.removeCallbacks(recheck)
        if (pkg != cur) return
        if (!LockStore.isLockedPkg(this, pkg, ess, launch)) return
        val now = System.currentTimeMillis()
        val until = LockStore.unlockUntil(this)
        if (now < until) {
            h.postDelayed(recheck, until - now + 500)
            return
        }
        val i = Intent(this, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("pkg", pkg)
        try {
            startActivity(i)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {}
}
