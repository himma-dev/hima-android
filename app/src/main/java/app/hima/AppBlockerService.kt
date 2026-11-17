package app.hima

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class AppBlockerService : AccessibilityService() {

    private var overlay: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tickHandler = Handler(Looper.getMainLooper())

    // قياس وقت الاستخدام
    private var curPkg: String? = null
    private var curStart: Long = 0L
    private var lastPkg: String? = null
    private var receiverOn = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopCounting()
                Intent.ACTION_USER_PRESENT -> {
                    val p = lastPkg
                    if (p != null) {
                        curPkg = p
                        curStart = SystemClock.elapsedRealtime()
                        tickHandler.removeCallbacks(tick)
                        tickHandler.postDelayed(tick, 15000L)
                    }
                }
            }
        }
    }

    private fun stopCounting() {
        val p = curPkg
        if (p != null) {
            val ms = SystemClock.elapsedRealtime() - curStart
            // سقف ساعة واحدة لكل فترة لتفادي الأخطاء
            UsageStore.add(this, p, minOf(ms, 3600000L))
        }
        curPkg = null
    }

    private fun startCounting(pkg: String) {
        if (pkg == curPkg) return
        stopCounting()
        curPkg = pkg
        lastPkg = pkg
        curStart = SystemClock.elapsedRealtime()
        tickHandler.removeCallbacks(tick)
        tickHandler.postDelayed(tick, 15000L)
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = curPkg ?: return
            if (limitReached(p)) {
                stopCounting()
                lastPkg = null
                performGlobalAction(GLOBAL_ACTION_HOME)
                showOverlay("انتهى الوقت المسموح لهذا التطبيق اليوم")
                return
            }
            tickHandler.postDelayed(this, 15000L)
        }
    }

    private fun limitReached(pkg: String): Boolean {
        val limit = UsageStore.getLimit(this, pkg)
        if (limit <= 0) return false
        var used = UsageStore.todayTotals(this)[pkg] ?: 0L
        if (curPkg == pkg) used += SystemClock.elapsedRealtime() - curStart
        return used >= limit * 60000L
    }

    private fun isLauncher(pkg: String): Boolean {
        if (pkg.contains("launcher")) return true
        return try {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val r = packageManager.resolveActivity(i, 0)
            r?.activityInfo?.packageName == pkg
        } catch (e: Exception) {
            false
        }
    }

    private val guardedPkgs = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.samsung.android.permissioncontroller"
    )

    private fun guardSettings(pkg: String): Boolean {
        if (!guardedPkgs.contains(pkg)) return false
        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() < prefs.getLong("settings_open_until", 0L)) return false
        val root = rootInActiveWindow ?: return false
        val hit = try {
            root.findAccessibilityNodeInfosByText("حمى").isNotEmpty() ||
                root.findAccessibilityNodeInfosByText("Hima").isNotEmpty()
        } catch (e: Exception) {
            false
        }
        if (!hit) return false
        stopCounting()
        lastPkg = null
        performGlobalAction(GLOBAL_ACTION_HOME)
        showOverlay("هذه الصفحة محمية من ولي الأمر")
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg.contains("honeyboard") || pkg.contains("keyboard")) return
        // نوافذ النظام ولوحة المفاتيح لا تغيّر التطبيق الحالي
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return

        // حماية صفحات الإعدادات الخاصة بالتطبيق
        if (guardSettings(pkg)) return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (pkg == packageName || isLauncher(pkg)) {
            stopCounting()
            lastPkg = null
            return
        }

        if (LockHelper.isLocked(this) && !LockHelper.allowedWhenLocked(this, pkg)) {
            stopCounting()
            lastPkg = null
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay("الهاتف مقفل من ولي الأمر")
            return
        }

        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        val blocked: Set<String> = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        if (blocked.contains(pkg)) {
            stopCounting()
            lastPkg = null
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay("هذا التطبيق محظور من قِبل ولي الأمر")
            return
        }

        if (limitReached(pkg)) {
            stopCounting()
            lastPkg = null
            performGlobalAction(GLOBAL_ACTION_HOME)
            showOverlay("انتهى الوقت المسموح لهذا التطبيق اليوم")
            return
        }

        startCounting(pkg)
    }
        override fun onServiceConnected() {
        super.onServiceConnected()

        // مهلة قصيرة بعد التفعيل حتى لا تُغلق صفحة الإعدادات أثناء الإعداد
        val gp = getSharedPreferences("hima", Context.MODE_PRIVATE)
        val grace = System.currentTimeMillis() + 120000L
        if (gp.getLong("settings_open_until", 0L) < grace) {
            gp.edit().putLong("settings_open_until", grace).apply()
        }

        if (!receiverOn) {
            try {
                val f = IntentFilter()
                f.addAction(Intent.ACTION_SCREEN_OFF)
                f.addAction(Intent.ACTION_USER_PRESENT)
                if (Build.VERSION.SDK_INT >= 33) {
                    registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(screenReceiver, f)
                }
                receiverOn = true
            } catch (e: Exception) {
            }
        }

        val prefs = getSharedPreferences("hima", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("protect_on", false)) return
        if (HimaVpnService.running) return
        if (VpnService.prepare(this) != null) return
        try {
            startService(Intent(this, HimaVpnService::class.java))
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        stopCounting()
        tickHandler.removeCallbacksAndMessages(null)
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
            }
            receiverOn = false
        }
        super.onDestroy()
    }

    private fun showOverlay(text: String) {
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
            msg.text = text
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

    override fun onInterrupt() {
        stopCounting()
    }
}
