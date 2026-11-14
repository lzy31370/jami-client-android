/*
 * GA patch: 内置启动密码门 (App-lock PIN gate)
 * 2026-11-14 — 独立于系统(MIUI)应用锁：冷启动/进程重启后必须先通过本门才能进入主界面。
 * PIN 以 MD5 存于私有 SharedPreferences；解锁状态仅存进程内存(AppLockGate.unlocked)，
 * 进程被杀后重新上锁。
 */
package cx.ring.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

object AppLockGate {
    private const val TAG = "AppLockGate"
    private const val PREF = "app_lock_gate"
    private const val KEY_PIN_MD5 = "pin_md5"

    @Volatile
    var unlocked: Boolean = false
        private set

    fun markUnlocked() {
        unlocked = true
        Log.w(TAG, "app unlocked for this process")
    }

    fun isPinSet(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).contains(KEY_PIN_MD5)

    fun needLock(context: Context): Boolean = !unlocked

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_PIN_MD5, md5(pin)).apply()
        Log.w(TAG, "PIN set")
    }

    fun checkPin(context: Context, pin: String): Boolean {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_PIN_MD5, null) ?: return false
        return saved == md5(pin)
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }
}

class AppLockActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var hint: TextView
    private lateinit var action: Button
    private var confirmStage: Boolean = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (22 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#F7F8FA"))
            setPadding(pad, (90 * resources.displayMetrics.density).toInt(), pad, pad)
        }

        val title = TextView(this).apply {
            text = "京东健康"
            textSize = 24f
            setTextColor(Color.parseColor("#17181A"))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        hint = TextView(this).apply {
            text = if (AppLockGate.isPinSet(this@AppLockActivity)) "请输入访问密码" else "首次使用，请设置访问密码"
            textSize = 15f
            setTextColor(Color.parseColor("#7C7E82"))
            gravity = Gravity.CENTER
            setPadding(0, (10 * resources.displayMetrics.density).toInt(), 0, (24 * resources.displayMetrics.density).toInt())
        }
        input = EditText(this).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        action = Button(this).apply {
            text = if (AppLockGate.isPinSet(this@AppLockActivity)) "解锁" else "下一步"
            setOnClickListener { onSubmit() }
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val btnParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (18 * resources.displayMetrics.density).toInt() }

        root.addView(title)
        root.addView(hint, params)
        root.addView(input, params)
        root.addView(action, btnParams)
        setContentView(root)
    }

    private fun onSubmit() {
        val pin = input.text?.toString()?.trim().orEmpty()
        if (pin.isEmpty()) {
            toast("请输入密码")
            return
        }
        if (AppLockGate.isPinSet(this)) {
            if (AppLockGate.checkPin(this, pin)) {
                unlock()
            } else {
                input.setText("")
                toast("密码错误，请重试")
            }
        } else {
            if (!confirmStage) {
                pendingPin = pin
                confirmStage = true
                hint.text = "请再次输入相同密码以确认"
                action.text = "完成"
                input.setText("")
            } else {
                if (pin == pendingPin) {
                    AppLockGate.setPin(this, pin)
                    toast("密码已设置")
                    unlock()
                } else {
                    confirmStage = false
                    pendingPin = null
                    hint.text = "两次输入不一致，请重新设置"
                    action.text = "下一步"
                    input.setText("")
                }
            }
        }
    }

    private var pendingPin: String? = null

    private fun unlock() {
        AppLockGate.markUnlocked()
        val intent = Intent(this, HomeActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
