package com.operit.yingkeji.adhelper

import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "Yingkeji-Hook"
        private const val PKG = "com.yxrjshun.yingkeji"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG) return
        
        Log.i(TAG, "[*] 瞬影科技刷量模块加载成功")

        // 代理启动刷量
        hookAppStart(lpparam)

        // 扫描API类
        scanApiClasses(lpparam)
    }

    private fun hookAppStart(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val appCls = lpparam.classLoader.loadClass("android.app.Application")
            XposedHelpers.findAndHookMethod(appCls, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = param.thisObject as android.app.Application
                    Log.i(TAG, "[*] 应用启动，启动刷量服务")
                    try {
                        ctx.startForegroundService(android.content.Intent(ctx, AdTaskService::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "启动Service失败: ${e.message}")
                    }
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Hook失败: ${e.message}")
        }
    }

    private fun scanApiClasses(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        val apis = listOf(
            "$PKG.api.ApiService",
            "$PKG.data.api.ApiService",
            "$PKG.data.remote.ApiService",
            "$PKG.net.ApiService",
            "$PKG.network.Api",
            "$PKG.utils.HttpUtils",
            "$PKG.net.HttpManager",
            "$PKG.utils.SignUtils",
            "$PKG.security.JWT"
        )
        for (name in apis) {
            try {
                val c = cl.loadClass(name)
                Log.i(TAG, "[+] 找到: $name")
                for (m in c.declaredMethods) {
                    Log.d(TAG, "   方法: ${m.name}(${m.parameterTypes.joinToString { it.simpleName }})")
                }
            } catch (_: Throwable) { }
        }
    }
}
