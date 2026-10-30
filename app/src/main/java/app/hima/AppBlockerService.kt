package app.hima

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        val blocked: Set<String> = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        if (blocked.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val i = Intent(this, BlockedScreenActivity::class.java)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(i)
                } catch (e: Exception) {
                }
            }, 400)
        }
    }

    override fun onInterrupt() {}
}
