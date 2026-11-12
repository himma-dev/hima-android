package app.hima

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

object LockDialog {
    fun show(act: Activity, onLocked: () -> Unit) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)

        val dlgRef = arrayOfNulls<AlertDialog>(1)

        fun lockFor(min: Int) {
            LockHelper.setLocked(act, true, min)
            dlgRef[0]?.dismiss()
            onLocked()
        }

        fun addOpt(label: String, min: Int) {
            val b = Button(act)
            b.text = label
            b.setOnClickListener { lockFor(min) }
            box.addView(b)
        }

        addOpt("30 دقيقة", 30)
        addOpt("ساعة", 60)
        addOpt("ساعتان", 120)
        addOpt("حتى أفكه بالرقم السري", 0)

        val tv = TextView(act)
        tv.text = "أو اكتب عدد الدقائق (من 1 إلى 1440):"
        tv.setPadding(0, 24, 0, 8)
        box.addView(tv)

        val et = EditText(act)
        et.hint = "عدد الدقائق"
        et.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(et)

        val ok = Button(act)
        ok.text = "قفل بهذه المدة"
        ok.setOnClickListener {
            val m = et.text.toString().toIntOrNull()
            if (m == null || m < 1 || m > 1440) {
                Toast.makeText(act, "اكتب رقماً بين 1 و1440", Toast.LENGTH_SHORT).show()
            } else {
                lockFor(m)
            }
        }
        box.addView(ok)

        val scroll = ScrollView(act)
        scroll.addView(box)

        val dlg = AlertDialog.Builder(act)
            .setTitle("مدة القفل")
            .setView(scroll)
            .setNegativeButton("إلغاء", null)
            .create()
        dlgRef[0] = dlg
        dlg.show()
    }
}
