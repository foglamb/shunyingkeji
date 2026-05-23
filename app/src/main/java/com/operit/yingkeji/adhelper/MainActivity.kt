package com.operit.yingkeji.adhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
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
        
        const val PREF_MAX_GOLD = "max_gold"
        const val PREF_MAX_ROUNDS = "max_rounds"
        const val PREF_MIN_DELAY = "min_delay"
        const val PREF_MAX_DELAY = "max_delay"
        const val PREF_AUTO_TOKEN = "auto_token"
        const val PREF_AUTO_OAID = "auto_oaid"
        const val PREF_AUTO_DEVICEID = "auto_deviceid"
        const val PREF_ACCOUNTS = "accounts"
    }

    private lateinit var editAccounts: EditText
    private lateinit var editMaxGold: EditText
    private lateinit var editMaxRounds: EditText
    private lateinit var editMinDelay: EditText
    private lateinit var editMaxDelay: EditText
    private lateinit var tvAutoToken: TextView
    private lateinit var tvAutoOaid: TextView
    private lateinit var tvAutoDeviceId: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvAccountNow: TextView
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
        val filter = IntentFilter()
        filter.addAction(ACTION_LOG)
        filter.addAction(ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        buildUI()
        loadConfig()
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

        layout.addView(TextView(this).apply {
            text = "瞬影科技刷量助手 v2.0"
            textSize = 22f
            setPadding(0, 0, 0, 16)
            setTypeface(null, Typeface.BOLD)
        })

        tvStatus = TextView(this).apply {
            text = "⚪ 状态: 未启动"
            textSize = 20f
            setTextColor(0xFFF44336.toInt())
        }
        layout.addView(tvStatus)

        tvAccountNow = TextView(this).apply {
            text = "当前账号: -"
            textSize = 15f
            setPadding(0, 8, 0, 8)
        }
        layout.addView(tvAccountNow)

        tvGold = TextView(this).apply {
            text = "💰 金币: 0/8000"
            textSize = 18f
            setTextColor(0xFFFFD700.toInt())
        }
        layout.addView(tvGold)

        tvRound = TextView(this).apply {
            text = "🔄 轮次: 0/2000"
            textSize = 18f
            setTextColor(0xFF4285F4.toInt())
        }
        layout.addView(tvRound)

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 8000
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        layout.addView(progressBar)

        // ===== 参数配置区 =====
        layout.addView(TextView(this).apply {
            text = "📋 参数配置"
            textSize = 16f
            setPadding(0, 20, 0, 8)
            setTypeface(null, Typeface.BOLD)
        })

        addParamRow(layout, "金币上限:", "8000", InputType.TYPE_CLASS_NUMBER).let { editMaxGold = it }
        addParamRow(layout, "轮次上限:", "2000", InputType.TYPE_CLASS_NUMBER).let { editMaxRounds = it }
        addParamRow(layout, "最小间隔(秒):", "20", InputType.TYPE_CLASS_NUMBER).let { editMinDelay = it }
        addParamRow(layout, "最大间隔(秒):", "30", InputType.TYPE_CLASS_NUMBER).let { editMaxDelay = it }

        // ===== 自动捕获参数 =====
        layout.addView(TextView(this).apply {
            text = "📡 自动捕获参数"
            textSize = 16f
            setPadding(0, 20, 0, 8)
            setTypeface(null, Typeface.BOLD)
        })
        
        layout.addView(TextView(this).apply {
            text = "打开瞬影科技应用触发网络请求后自动捕获 ↓"
            textSize = 13f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 8)
        })

        tvAutoToken = TextView(this).apply {
            text = "Token: 未捕获"
            textSize = 14f
            setPadding(0, 2, 0, 2)
            setTextColor(0xFF4CAF50.toInt())
        }
        layout.addView(tvAutoToken)

        tvAutoOaid = TextView(this).apply {
            text = "OAID: 未捕获"
            textSize = 14f
            setPadding(0, 2, 0, 2)
        }
        layout.addView(tvAutoOaid)

        tvAutoDeviceId = TextView(this).apply {
            text = "DeviceID: 未捕获"
            textSize = 14f
            setPadding(0, 2, 0, 2)
        }
        layout.addView(tvAutoDeviceId)

        val btnRefreshAuto = Button(this).apply {
            text = "刷新捕获状态"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF009688.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 80
            ).apply { setMargins(0, 8, 0, 0) }
            setOnClickListener { refreshAutoCaptured() }
        }
        layout.addView(btnRefreshAuto)

        // ===== 账号配置 =====
        layout.addView(TextView(this).apply {
            text = "👤 账号配置"
            textSize = 16f
            setPadding(0, 20, 0, 8)
            setTypeface(null, Typeface.BOLD)
        })
        
        layout.addView(TextView(this).apply {
            text = "每行一个账号，格式: token#oaid#deviceid\n支持自动捕获填入"
            textSize = 12f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 4)
        })

        editAccounts = EditText(this).apply {
            hint = "token#oaid#deviceid"
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
            text = "▶ 开始刷量"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener { startTask() }
        }
        btnRow.addView(btnStart)

        btnStop = Button(this).apply {
            text = "⏹ 停止刷量"
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
        layout.addView(Button(this).apply {
            text = "🔄 刷新状态"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF607D8B.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 100
            ).apply { setMargins(0, 4, 0, 0) }
            setOnClickListener {
                sendBroadcast(Intent("com.operit.yingkeji.adhelper.REFRESH"))
                appendLog("[系统] 已发送状态刷新请求")
            }
        })

        // Log section
        layout.addView(TextView(this).apply {
            text = "📝 运行日志"
            textSize = 16f
            setPadding(0, 16, 0, 8)
            setTypeface(null, Typeface.BOLD)
        })

        tvLog = TextView(this).apply {
            textSize = 12f
            setMinLines(6)
            setMaxLines(16)
            setTextColor(0xFFE0F7FA.toInt())
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(12, 12, 12, 12)
            text = "[系统] v2.0 应用已启动\n"
        }
        layout.addView(tvLog)

        root.addView(layout)
        setContentView(root)
    }

    private fun addParamRow(layout: LinearLayout, label: String, hint: String, inputType: Int): EditText {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setPadding(0, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f)
        })
        val edit = EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
        }
        row.addView(edit)
        layout.addView(row)
        return edit
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
        
        editMaxGold.setText(prefs.getInt(PREF_MAX_GOLD, 8000).toString())
        editMaxRounds.setText(prefs.getInt(PREF_MAX_ROUNDS, 2000).toString())
        editMinDelay.setText(prefs.getInt(PREF_MIN_DELAY, 20).toString())
        editMaxDelay.setText(prefs.getInt(PREF_MAX_DELAY, 30).toString())
        
        val accounts = prefs.getString(PREF_ACCOUNTS, "") ?: ""
        if (accounts.isNotEmpty()) {
            editAccounts.setText(accounts.replace("&", "\n"))
        }
        
        refreshAutoCaptured()
    }

    private fun refreshAutoCaptured() {
        val prefs = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
        val token = prefs.getString(PREF_AUTO_TOKEN, "") ?: ""
        val oaid = prefs.getString(PREF_AUTO_OAID, "") ?: ""
        val deviceId = prefs.getString(PREF_AUTO_DEVICEID, "") ?: ""
        
        tvAutoToken.text = if (token.isNotEmpty()) "Token: ${token.take(20)}..." else "Token: 未捕获"
        tvAutoOaid.text = if (oaid.isNotEmpty()) "OAID: ${oaid.take(16)}..." else "OAID: 未捕获"
        tvAutoDeviceId.text = if (deviceId.isNotEmpty()) "DeviceID: ${deviceId.take(16)}..." else "DeviceID: 未捕获"
        
        if (token.isNotEmpty() && oaid.isNotEmpty() && deviceId.isNotEmpty()) {
            val existing = editAccounts.text.toString().trim()
            val newLine = "$token#$oaid#$deviceId"
            if (existing.isEmpty()) {
                editAccounts.setText(newLine)
            } else {
                val lines = existing.split("\n").filter { it.isNotBlank() }
                if (lines.none { it.startsWith(token) }) {
                    editAccounts.setText(existing + "\n" + newLine)
                }
            }
            appendLog("[系统] 已自动填入捕获的参数")
        }
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
        prefs.edit()
            .putInt(PREF_MAX_GOLD, editMaxGold.text.toString().toIntOrNull() ?: 8000)
            .putInt(PREF_MAX_ROUNDS, editMaxRounds.text.toString().toIntOrNull() ?: 2000)
            .putInt(PREF_MIN_DELAY, editMinDelay.text.toString().toIntOrNull() ?: 20)
            .putInt(PREF_MAX_DELAY, editMaxDelay.text.toString().toIntOrNull() ?: 30)
            .apply()
        
        val accStr = editAccounts.text.toString().trim()
            .split("\n")
            .filter { it.isNotBlank() }
            .joinToString("&")
        prefs.edit().putString(PREF_ACCOUNTS, accStr).apply()
    }

    private fun startTask() {
        saveConfig()
        
        val accounts = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
            .getString(PREF_ACCOUNTS, "") ?: ""
        if (accounts.isEmpty()) {
            Toast.makeText(this, "请先输入账号或等待自动捕获", Toast.LENGTH_SHORT).show()
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
            if (parts.size >= 6) {
                val running = parts[0] == "1"
                val gold = parts[1].toIntOrNull() ?: 0
                val round = parts[2].toIntOrNull() ?: 0
                val account = parts[3]
                val maxGold = parts[4].toIntOrNull() ?: 8000
                val maxRounds = parts[5].toIntOrNull() ?: 2000

                tvStatus.text = if (running) "🟢 状态: 运行中" else "🔴 状态: 已停止"
                tvStatus.setTextColor(if (running) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

                tvGold.text = "💰 金币: $gold/$maxGold"
                tvRound.text = "🔄 轮次: $round/$maxRounds"
                progressBar.max = maxGold
                progressBar.progress = gold.coerceAtMost(maxGold)

                if (account.isNotEmpty()) {
                    tvAccountNow.text = "当前账号: $account"
                }
            }
        } catch (_: Exception) {}
    }
}
