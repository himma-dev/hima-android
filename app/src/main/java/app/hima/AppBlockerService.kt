package app.hima

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        if (blocked.contains(pkg)) {
            performGlobalA
