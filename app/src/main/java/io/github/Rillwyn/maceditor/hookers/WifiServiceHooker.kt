package io.github.Rillwyn.maceditor.hookers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.util.Log
import io.github.Rillwyn.maceditor.BuildConfig
import io.github.Rillwyn.maceditor.TAG
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

class WifiServiceHooker {
    companion object {
        var module: XposedModule? = null
            private set

        const val ACTION_APPLY_MAC = "${BuildConfig.APPLICATION_ID}.ACTION_APPLY_MAC"
        const val ACTION_MAC_DETECTED = "${BuildConfig.APPLICATION_ID}.ACTION_MAC_DETECTED"
        private const val RECEIVER_CLASS = "${BuildConfig.APPLICATION_ID}.MacBroadcastReceiver"

        // cached WifiNative state
        private var nativeInstance: Any? = null
        private var nativeSetStaMethod: Method? = null
        private var nativeSetApMethod: Method? = null
        private var lastIfaceName: String? = null
        private var receiverRegistered = false

        @SuppressLint("PrivateApi")
        fun hook(param: SystemServerStartingParam, module: XposedModule) {
            this.module = module
            module.hook(
                param.classLoader.loadClass("com.android.server.SystemServiceManager")
                    .getDeclaredMethod("loadClassFromLoader", String::class.java, ClassLoader::class.java)
            ).intercept { chain ->
                val result = chain.proceed()
                val className = chain.getArg(0) as String
                if (className == "com.android.server.wifi.WifiService") {
                    val cl = chain.getArg(1) as ClassLoader
                    val nativeClass = cl.loadClass("com.android.server.wifi.WifiNative")
                    val setStaMethod = nativeClass.getDeclaredMethod("setStaMacAddress", String::class.java, MacAddress::class.java)
                    val setApMethod = nativeClass.getDeclaredMethod("setApMacAddress", String::class.java, MacAddress::class.java)
                    nativeSetStaMethod = setStaMethod
                    nativeSetApMethod = setApMethod
                    val hooker = MacAddrHooker()
                    module.hook(setStaMethod).intercept(hooker)
                    module.hook(setApMethod).intercept(hooker)
                    module.log(Log.INFO, TAG, "Hooked WifiNative.setStaMacAddress and setApMacAddress")
                }
                result
            }
        }

        @SuppressLint("PrivateApi")
        private fun _getSystemContext(): Context? {
            return try {
                val at = Class.forName("android.app.ActivityThread")
                at.getMethod("currentApplication").invoke(null) as? Context
            } catch (_: Exception) {
                null
            }
        }

        private fun _registerApplyReceiver() {
            if (receiverRegistered) return
            val ctx = _getSystemContext() ?: return
            ctx.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    _applyMacDirectly()
                }
            }, IntentFilter(ACTION_APPLY_MAC), Context.RECEIVER_EXPORTED)
            receiverRegistered = true
            module?.log(Log.INFO, TAG, "Registered apply-MAC receiver in system_server")
        }

        private fun _applyMacDirectly() {
            val native = nativeInstance
            val method = nativeSetStaMethod
            val iface = lastIfaceName
            if (native == null || method == null || iface == null) {
                module?.log(Log.WARN, TAG, "Cannot apply MAC: WifiNative not cached yet")
                return
            }
            val prefs = module?.getRemotePreferences(BuildConfig.APPLICATION_ID)
            val mac = prefs?.getString("customMac", "") ?: ""
            if (mac.isEmpty()) return

            try {
                method.invoke(native, iface, MacAddress.fromString(mac))
                module?.log(Log.INFO, TAG, "Directly applied MAC: $mac on $iface")
            } catch (e: Exception) {
                module?.log(Log.ERROR, TAG, "Failed to directly apply MAC: $e")
            }
        }

        private fun _broadcastDeviceMac(mac: MacAddress) {
            try {
                val ctx = _getSystemContext() ?: return
                val intent = Intent(ACTION_MAC_DETECTED).apply {
                    putExtra("mac", mac.toString())
                    setClassName(BuildConfig.APPLICATION_ID, RECEIVER_CLASS)
                }
                ctx.sendBroadcast(intent)
            } catch (e: Exception) {
                module?.log(Log.WARN, TAG, "Could not broadcast MAC: $e")
            }
        }

        class MacAddrHooker : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val prefs = module?.getRemotePreferences(BuildConfig.APPLICATION_ID)
                val hookActive = prefs?.getBoolean("hookActive", true) ?: true

                if (!hookActive) return chain.proceed()

                // cache WifiNative instance and iface
                nativeInstance = chain.thisObject
                val ifaceName = chain.getArg(0) as? String
                lastIfaceName = ifaceName
                _registerApplyReceiver()

                // broadcast the system-assigned MAC to the app
                (chain.getArg(1) as? MacAddress)?.let { _broadcastDeviceMac(it) }

                // ------------------------------------------------------------
                // 改动：直接根据接口名称判断是否为 AP 接口（通常为 wlan2）
                // 如果是 AP 接口，且 apMacOverride 开关为 false，则直接放行，不替换 MAC
                // ------------------------------------------------------------
                val apOverride = prefs?.getBoolean("apMacOverride", false) ?: false
                if (ifaceName != null && ifaceName.startsWith("wlan") && !ifaceName.equals("wlan0", ignoreCase = true)) {
                    // 非 wlan0 的 wlan 接口通常为 AP（如 wlan2）
                    if (!apOverride) {
                        module?.log(Log.INFO, TAG, "AP MAC override disabled, skipping replacement for $ifaceName")
                        return chain.proceed()
                    }
                }

                val customMac = prefs?.getString("customMac", "") ?: ""
                if (customMac.isNotEmpty()) {
                    val args = chain.args.toTypedArray()
                    args[1] = MacAddress.fromString(customMac)
                    module?.log(Log.INFO, TAG, "Replacing MAC with $customMac on $ifaceName")
                    return chain.proceed(args)
                }
                return chain.proceed()
            }
        }
    }
}