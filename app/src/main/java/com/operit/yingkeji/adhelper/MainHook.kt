package com.operit.yingkeji.adhelper

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "YingkejiAdHelper-Hook"
        private const val TARGET_PACKAGE = "com.yxrjshun.yingkeji"
        private const val MODULE_PACKAGE = "com.operit.yingkeji.adhelper"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只在目标应用（瞬影科技）中Hook
        if (lpparam.packageName != TARGET_PACKAGE) return
        
        Log.i(TAG, "已加载到目标应用: ${lpparam.packageName}")
        
        try {
            // 方案1: Hook OkHttp的Request.Builder.addHeader方法
            // 当瞬影科技应用发送HTTP请求时，拦截header中的token/oaid/deviceid
            hookOkHttpHeaders(lpparam)
        } catch (e: Exception) {
            Log.e(TAG, "Hook OkHttp失败: ${e.message}")
        }
        
        try {
            // 方案2: 通过ClassLoader搜索可能存放token的类
            hookTokenStorage(lpparam)
        } catch (e: Exception) {
            Log.e(TAG, "Hook TokenStorage失败: ${e.message}")
        }
    }

    private fun hookOkHttpHeaders(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        
        try {
            // Hook OkHttp的Request.Builder
            val builderClass = classLoader.loadClass("okhttp3.Request\$Builder")
            
            XposedHelpers.findAndHookMethod(builderClass, "addHeader", String::class.java, String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args[0] as? String ?: return
                    val value = param.args[1] as? String ?: return
                    
                    // 捕获关键参数
                    when (name.lowercase()) {
                        "token" -> saveAutoParam("auto_token", value)
                        "oaid" -> saveAutoParam("auto_oaid", value)
                        "deviceid" -> saveAutoParam("auto_deviceid", value)
                    }
                }
            })
            
            Log.i(TAG, "✅ Hook OkHttp Request.Builder.addHeader 成功")
        } catch (e: Exception) {
            Log.e(TAG, "Hook OkHttp Request.Builder失败: ${e.message}")
        }
        
        try {
            // 也尝试Hook OkHttp的Request.Builder.setHeader
            val builderClass = classLoader.loadClass("okhttp3.Request\$Builder")
            XposedHelpers.findAndHookMethod(builderClass, "setHeader", String::class.java, String::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val name = param.args[0] as? String ?: return
                    val value = param.args[1] as? String ?: return
                    
                    when (name.lowercase()) {
                        "token" -> saveAutoParam("auto_token", value)
                        "oaid" -> saveAutoParam("auto_oaid", value)
                        "deviceid" -> saveAutoParam("auto_deviceid", value)
                    }
                }
            })
            
            Log.i(TAG, "✅ Hook OkHttp Request.Builder.setHeader 成功")
        } catch (e: Exception) {
            Log.e(TAG, "Hook OkHttp Request.Builder.setHeader失败: ${e.message}")
        }
    }

    private fun hookTokenStorage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val classLoader = lpparam.classLoader
        
        // 尝试常见的类名和字段名来捕获token/oaid/deviceid
        val possibleClasses = listOf(
            "com.yxrjshun.yingkeji.network.ApiClient",
            "com.yxrjshun.yingkeji.network.HttpClient",
            "com.yxrjshun.yingkeji.data.UserManager",
            "com.yxrjshun.yingkeji.data.AccountManager",
            "com.yxrjshun.yingkeji.utils.DeviceUtils"
        )
        
        for (className in possibleClasses) {
            try {
                val clazz = classLoader.loadClass(className)
                Log.i(TAG, "找到类: $className")
                
                // 尝试Hook所有public方法，捕获返回token/oaid/deviceid的方法
                for (method in clazz.methods) {
                    val methodName = method.name.lowercase()
                    
                    if (methodName.contains("token") || methodName.contains("auth") || 
                        methodName.contains("login") || methodName.contains("init")) {
                        
                        if (method.parameterTypes.isEmpty()) {
                            val returnType = method.returnType
                            if (returnType == String::class.java) {
                                XposedHelpers.findAndHookMethod(clazz, method.name, object : XC_MethodHook() {
                                    override fun afterHookedMethod(param: MethodHookParam) {
                                        val result = param.result as? String ?: return
                                        if (result.length > 10 && !result.contains(" ")) {
                                            if (methodName.contains("token")) {
                                                saveAutoParam("auto_token", result)
                                                Log.i(TAG, "自动捕获Token: ${result.take(16)}...")
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun saveAutoParam(key: String, value: String) {
        if (value.length < 10) return // 太短的不保存
        
        try {
            // 通过模块的Context获取SharedPreferences
            val context = XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentApplication"
            ) as? Context ?: return
            
            val prefs = context.createPackageContext(MODULE_PACKAGE, 0)
                .getSharedPreferences("ad_helper_config", Context.MODE_PRIVATE)
            
            // 只在有意义的参数时才保存
            val existing = prefs.getString(key, "") ?: ""
            if (existing != value) {
                prefs.edit().putString(key, value).apply()
                Log.i(TAG, "💾 已保存自动捕获参数: $key = ${value.take(16)}...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存自动捕获参数失败: ${e.message}")
        }
    }
}
