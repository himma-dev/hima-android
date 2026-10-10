package app.hima

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "حمى\nHima 0.1"
        tv.textSize = 32f
        tv.gravity = Gravity.CENTER
        setContentView(tv)
    }
}
