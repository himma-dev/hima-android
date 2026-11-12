package app.hima

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

object LockHelper {
    fun isLocked(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("hima", Context.MODE_PRIVATE)
        if (!p.getBoolean("lock_on", false)) return false
        val until = p.getLong("lock_until", 0L)
        if (until > 0L && System.currentTimeMillis() >= until) {
            p.edit().putBoolean("lock_on", false).putLong("lock_until", 0L).apply()
            return false
        }
        return true
    }

    // minutes = 0 يعني: قفل حتى يفكه الأب بالرقم السري
    fun setLocked(ctx: Context, on: Boolean, minutes: Int = 0) {
        val until = if (on && minutes > 0) System.currentTimeMillis() + minutes * 60000L else 0L
        ctx.getSharedPreferences("hima", Context.MODE_PRIVATE)
            .edit().putBoolean("lock_on", on).putLong("lock_until", until).apply()
    }

    // تطبيقات يُسمح بها أثناء القفل: الاتصال والرسائل النصية وشاشة المكالمات
    fun allowedWhenLocked(ctx: Context, pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.phone") return true
        if (pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("emergency")) return true
        if (pkg.contains("messaging") || pkg.contains("mms") || pkg.contains("dialer") || pkg.contains("contacts")) return true
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage == pkg) return true
        } catch (e: Exception) {
        }
        try {
            if (Telephony.Sms.getDefaultSmsPackage(ctx) == pkg) return true
        } catch (e: Exception) {
        }
        return false
    }
}
