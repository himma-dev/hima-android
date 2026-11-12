package app.hima

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager

object LockHelper {
    fun isLocked(ctx: Context): Boolean =
        ctx.getSharedPreferences("hima", Context.MODE_PRIVATE).getBoolean("lock_on", false)

    fun setLocked(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("hima", Context.MODE_PRIVATE)
            .edit().putBoolean("lock_on", on).apply()
    }

    // تطبيقات يُسمح بها أثناء القفل: الاتصال والرسائل النصية وشاشة المكالمات
    fun allowedWhenLocked(ctx: Context, pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.phone") return true
        if (pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("emergency")) return true
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
