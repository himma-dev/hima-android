package app.hima

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var button: Button

    private val prefs by lazy { getSharedPreferences("hima", Context.MODE_PRIVATE) }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            button.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "حمى"
        title.textSize = 44f
        title.gravity = Gravity.CENTER
        root.addView(title)

        status = TextView(this)
        status.textSize = 22f
        status.gravity = Gravity.CENTER
        root.addView(status)

        count = TextView(this)
        count.textSize = 18f
        count.gravity = Gravity.CENTER
        root.addView(count)

        button = Button(this)
        button.textSize = 20f
        button.setOnClickListener { toggle() }
        root.addView(button)

        val change = Button(this)
        change.text = "تغيير الرقم السري"
        change.setOnClickListener {
            if (hasPin()) askPin { askSetPin { } } else askSetPin { }
        }
        root.addView(change)
        val apps = Button(this)
            apps.text = "التطبيقات المحظورة"
            apps.setOnClickListener { askPin {
                startActivity(Intent(this, BlockedAppsActivity::class.java))
            } } 
            root.addView(apps)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        tick.run()
    }

    override fun onPause() {
        super.onPause()
        button.removeCallbacks(tick)
    }

    private fun refresh() {
        if (HimaVpnService.running) {
            status.text = "الحماية تعمل"
            button.text = "إيقاف الحماية"
        } else {
            status.text = "الحماية متوقفة"
            button.text = "تشغيل الحماية"
        }
        count.text = "عدد المواقع المحجوبة: " + HimaVpnService.blockedCount.get()
    }

    private fun toggle() {
        if (HimaVpnService.running) {
            askPin { stopProtection() }
        } else {
            if (hasPin()) startProtection() else askSetPin { startProtection() }
        }
    }

    private fun stopProtection() {
        val i = Intent(this, HimaVpnService::class.java)
        i.action = HimaVpnService.ACTION_STOP
        startService(i)
        HimaVpnService.running = false
        refresh()
    }

    private fun startProtection() {
        val prep = VpnService.prepare(this)
        if (prep != null) {
            startActivityForResult(prep, 1)
        } else {
            startFilter()
        }
    }

    private fun startFilter() {
        startService(Intent(this, HimaVpnService::class.java))
        button.postDelayed({ refresh() }, 500)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) startFilter()
    }

    private fun hashPin(pin: String, salt: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun hasPin(): Boolean = prefs.getString("hash", null) != null

    private fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("salt", "") ?: ""
        return hashPin(pin, salt) == prefs.getString("hash", "")
    }

    private fun savePin(pin: String) {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val salt = b.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("salt", salt).putString("hash", hashPin(pin, salt)).apply()
    }

    private fun numericField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return e
    }

    private fun askSetPin(onDone: () -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val a = numericField("الرقم السري (4 أرقام على الأقل)")
        val b = numericField("أعد كتابة الرقم السري")
        box.addView(a)
        box.addView(b)
        AlertDialog.Builder(this)
            .setTitle("الرقم السري للأب")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                val p = a.text.toString()
                if (p.length >= 4 && p == b.text.toString()) {
                    savePin(p)
                    Toast.makeText(this, "تم حفظ الرقم السري", Toast.LENGTH_SHORT).show()
                    onDone()
                } else {
                    Toast.makeText(this, "الرقمان غير متطابقين أو أقل من 4 أرقام", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun askPin(onOk: () -> Unit) {
        val lockUntil = prefs.getLong("lockUntil", 0L)
        val now = System.currentTimeMillis()
        if (now < lockUntil) {
            val secs = (lockUntil - now) / 1000 + 1
            Toast.makeText(this, "محاولات كثيرة، انتظر $secs ثانية", Toast.LENGTH_LONG).show()
            return
        }
        val e = numericField("الرقم السري")
        val box = LinearLayout(this)
        box.setPadding(48, 24, 48, 0)
        box.addView(e)
        AlertDialog.Builder(this)
            .setTitle("أدخل الرقم السري للأب")
            .setView(box)
            .setPositiveButton("تأكيد") { _, _ ->
                if (checkPin(e.text.toString())) {
                    prefs.edit().putInt("fails", 0).apply()
                    onOk()
                } else {
                    val fails = prefs.getInt("fails", 0) + 1
                    prefs.edit().putInt("fails", fails).apply()
                    if (fails >= 5) {
                        val lockSecs = minOf(30 * (fails - 4), 300)
                        prefs.edit().putLong("lockUntil", now + lockSecs * 1000L).apply()
                        Toast.makeText(this, "محاولات كثيرة، انتظر $lockSecs ثانية", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "رقم سري خاطئ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}

