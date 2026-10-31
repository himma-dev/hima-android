package app.hima

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class AppBlockerService : AccessibilityService() {

    private var overlay: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
                    val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        val blocked: Set<String> = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        if (blocked.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            showOverlay()
        }
    }
override fun onServiceConnected() {
    super.onServiceConnected()
    val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("protect_on", false)) return
    if (HimaVpnService.running) return
    if (android.net.VpnService.prepare(this) != null) return
    try {
        startService(android.content.Intent(this, HimaVpnService::class.java))
        Toast.makeText(this, "حمى: تشغيل الحماية تلقائياً", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(this, "حمى خطأ: " + e.message, Toast.LENGTH_LONG).show()
    }
}
    private fun showOverlay() {
        if (overlay != null) return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val root = LinearLayout(this)
            root.orientation = LinearLayout.VERTICAL
            root.gravity = Gravity.CENTER
            root.setBackgroundColor(Color.WHITE)
            root.setPadding(48, 48, 48, 48)

            val icon = TextView(this)
            icon.text = "🚫"
            icon.textSize = 64f
            icon.gravity = Gravity.CENTER
            root.addView(icon)

            val msg = TextView(this)
            msg.text = "هذا التطبيق محظور من قِبل ولي الأمر"
            msg.textSize = 22f
            msg.setTextColor(Color.BLACK)
            msg.gravity = Gravity.CENTER
            msg.setPadding(0, 32, 0, 48)
            root.addView(msg)

            val ok = Button(this)
            ok.text = "حسناً"
            ok.setOnClickListener { hideOverlay() }
            root.addView(ok)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(root, params)
            overlay = root
            handler.postDelayed({ hideOverlay() }, 3000)
        } catch (e: Exception) {
            
        }
    }

    private fun hideOverlay() {
        handler.removeCallbacksAndMessages(null)
        val v = overlay ?: return
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
        }
        overlay = null
    }

    override fun onInterrupt() {}
            }
