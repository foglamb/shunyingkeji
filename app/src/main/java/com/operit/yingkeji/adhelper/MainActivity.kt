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
    private lateinit var tvAccount: TextView
    private lateinit var tvGold: TextView
    private lateinit var tvRound: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var progressBar: ProgressBar
    private var isRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LOG -> {
                    val msg = intent.getStringExtra(EXTRA_LOG) ?: return
                    runOnUiThread { appendLog(msg) }
                }
                ACTION_STATUS -> {
                    val data = intent.getStringExtra(EXTRA_STATUS) ?: return
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
        buildUI()
        loadAccounts()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun buildUI() {
        val root = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "瞬影科技刷量助手 v1.0"
            textSize = 22f
            setPadding(0, 0, 0, 16)
            setTypeface(null, Typeface.BOLD)
        })

        // Status indicator
        tvAccount = TextView(this).apply {
            text = "状态: 未启动"
            textSize = 20f
            setTextColor(0xFFF44336.toInt())
        }
        layout.addView(tvAccount)

        // Current account
        layout.addView(TextView(this).apply {
            text = "当前账号: -"
            textSize = 15f
            setPadding(0, 8, 0, 8)
        })

        // Gold display
        tvGold = TextView(this).apply {
            text = "金币: 0/8000"
            textSize = 18f
            setTextColor(0xFFFFD700.toInt())
        }
        layout.addView(tvGold)

        // Round display
        tvRound = TextView(this).apply {
            text = "轮次: 0/2000"
            textSize = 18f
            setTextColor(0xFF4285F4.toInt())
        }
        layout.addView(tvRound)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 8000
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(progressBar)

        // Account config section title
        layout.addView(TextView(this).apply {
            text = "账号配置"
            textSize = 16f
            setPadding(0, 24, 0, 6)
            setTypeface(null, Typeface.BOLD)
        })

        // Account input
        editAccounts = EditText(this).apply {
            hint = "每行一个账号，格式: token#oaid#deviceid"
            gravity = Gravity.TOP
            setMinLines(4)
            textSize = 14f
        }
        layout.addView(editAccounts)

        // Button row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        btnStart = Button(this).apply {
            text = "开始刷量"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener { startTask() }
        }
        btnRow.addView(btnStart)

        btnStop = Button(this).apply {
            text = "停止刷量"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFF44336.toInt())
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply {
                setMargins(8, 0, 0, 0)
            }
            setOnClickListener { stopTask() }
        }
        btnRow.addView(btnStop)
        layout.addView(btnRow)

        // Refresh button
        val btnRefresh = Button(this).apply {
            text = "刷新状态"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF607D8B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                100
            ).apply { setMargins(0, 4, 0, 0) }
            setOnClickListener {
                sendBroadcast(Intent("com.operit.yingkeji.adhelper.REFRESH"))
                appendLog("已发送状态刷新请求")
            }
        }
        layout.addView(btnRefresh)

        // Log section title
        layout.addView(TextView(this).apply {
            text = "运行日志"
            textSize = 16f
            setPadding(0, 16, 0, 8)
            setTypeface(null, Typeface.BOLD)
        })

        // Log panel
        tvLog = TextView(this).apply {
            textSize = 12f
            setMinLines(6)
            setMaxLines(16)
            setTextColor(0xFFE0F7FA.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(12, 12, 12, 12)
            text = "[系统] 应用已启动，请配置账号后点击开始。\n"
        }
        layout.addView(tvLog)

        root.addView(layout)
        setContentView(root)
    }

    private fun loadAccounts() {
        val s = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
            .getString("accounts", "") ?: ""
        if (s.isNotEmpty()) {
            editAccounts.setText(s.replace("&", "\n"))
        }
    }

    private fun saveAccounts(): String {
        val s = editAccounts.text.toString().trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .joinToString("&")
        getSharedPreferences("ad_helper_config", MODE_PRIVATE)
            .edit()
            .putString("accounts", s)
            .apply()
        return s
    }

    private fun startTask() {
        if (saveAccounts().isEmpty()) {
            Toast.makeText(this, "请先输入至少一个账号", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, AdTaskService::class.java)
        ContextCompat.startForegroundService(this, intent)
        isRunning = true
        updateButtons()
        appendLog("[系统] 刷量服务已启动")
    }

    private fun stopTask() {
        stopService(Intent(this, AdTaskService::class.java))
        isRunning = false
        updateButtons()
        appendLog("[系统] 刷量服务已停止")
    }

    private fun updateButtons() {
        btnStart.isEnabled = !isRunning
        btnStop.isEnabled = isRunning
    }

    private fun appendLog(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        tvLog.append("[$t] $msg\n")
    }

    private fun updateStatus(data: String) {
        try {
            val parts = data.split("|")
            if (parts.size >= 5) {
                val isRunning = parts[0] == "1"
                val gold = parts[1].toIntOrNull() ?: 0
                val round = parts[2].toIntOrNull() ?: 0
                val account = parts[3]
                val statusMsg = parts[4]

                if (isRunning) {
                    tvAccount.text = "状态: 运行中"
                    tvAccount.setTextColor(0xFF4CAF50.toInt())
                } else {
                    tvAccount.text = "状态: 已停止"
                    tvAccount.setTextColor(0xFFF44336.toInt())
                }

                tvGold.text = "金币: $gold/8000"
                tvRound.text = "轮次: $round/2000"
                progressBar.progress = gold.coerceAtMost(8000)

                if (account.isNotEmpty()) {
                    val accView = (tvAccount.parent as? LinearLayout)?.getChildAt(1) as? TextView
                    accView?.text = "当前账号: $account"
                }
            }
        } catch (_: Exception) {}
    }
}
