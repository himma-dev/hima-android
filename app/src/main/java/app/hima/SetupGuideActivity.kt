package app.hima

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SetupGuideActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 64, 48, 48)

        val title = TextView(this)
        title.text = "تفعيل حظر التطبيقات"
        title.textSize = 22f
        title.gravity = Gravity.CENTER
        root.addView(title)

        status = TextView(this)
        status.textSize = 18f
        status.gravity = Gravity.CENTER
        status.setPadding(0, 32, 0, 32)
        root.addView(status)

        val steps = TextView(this)
        steps.textSize = 16f
        steps.text = "لكي يعمل حظر التطبيقات يجب تفعيل خدمة حمى مرة واحدة:\n\n" +
            "1) اضغط زر \"إعدادات إمكانية الوصول\" بالأسفل، ثم افتح \"التطبيقات المثبتة\" واختر حمى.\n\n" +
            "2) شغّل المفتاح ووافق على نافذة التحذير.\n\n" +
            "3) في هواتف سامسونج: اضغط \"معلومات التطبيق\" وأوقف خيار \"إدارة التطبيق إن لم يُستخدم\"، واجعل البطارية \"غير مقيّدة\" كي لا يوقف النظام الخدمة تلقائياً.\n\n" +
            "إن كان المفتاح رمادياً أو ظهرت رسالة \"الإعدادات المقيّدة\": اضغط المفتاح مرة، ثم اضغط \"معلومات التطبيق\" ← النقاط الثلاث (⋮) ← \"السماح بالإعدادات المقيدة\" وأكّد، ثم أعد المحاولة. وإن لم تظهر ⋮ فأعد المحاولة مرة أو مرتين."
        steps.setPadding(0, 0, 0, 32)
        root.addView(steps)

        val info = Button(this)
        info.text = "معلومات التطبيق"
        info.setOnClickListener {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        }
        root.addView(info)

        val access = Button(this)
        access.text = "إعدادات إمكانية الوصول"
        access.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(access)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (isServiceEnabled()) {
            status.text = "✅ الخدمة مفعّلة والحظر يعمل"
            status.setTextColor(0xFF2E7D32.toInt())
        } else {
            status.text = "⚠️ الخدمة غير مفعّلة، والتطبيقات المحظورة لن تُغلق"
            status.setTextColor(0xFFC62828.toInt())
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, AppBlockerService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(":").any {
            it.equals(full, true) || it.equals(short, true)
        }
    }
}
