package com.operit.yingkeji.adhelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AdTaskService : Service() {

    companion object {
        private const val TAG = "YingkejiAdHelper-Service"
        private const val CHANNEL_ID = "ad_task_channel"
        private const val NOTIFICATION_ID = 1001
        
        // ====== 固定常量 ======
        private const val JWT_KEY = "059f0570a479a317932f175e9321c274"
        private const val DEVICE_SHA = "FA552E62ED750596E7F157BAEEE66F75BAB29D26"
        private const val DOMAIN = "1526xin.yingkeji.cc"
        private const val VERSION = "1077"
        private const val DIVIDENDS = "4"
        private val VALID_NETWORK_IDS = listOf("3", "4", "16", "18", "19", "40")
        private const val X_CH = "64131e7a9286cfea"
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private var accounts = mutableListOf<AccountInfo>()
    
    // 可配置参数（从SharedPreferences读取）
    private var stopGold = 8000
    private var loopCount = 2000
    private var minDelayMs = 20000L
    private var maxDelayMs = 30000L
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class AccountInfo(
        val token: String,
        val oaid: String,
        val deviceId: String
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("刷量服务已启动"))
        Log.i(TAG, "广告刷量服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "收到启动命令，开始刷量任务")
        
        // 读取自定义参数
        loadConfig()
        
        // 读取自动捕获的参数（如果手动账号为空，使用自动捕获的）
        val prefs = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
        val autoToken = prefs.getString("auto_token", "") ?: ""
        val autoOaid = prefs.getString("auto_oaid", "") ?: ""
        val autoDeviceId = prefs.getString("auto_deviceid", "") ?: ""
        
        loadAccounts(autoToken, autoOaid, autoDeviceId)
        
        if (accounts.isEmpty()) {
            Log.w(TAG, "没有配置账号，使用默认测试账号")
            accounts.add(AccountInfo(
                token = "test_token",
                oaid = "test_oaid",
                deviceId = "test_device"
            ))
        }
        
        // 发送状态给面板
        sendStatusToUI()
        
        scope.launch {
            runAllAccounts()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("ad_helper_config", MODE_PRIVATE)
        stopGold = prefs.getInt("max_gold", 8000)
        loopCount = prefs.getInt("max_rounds", 2000)
        minDelayMs = (prefs.getInt("min_delay", 20) * 1000L).coerceAtLeast(5000L) // 最少5秒
        maxDelayMs = (prefs.getInt("max_delay", 30) * 1000L).coerceAtLeast(minDelayMs + 1000)
        
        Log.i(TAG, "配置参数: 金币上限=$stopGold, 轮次上限=$loopCount, 延迟=${minDelayMs/1000}-${maxDelayMs/1000}秒")
    }

    private fun loadAccounts(autoToken: String, autoOaid: String, autoDeviceId: String) {
        val prefs = getSharedPreferences("ad_helper_config", Context.MODE_PRIVATE)
        val raw = prefs.getString("accounts", "") ?: ""
        
        if (raw.isNotEmpty()) {
            raw.split("&").forEach { acc ->
                val parts = acc.trim().split("#")
                if (parts.size == 3) {
                    accounts.add(AccountInfo(
                        token = parts[0],
                        oaid = parts[1],
                        deviceId = parts[2]
                    ))
                }
            }
        }
        
        // 如果手动账号为空，尝试使用自动捕获的参数
        if (accounts.isEmpty() && autoToken.isNotEmpty()) {
            accounts.add(AccountInfo(token = autoToken, oaid = autoOaid, deviceId = autoDeviceId))
            Log.i(TAG, "使用自动捕获的账号: ${autoToken.take(16)}...")
        }
        
        Log.i(TAG, "已加载 ${accounts.size} 个账号")
    }

    private fun sendStatusToUI() {
        try {
            val statusStr = if (accounts.isNotEmpty()) {
                val acc = accounts[0]
                "1|0|0|${acc.token.take(12)}|$stopGold|$loopCount"
            } else {
                "0|0|0|-|$stopGold|$loopCount"
            }
            sendBroadcast(Intent("com.operit.yingkeji.adhelper.STATUS").apply {
                putExtra("status_data", statusStr)
            })
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "刷量任务", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("瞬影科技刷量助手")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
        return builder.build()
    }

    // ==================== 刷量核心逻辑 ====================

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun createJwt(payload: String, secret: String): String {
        val header = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }.toString()
        
        val headerB64 = base64UrlEncode(header.toByteArray())
        val payloadB64 = base64UrlEncode(
            JSONObject().apply { put("sub", payload) }.toString().toByteArray()
        )
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal("$headerB64.$payloadB64".toByteArray())
        
        return "$headerB64.$payloadB64.${base64UrlEncode(signature)}"
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(s.toByteArray()).joinToString("") { String.format("%02x", it) }
    }

    private fun randomSuffix(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..n).map { chars[Random().nextInt(chars.length)] }.joinToString("")
    }

    private data class HttpResponse(
        val status: Int,
        val body: String,
        val error: String?
    )

    private fun httpRequest(
        token: String,
        method: String,
        path: String,
        body: String? = null
    ): HttpResponse {
        try {
            val ts = System.currentTimeMillis().toString()
            val nc = (100000 + Random().nextInt(900000)).toString()
            val tick = (System.currentTimeMillis() / 60000).toString()
            val rnd = randomSuffix(6)
            val sig = md5Hex(token + ts + nc + "/" + path).substring(8, 24)
            val tokena = createJwt(token, JWT_KEY)
            val sha = createJwt(DEVICE_SHA, JWT_KEY)

            val urlBuilder = HttpUrl.Builder()
                .scheme("https")
                .host(DOMAIN)
                .addPathSegments(path)
                .addQueryParameter("_rnd", rnd)
                .addQueryParameter("_tick", tick)

            val requestBuilder = Request.Builder()
                .url(urlBuilder.build())
                .header("Host", DOMAIN)
                .header("token", token)
                .header("version", VERSION)
                .header("sha", sha)
                .header("tokena", tokena)
                .header("x-ts", ts)
                .header("x-nc", nc)
                .header("x-ch", X_CH)
                .header("x-sig", sig)
                .header("User-Agent", "okhttp/4.10.0")
                .method(method, if (method == "POST" && body != null) {
                    RequestBody.create("application/x-www-form-urlencoded".toMediaType(), body)
                } else null)

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""
            
            return HttpResponse(status = response.code, body = responseBody, error = null)
        } catch (e: Exception) {
            Log.w(TAG, "HTTP请求失败: ${e.message}")
            return HttpResponse(status = 0, body = "", error = e.message)
        }
    }

    private data class AdConfig(
        val adTypes: MutableMap<String, AdTypeInfo>,
        val placementIds: MutableList<String>,
        val availableTypes: MutableList<String>
    )

    private data class AdTypeInfo(
        val name: String,
        val adtimes: Int,
        var number: Int
    )

    private fun fetchAdConfig(token: String): AdConfig {
        val adTypes = mutableMapOf<String, AdTypeInfo>()
        val placementIds = mutableListOf<String>()
        val availableTypes = mutableListOf<String>()

        // 请求 api/Sigbom/ACfig
        val resp1 = httpRequest(token, "POST", "api/Sigbom/ACfig", "")
        if (resp1.error == null) {
            try {
                val data = JSONObject(resp1.body)
                val dataObj = data.optJSONObject("data")
                if (dataObj != null) {
                    dataObj.keys().forEach { key ->
                        val item = dataObj.optJSONObject(key)
                        if (item != null) {
                            val status = item.optInt("status", 0)
                            val number = item.optInt("number", 0)
                            if (status == 1 && number > 0) {
                                adTypes[key] = AdTypeInfo(
                                    name = item.optString("adname", ""),
                                    adtimes = item.optInt("adtimes", 30),
                                    number = number
                                )
                                availableTypes.add(key)
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }

        // 请求 api/adsdee/AConfig
        val resp2 = httpRequest(token, "POST", "api/adsdee/AConfig", "")
        if (resp2.error == null) {
            try {
                val data = JSONObject(resp2.body)
                val d = data.optJSONObject("data")
                if (d != null) {
                    val idKeys = listOf(
                        "sigmob_rewarded_id", "sigmob_splash_id", "sigmob_banner_id",
                        "sigmob_interstitial_id", "sigmob_native_id"
                    )
                    for (key in idKeys) {
                        val value = d.optString(key, "")
                        if (value.isNotEmpty() && !placementIds.contains(value)) {
                            placementIds.add(value)
                        }
                    }
                }
            } catch (e: Exception) { }
        }

        // 如果获取失败，使用默认值
        if (adTypes.isEmpty()) {
            adTypes["1"] = AdTypeInfo("开屏", 60, 100)
            adTypes["2"] = AdTypeInfo("横幅", 30, 500)
            adTypes["3"] = AdTypeInfo("插屏", 30, 500)
            adTypes["4"] = AdTypeInfo("信息流", 30, 500)
            availableTypes.addAll(listOf("1", "2", "3", "4"))
        }

        if (placementIds.isEmpty()) {
            placementIds.addAll(listOf(
                "6946757878516186", "7836544146343427", "7734978116958422",
                "4823381738777711", "4729439369894264"
            ))
        }

        return AdConfig(adTypes, placementIds, availableTypes)
    }

    private suspend fun runAccount(account: AccountInfo, accIndex: Int) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "[账号${accIndex + 1}] 开始刷量 (上限: ${stopGold}金币, ${loopCount}轮)")
            Log.i(TAG, "[账号${accIndex + 1}] Token: ${account.token.take(16)}...")
            
            val config = fetchAdConfig(account.token)
            var adTypes = config.adTypes
            var placementIds = config.placementIds
            var availableTypes = config.availableTypes.toMutableList()

            var roundNum = 0
            var lastGold = 0

            while (roundNum < loopCount) {
                roundNum++

                // 获取用户信息
                val userInfoResp = httpRequest(account.token, "GET", "api/Member/Guserinfo")
                if (userInfoResp.error != null) {
                    Log.w(TAG, "[账号${accIndex + 1}] 获取用户信息失败: ${userInfoResp.error}")
                    delay(5000)
                    continue
                }

                val gold: Int
                try {
                    val userData = JSONObject(userInfoResp.body)
                    val userinfo = userData.optJSONObject("data")?.optJSONObject("userinfo")
                    gold = userinfo?.optInt("forecast_gold", 0) ?: 0
                } catch (e: Exception) {
                    Log.w(TAG, "[账号${accIndex + 1}] 解析用户信息失败: ${e.message}")
                    delay(5000)
                    continue
                }

                val delta = if (lastGold > 0) gold - lastGold else 0
                lastGold = gold

                // 发送状态到UI
                try {
                    val statusStr = "1|$gold|$roundNum|${account.token.take(12)}|$stopGold|$loopCount"
                    sendBroadcast(Intent("com.operit.yingkeji.adhelper.STATUS").apply {
                        putExtra("status_data", statusStr)
                    })
                } catch (_: Exception) {}

                if (gold >= stopGold) {
                    Log.i(TAG, "[账号${accIndex + 1}] ✅ 已满 $stopGold 金币，停止")
                    updateNotification("[账号${accIndex + 1}] 金币已满 $stopGold")
                    break
                }

                if (availableTypes.isEmpty()) {
                    val newConfig = fetchAdConfig(account.token)
                    adTypes = newConfig.adTypes
                    placementIds = newConfig.placementIds
                    availableTypes = newConfig.availableTypes.toMutableList()
                    if (availableTypes.isEmpty()) break
                }

                val adType = availableTypes[Random().nextInt(availableTypes.size)]
                val typeInfo = adTypes[adType] ?: AdTypeInfo("type$adType", 30, 0)
                val placementId = if (placementIds.isNotEmpty()) {
                    placementIds[Random().nextInt(placementIds.size)]
                } else {
                    (1000000000000000L + Math.abs(Random().nextLong()) % 8999999999999999L).toString()
                }
                val networkId = VALID_NETWORK_IDS[Random().nextInt(VALID_NETWORK_IDS.size)]
                val loadId = UUID.randomUUID().toString()
                val ecpm = (5000 + Random().nextInt(5001)).toString()

                // 构建广告参数
                val adParams = JSONObject()
                adParams.put("placementId", placementId)
                adParams.put("networkPlacementId", placementId)
                adParams.put("networkId", networkId)
                adParams.put("loadId", loadId)
                adParams.put("eCPM", ecpm)
                adParams.put("version", VERSION)
                adParams.put("dividends", DIVIDENDS)
                adParams.put("ad_type", "1")
                adParams.put("type", adType)
                adParams.put("deviceId", account.deviceId)
                adParams.put("oaid", account.oaid)
                adParams.put("sha", createJwt(DEVICE_SHA, JWT_KEY))

                val sgin = createJwt(adParams.toString(), JWT_KEY)

                val bodyParams = JSONObject()
                bodyParams.put("placementId", placementId)
                bodyParams.put("networkPlacementId", placementId)
                bodyParams.put("networkId", networkId)
                bodyParams.put("loadId", loadId)
                bodyParams.put("eCPM", ecpm)
                bodyParams.put("version", VERSION)
                bodyParams.put("dividends", DIVIDENDS)
                bodyParams.put("ad_type", "1")
                bodyParams.put("type", adType)
                bodyParams.put("deviceId", account.deviceId)
                bodyParams.put("oaid", account.oaid)
                bodyParams.put("sgin", sgin)
                bodyParams.put("tc", createJwt(DEVICE_SHA, JWT_KEY))
                bodyParams.put("ta", createJwt("AOterUrl", JWT_KEY))
                bodyParams.put("tb", sgin)

                val bodyStr = buildUrlEncodedString(bodyParams)

                // 发送广告请求
                val resp = httpRequest(account.token, "POST", "api/Pubqingqiu/Pderdob", bodyStr)
                if (resp.body.isNotEmpty()) {
                    try {
                        val result = JSONObject(resp.body)
                        val dataResult = result.optJSONObject("data")
                        if (dataResult != null && dataResult.has("number")) {
                            val serverNumber = dataResult.optInt("number")
                            adTypes[adType]?.number = serverNumber
                            if (serverNumber <= 0 && availableTypes.contains(adType)) {
                                availableTypes.remove(adType)
                            }
                        }
                    } catch (e: Exception) { }
                }

                val remaining = adTypes[adType]?.number?.toString() ?: "?"
                Log.d(TAG, "[账号${accIndex + 1}] #$roundNum 金币:$gold (+$delta) | ${typeInfo.name} 剩${remaining}次")
                updateNotification("[账号${accIndex + 1}] #$roundNum 金币:$gold")

                // 每50轮重新获取配置
                if (roundNum % 50 == 0) {
                    val newConfig = fetchAdConfig(account.token)
                    adTypes = newConfig.adTypes
                    placementIds = newConfig.placementIds
                    availableTypes = newConfig.availableTypes.toMutableList()
                }

                // 随机延迟（使用用户配置）
                val delay = minDelayMs + Math.abs(Random().nextLong()) % (maxDelayMs - minDelayMs + 1)
                delay(delay)
            }

            Log.i(TAG, "[账号${accIndex + 1}] 完成刷量")
        }
    }

    private fun buildUrlEncodedString(params: JSONObject): String {
        return params.keys().asSequence().map { key ->
            key + "=" + java.net.URLEncoder.encode(params.getString(key), "UTF-8")
        }.joinToString("&")
    }

    private suspend fun runAllAccounts() {
        Log.i(TAG, "开始执行 ${accounts.size} 个账号的刷量任务")
        
        for ((index, account) in accounts.withIndex()) {
            runAccount(account, index)
            if (index < accounts.size - 1) {
                delay(3000 + Math.abs(Random().nextLong()) % 5001)
            }
        }
        
        Log.i(TAG, "所有账号执行完毕")
        updateNotification("所有账号刷量完成")
        stopSelf()
    }

    private fun updateNotification(content: String) {
        try {
            val notification = createNotification(content)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }
}
