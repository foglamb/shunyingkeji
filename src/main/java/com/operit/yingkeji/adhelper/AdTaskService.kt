package com.operit.yingkeji.adhelper

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 自动广告刷量后台服务
 * 完全模拟Python脚本的刷量逻辑
 */
class AdTaskService : Service() {

    companion object {
        private const val TAG = "YingkejiAdHelper-Service"
        private const val CHANNEL_ID = "ad_task_channel"
        private const val NOTIFICATION_ID = 1001
        
        // ====== 固定常量（从Python脚本提取） ======
        private const val JWT_KEY = "059f0570a479a317932f175e9321c274"
        private const val DEVICE_SHA = "FA552E62ED750596E7F157BAEEE66F75BAB29D26"
        private const val DOMAIN = "1526xin.yingkeji.cc"
        private const val VERSION = "1077"
        private const val DIVIDENDS = "4"
        private val VALID_NETWORK_IDS = listOf("3", "4", "16", "18", "19", "40")
        private const val LOOP_COUNT = 2000
        private const val MIN_DELAY_MS = 20000L  // 20秒
        private const val MAX_DELAY_MS = 30000L  // 30秒
        private const val STOP_GOLD = 8000
        private const val X_CH = "64131e7a9286cfea"
    }

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    // 多账号配置（通过SharedPreferences存储）
    private var accounts = mutableListOf<AccountInfo>()
    
    // OkHttp客户端
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
        
        // 读取账号信息
        loadAccounts()
        
        if (accounts.isEmpty()) {
            Log.w(TAG, "没有配置账号，使用默认测试账号")
            accounts.add(AccountInfo(
                token = "test_token",
                oaid = "test_oaid",
                deviceId = "test_device"
            ))
        }
        
        // 启动刷量任务
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

    /**
     * 加载账号配置
     * 通过SharedPreferences读取
     */
    private fun loadAccounts() {
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
        
        Log.i(TAG, "已加载 ${accounts.size} 个账号")
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "刷量任务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(content: String): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("瞬影科技刷量助手")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
        }
        
        return builder.build()
    }

    // ==================== 以下完全模拟Python脚本的刷量逻辑 ====================

    /**
     * Base64 URL-safe编码（同Python的base64url_encode）
     */
    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    /**
     * 创建JWT（同Python的create_jwt）
     */
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
        val keySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        val signature = mac.doFinal("$headerB64.$payloadB64".toByteArray())
        
        return "$headerB64.$payloadB64.${base64UrlEncode(signature)}"
    }

    /**
     * MD5（同Python的md5_hex）
     */
    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(s.toByteArray())
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    /**
     * 随机后缀（同Python的random_suffix）
     */
    private fun randomSuffix(n: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..n).map { chars[Random().nextInt(chars.length)] }.joinToString("")
    }

    /**
     * HTTP请求（同Python的http_request）
     */
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
            val ts = (System.currentTimeMillis()).toString()
            // nc = random 100000-999999
            val nc = (100000 + Random().nextInt(900000)).toString()
            val tick = (System.currentTimeMillis() / 60000).toString()
            val rnd = randomSuffix(6)
            
            // sig = md5(token + ts + nc + '/' + path)[8:24]
            val sig = md5Hex(token + ts + nc + "/" + path).substring(8, 24)
            
            // tokena = create_jwt(token, JWT_KEY)
            val tokena = createJwt(token, JWT_KEY)
            
            // sha = create_jwt(DEVICE_SHA, JWT_KEY)
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
                    RequestBody.create(MediaType.parse("application/x-www-form-urlencoded"), body)
                } else null)
            
            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body()?.string() ?: ""
            
            return HttpResponse(
                status = response.code(),
                body = responseBody,
                error = null
            )
        } catch (e: Exception) {
            return HttpResponse(status = 0, body = "", error = e.message)
        }
    }

    /**
     * 获取广告配置（同Python的fetch_ad_config）
     */
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

    /**
     * 运行单个账号的刷量逻辑（同Python的run_account）
     */
    private suspend fun runAccount(account: AccountInfo, accIndex: Int) {
        withContext(Dispatchers.IO) {
            Log.i(TAG, "[账号${accIndex + 1}] 开始刷量")
            
            val config = fetchAdConfig(account.token)
            var adTypes = config.adTypes
            var placementIds = config.placementIds
            var availableTypes = config.availableTypes.toMutableList()
            
            var roundNum = 0
            var lastGold = 0
            
            while (roundNum < LOOP_COUNT) {
                roundNum++
                
                // 获取用户信息
                val userInfoResp = httpRequest(account.token, "GET", "api/Member/Guserinfo")
                if (userInfoResp.error != null) {
                    delay(5000)
                    continue
                }
                
                val gold: Int
                try {
                    val userData = JSONObject(userInfoResp.body)
                    val userinfo = userData.optJSONObject("data")?.optJSONObject("userinfo")
                    gold = userinfo?.optInt("forecast_gold", 0) ?: 0
                } catch (e: Exception) {
                    delay(5000)
                    continue
                }
                
                val delta = if (lastGold > 0) gold - lastGold else 0
                lastGold = gold
                
                if (gold >= STOP_GOLD) {
                    Log.i(TAG, "[账号${accIndex + 1}] ✅ 已满 $STOP_GOLD 金币，停止")
                    updateNotification("[账号${accIndex + 1}] 金币已满 $STOP_GOLD")
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
                    (1000000000000000L + Random().nextLong(8999999999999999L)).toString()
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

                // 构建请求body
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

                // 构建URL-encoded body
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

                // 随机延迟20-30秒
                val delay = MIN_DELAY_MS + Random().nextLong(MAX_DELAY_MS - MIN_DELAY_MS + 1)
                delay(delay)
            }

            Log.i(TAG, "[账号${accIndex + 1}] 完成刷量")
        }
    }

    /**
     * 构建URL编码的请求体
     */
    private fun buildUrlEncodedString(params: JSONObject): String {
        return params.keys().asSequence().map { key ->
            key + "=" + java.net.URLEncoder.encode(params.getString(key), "UTF-8")
        }.joinToString("&")
    }

    /**
     * 运行所有账号
     */
    private suspend fun runAllAccounts() {
        Log.i(TAG, "开始执行 ${accounts.size} 个账号的刷量任务")
        
        for ((index, account) in accounts.withIndex()) {
            runAccount(account, index)
            // 账号之间延迟3-8秒
            delay(3000 + Random().nextLong(5001))
        }
        
        Log.i(TAG, "所有账号执行完毕")
        updateNotification("所有账号刷量完成")
        stopSelf()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
