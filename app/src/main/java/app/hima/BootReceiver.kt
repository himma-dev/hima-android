package app.hima

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    private fun say(c: Context, s: String) {
        Toast.makeText(c, "حمى: " + s, Toast.LENGTH_LONG).show()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        say(context, "وصلت إشارة " + intent?.action)
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("hima", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("protect_on", false)) {
            say(context, "الحماية غير مسجلة")
            return
        }
        if (VpnService.prepare(context) != null) {
            say(context, "موافقة VPN مفقودة")
            return
        }
        try {
            context.startService(Intent(context, HimaVpnService::class.java))
            say(context, "تم طلب تشغيل الحماية")
        } catch (e: Exception) {
            say(context, "خطأ: " + e.message)
        }
    }
}
