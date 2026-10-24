package app.hima

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class BlockedAppsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hima", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val blocked = prefs.getStringSet("blocked_apps", emptySet())!!.toMutableSet()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 64, 32, 32)

        val title = TextView(this)
        title.text = "اختر التطبيقات المحظورة"
        title.textSize = 24f
        title.gravity = Gravity.CENTER
        root.addView(title)

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
    .filter { it.packageName != packageName }
    .sortedBy { it.loadLabel(pm).toString().lowercase() }
        val listLayout = LinearLayout(this)
        listLayout.orientation = LinearLayout.VERTICAL

        for (app: ApplicationInfo in apps) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, 20, 0, 20)

            val box = CheckBox(this)
            box.isChecked = blocked.contains(app.packageName)
            box.setOnCheckedChangeListener { _, checked ->
                if (checked) blocked.add(app.packageName) else blocked.remove(app.packageName)
                prefs.edit().putStringSet("blocked_apps", blocked).apply()
            }

            val label = TextView(this)
            label.text = app.loadLabel(pm).toString()
            label.textSize = 18f
            label.setPadding(16, 0, 0, 0)

            row.addView(box)
            row.addView(label)
            listLayout.addView(row)
        }

        val scroll = ScrollView(this)
        scroll.addView(listLayout)
        root.addView(scroll)

        setContentView(root)
    }
}
