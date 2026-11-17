package app.hima

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom

object Recovery {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun hash(code: String, salt: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((salt + code).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun newCode(): String {
        val r = SecureRandom()
        val sb = StringBuilder()
        for (i in 0 until 8) sb.append(ALPHABET[r.nextInt(ALPHABET.length)])
        return sb.toString()
    }

    fun showNew(act: Activity, onDone: () -> Unit) {
        val code = newCode()
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val salt = b.joinToString("") { "%02x".format(it) }
        val p = act.getSharedPreferences("hima", Context.MODE_PRIVATE)
        p.edit().putString("rsalt", salt).putString("rhash", hash(code, salt)).apply()
        val shown = code.substring(0, 4) + "-" + code.substring(4)

        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(48, 24, 48, 0)
        val msg = TextView(act)
        msg.text = "اكتب هذا الرمز على ورقة واحتفظ بها. تحتاجه إذا نسيت الرقم السري. لن يظهر مرة أخرى."
        msg.textSize = 16f
        box.addView(msg)
        val tv = TextView(act)
        tv.text = shown
        tv.textSize = 34f
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 32, 0, 16)
        box.addView(tv)

        AlertDialog.Builder(act)
            .setTitle("رمز الاسترجاع")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("كتبته") { _, _ -> onDone() }
            .show()
    }

    fun ask(act: Activity, onVerified: () -> Unit) {
        val p = act.getSharedPreferences("hima", Context.MODE_PRIVATE)
        val h = p.getString("rhash", null)
        if (h == null) {
            Toast.makeText(act, "لا يوجد رمز استرجاع محفوظ", Toast.LENGTH_LONG).show()
            return
        }
        val now = System.currentTimeMillis()
        val until = p.getLong("rlock", 0L)
        if (now < until) {
            val secs = (until - now) / 1000 + 1
            Toast.makeText(act, "محاولات كثيرة، انتظر $secs ثانية", Toast.LENGTH_LONG).show()
            return
        }
        val e = EditText(act)
        e.hint = "رمز الاسترجاع (مثل K7M2-9QX4)"
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val box = LinearLayout(act)
        box.setPadding(48, 24, 48, 0)
        box.addView(e)

        AlertDialog.Builder(act)
            .setTitle("استرجاع الرقم السري")
            .setView(box)
            .setPositiveButton("تأكيد") { _, _ ->
                val typed = e.text.toString().uppercase().filter { it in ALPHABET }
                val salt = p.getString("rsalt", "") ?: ""
                if (hash(typed, salt) == h) {
                    p.edit().putInt("fails", 0).putLong("lockUntil", 0L)
                        .putInt("rfails", 0).putLong("rlock", 0L).apply()
                    Toast.makeText(act, "الرمز صحيح، اختر رقماً سرياً جديداً", Toast.LENGTH_LONG).show()
                    onVerified()
                } else {
                    val f = p.getInt("rfails", 0) + 1
                    if (f >= 5) {
                        p.edit().putInt("rfails", 0).putLong("rlock", now + 300000L).apply()
                        Toast.makeText(act, "محاولات كثيرة، انتظر 5 دقائق", Toast.LENGTH_LONG).show()
                    } else {
                        p.edit().putInt("rfails", f).apply()
                        Toast.makeText(act, "رمز خاطئ", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
