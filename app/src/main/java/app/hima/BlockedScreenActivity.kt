package app.hima

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockedScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(48, 48, 48, 48)

        val icon = TextView(this)
        icon.text = "🚫"
        icon.textSize = 64f
        icon.gravity = Gravity.CENTER
        root.addView(icon)

        val msg = TextView(this)
        msg.text = "هذا التطبيق محظور من قِبل ولي الأمر"
        msg.textSize = 22f
        msg.gravity = Gravity.CENTER
        msg.setPadding(0, 32, 0, 48)
        root.addView(msg)

        val back = Button(this)
        back.text = "العودة إلى الشاشة الرئيسية"
        back.setOnClickListener { goHome() }
        root.addView(back)

        setContentView(root)
    }

    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_HOME)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(i)
        finish()
    }
}
