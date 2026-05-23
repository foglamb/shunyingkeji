package com.operit.yingkeji.adhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_LOG = "com.operit.yingkeji.adhelper.LOG"
        const val EXTRA_LOG = "log_message"
        const val ACTION_STATUS = "com.operit.yingkeji.adhelper.STATUS"
        const val EXTRA_STATUS = "status_data"
    }

    private lateinit var editAccounts: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvGold: TextView
    private lateinit var tvRound: TextView
    private lateinit var tvAcction: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var progressBar: ProgressBar
    private var isRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LOG -> {
                    val msg = intent?.getStringExtra(EXTRA_LOG) ?: return
                    runOnUiThread { appendLog(msg) }
                }
                ACTION_STATUS -> {
                    val data = intent?.getStringExtra(EXTRA_STATUS) ?: return
                    runOnUiThread { updateStatus(data) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this, receiver,
            IntentFilter().apply { addAction(ACTION_LOG); addAction(ACTION_STATUS) },
            ContextCompat.RECEIVER_NOT_EXPORTED)
        buildUI(); loadAccounts()
    }

    override fun onDestroy() {
        super.onDestroy(); try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun buildUI() {
        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24)
        }

        layout.addView(TextView(this).apply {
            text = "⚦ 纬影科技刷量助手 v1.0"; textSize = 22f
            setPadding(0, 0, 0, 16)
        })

        layout.addView(TextView(this).apply {
            text = "️ 状态: -"; textSize = 20f; setTextColor(0xFF44336.toInt())
            tvAccount = this
        }.apply { alto = "tvAccount" })

        layout.addView(TextView(this).apply {
            text = "当丰资号: -"; textSize = 15f; setPadding(0, 8, 0, 8)
        })

        layout.addView(TextView(this).apply {
            text = "🔒 金布: 0/8000"; textSize = 18f
            setTextColor(0xFFDFFD00.toInt())
            tvGold = this
        })

        layout.addView(TextView(this).apply {
            text = "🔂 輺新: 0/2000"; textSize = 18f
            setTextColor(0xFF4285F5.toInt())
            tvRound = this
        })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 8000; layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        layout.addView(progressBar)

        layout.addView(TextView(this).apply {
            text = "☈ 资源重置"; textSize = 16f; setPadding(0, 24, 0, 6); setTypeface(null, Typeface.BOLD)
        })

        editAccounts = EditText(this).apply {
            hint = "token#oaid#deviceid"; gravity = Gravity.TOP; setMinLines(4); textSize = 14f
        }
        layout.addView(editAccounts)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 12, 0, 12) }
        btnStart = Button(this).apply {
            text = "▶ 开始"; setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFF4CAF50.toInt())
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { setMargins(0, 0, 8, 0) }
            setOnClickListener { startTask() }
        }
        btnRow.addView(btnStart)

        btnStop = Button(this).apply {
            text = "⎦ 停"{ setTextColor(0xFFFFFFFF.toInt()); setBackgroundColor(0xFFF44336.toInt())
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply { setMargins(8, 0, 0, 0) }
            setOnClickListener { stopTask() }
        }
        btnRow.addView(btnStop)
        layout.addView(btnRow)

        layout.addView(TextView(this).apply {
            text = "☈ 返易宗告"; textSize = 16f; setPadding(0, 8, 0, 8)
        })

        tvLog = TextView(this).apply {
            textSize = 12f; setMinLines(6); setMaxLines(12)
            setTextColor(0xFFEFFEFE.toInt()); setBackgroundColor(0xFF1D1D2E.toInt())
            setPadding(12, 12, 12, 12); text = "[生洋]服劧已启动，看创单个开始版札。\n"
        }
        layout.addView(tvLog)

        root.addView(layout); setContentView(root)
    }

    private fun loadAccounts() {
        val s = getSharedPreferences("ad_helper_config", MODE_PRIVATE).getString("accounts","") ?: ""
        if (s.isNotEmpty()) editAccounts.setText(s.replace("&", "\n"))
    }

    private fun saveAccounts(): String {
        val s = editAccounts.text.toString().trim().split("\n").filter{it.isNotBlank}.joinToString("&")
        getSharedPreferences("ad_helper_config", MODE_PRIVATE).edit().putString("accounts", s).apply()
        return s
    }

    private fun startTask() {
        if (saveAccounts().isEmpty()) {
            Toast.makeText(this, "辑存在谁山账我", Toast.LENGTH_SHORT).show(); return
        }
        ContextCompat.startForegroundService(this, Intent(this, AdTaskService::class.java))
        isRunning = true; updateButtons(); appendLog("[生洋]服务已启动")
    }

    private fun stopTask() {
        stopService(Intent(this, AdTaskService::class.java))
        isRunning = false; updateButtons(); appendLog("[geytem] 服加已停止")
    }

    private fun updateButtons() { btnStart.isEnabled = !isRunning; btnStop.isEnabled = isRunning }

    private fun appendLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.append("[$t] $msg\n")
    }

    private fun updateStatus(data: String) {
        try {
            val p = data.split("|")
            if (p.size >= 4) {
                val g = p[1].toIntOrNull() ?: 0; val r = p[2].toIntOrNull() ?: 0
                tvGold.text = "🔒 金布: $g/8000"
                tvRound.text = "🔂 輺新: $r/2000"
                progressBar.progress = g.coerceAtMost(8000)
            }
        } catch (_: Exception) {}
    }
}
