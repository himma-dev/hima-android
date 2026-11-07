package app.hima

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BlockedAppsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hima", Context.MODE_PRIVATE) }

    private fun fmt(ms: Long): String {
        val min = ms / 60000L
        if (min < 1L) return "أقل من دقيقة"
        val h = min / 60L
        val m = min % 60L
        return if (h > 0L) "$h س $m د" else "$m د"
    }

    private fun limitText(min: Int): String {
        if (min <= 0) return "⏱ حد يومي: بدون حد"
        return "⏱ حد يومي: " + durText(min)
    }

    private fun durText(min: Int): String {
        if (min < 60) return "$min دقيقة"
        val h = min / 60
        val m = min % 60
        return if (m == 0) "$h س" else "$h س $m د"
    }

    private fun pickLimit(name: String, pkg: String, view: TextView) {
        val start = UsageStore.getLimit(this, pkg)
        val cur = intArrayOf(if (start > 0) start else 60)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 32, 48, 16)

        val tv = TextView(this)
        tv.textSize = 28f
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 0, 0, 24)
        tv.text = durText(cur[0])
        box.addView(tv)

        val rowBtns = LinearLayout(this)
        rowBtns.orientation = LinearLayout.HORIZONTAL
        rowBtns.gravity = Gravity.CENTER

        fun addBtn(label: String, delta: Int) {
            val b = Button(this)
            b.text = label
            b.setOnClickListener {
                var v = cur[0] + delta
                if (v < 30) v = 30
                if (v > 1440) v = 1440
                cur[0] = v
                tv.text = durText(v)
            }
            rowBtns.addView(b)
        }
        addBtn("-1س", -60)
        addBtn("-30", -30)
        addBtn("+30", 30)
        addBtn("+1س", 60)
        box.addView(rowBtns)

        AlertDialog.Builder(this)
            .setTitle(name)
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                UsageStore.setLimit(this, pkg, cur[0])
                view.text = limitText(cur[0])
            }
            .setNegativeButton("بدون حد") { _, _ ->
                UsageStore.setLimit(this, pkg, 0)
                view.text = limitText(0)
            }
            .setNeutralButton("إلغاء", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blocked = prefs.getStringSet("blocked_apps", emptySet())!!.toMutableSet()
        val usage = UsageStore.todayTotals(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 64, 32, 32)

        val title = TextView(this)
        title.text = "اختر التطبيقات المحظورة"
        title.textSize = 24f
        title.gravity = Gravity.CENTER
        root.addView(title)

        val sub = TextView(this)
        sub.text = "الأرقام تحت الأسماء: وقت الاستخدام اليوم"
        sub.textSize = 14f
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, 8, 0, 16)
        root.addView(sub)

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != packageName }
            .sortedWith(
                compareByDescending<ApplicationInfo> { usage[it.packageName] ?: 0L }
                    .thenBy { it.loadLabel(pm).toString().lowercase() }
            )

        val listLayout = LinearLayout(this)
        listLayout.orientation = LinearLayout.VERTICAL

        for (app: ApplicationInfo in apps) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, 20, 0, 20)

            val box = CheckBox(this)
            box.isChecked = blocked.contains(app.packageName)
            box.setOnCheckedChangeListener { _, checked ->
                if (checked) blocked.add(app.packageName) else blocked.remove(app.packageName)
                prefs.edit().putStringSet("blocked_apps", blocked).apply()
            }

            val used = usage[app.packageName] ?: 0L
            val label = TextView(this)
            val name = app.loadLabel(pm).toString()
            label.text = if (used > 0L) name + "\n" + fmt(used) else name
            label.textSize = 18f
            label.setPadding(16, 0, 0, 0)

            val limitView = TextView(this)
            limitView.text = limitText(UsageStore.getLimit(this, app.packageName))
            limitView.textSize = 14f
            limitView.setPadding(16, 12, 0, 12)
            limitView.setOnClickListener {
                pickLimit(name, app.packageName, limitView)
            }

            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.addView(label)
            col.addView(limitView)

            row.addView(box)
            row.addView(col)
            listLayout.addView(row)
        }

        val scroll = ScrollView(this)
        scroll.addView(listLayout)
        root.addView(scroll)

        setContentView(root)
    }
}
