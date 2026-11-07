package app.hima

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UsageStore {
    private const val FILE = "hima_usage"

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun add(ctx: Context, pkg: String, ms: Long) {
        if (ms <= 0) return
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val day = today()
        val e = p.edit()
        if (p.getString("day", "") != day) {
            e.clear()
            e.putString("day", day)
            e.putLong("t:$pkg", ms)
        } else {
            e.putLong("t:$pkg", p.getLong("t:$pkg", 0L) + ms)
        }
        e.apply()
    }

    fun todayTotals(ctx: Context): Map<String, Long> {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (p.getString("day", "") != today()) return emptyMap()
        val out = HashMap<String, Long>()
        for ((k, v) in p.all) {
            if (k.startsWith("t:") && v is Long) out[k.substring(2)] = v
        }
        return out
    }
}
