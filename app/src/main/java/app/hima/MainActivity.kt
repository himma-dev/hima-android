package app.hima

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var button: Button

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
            val i = Intent(this, HimaVpnService::class.java)
            i.action = HimaVpnService.ACTION_STOP
            startService(i)
            HimaVpnService.running = false
            refresh()
        } else {
            val prep = VpnService.prepare(this)
            if (prep != null) {
                startActivityForResult(prep, 1)
            } else {
                startFilter()
            }
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
}
