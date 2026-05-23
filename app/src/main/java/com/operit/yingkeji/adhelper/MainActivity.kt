package com.operit.yingkeji.adhelper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var editAccounts: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        layout.addView(TextView(this).apply {
            text = "瞬影科技刷量助手 v1.0"
            textSize = 20f
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "账号格式: token#oaid#deviceid，多账号用 &"
            textSize = 14f
        })

        editAccounts = EditText(this).apply {
            hint = "token1#oaid1#device1&token2#oaid2#device2"
            setMinLines(3)
        }
        layout.addView(editAccounts)

        val prefs = getSharedPreferences("ad_helper_config", Context.MODE_PRIVATE)
        editAccounts.setText(prefs.getString("accounts", ""))

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }

        btnLayout.addView(Button(this).apply {
            text = "保存"
            setOnClickListener {
                val acc = editAccounts.text.toString().trim()
                prefs.edit().putString("accounts", acc).apply()
                tvLog.append("[${getTime()}] 已保存\n")
                Toast.makeText(this@MainActivity, "已保存", Toast.LENGTH_SHORT).show()
            }
        })

        btnLayout.addView(Button(this).apply {
            text = "开始刷量"
            setOnClickListener {
                prefs.edit().putString("accounts", editAccounts.text.toString().trim()).apply()
                startForegroundService(Intent(this@MainActivity, AdTaskService::class.java))
                tvStatus.text = "状态: 运行中"
                tvLog.append("[${getTime()}] 服务已启动\n")
            }
        })

        btnLayout.addView(Button(this).apply {
            text = "停止"
            setOnClickListener {
                stopService(Intent(this@MainActivity, AdTaskService::class.java))
                tvStatus.text = "状态: 已停止"
                tvLog.append("[${getTime()}] 服务已停止\n")
            }
        })

        layout.addView(btnLayout)

        tvStatus = TextView(this).apply {
            text = "状态: 未启动"
            textSize = 16f
        }
        layout.addView(tvStatus)

        tvLog = TextView(this).apply {
            text = "日志:\n"
            textSize = 12f
            setMinLines(8)
        }
        layout.addView(tvLog)

        layout.addView(TextView(this).apply {
            text = "\n提示: 需在瞬影科技APP内使用，LSPatch模块会自动注入"
            textSize = 12f
            setTextColor(0xFF666666.toInt())
        })

        setContentView(layout)
    }

    private fun getTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }
}
