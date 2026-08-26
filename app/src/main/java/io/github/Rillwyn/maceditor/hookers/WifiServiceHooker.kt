package io.github.Rillwyn.maceditor.hookers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import io.github.Rillwyn.maceditor.BuildConfig
import io.github.Rillwyn.maceditor.MacBroadcastReceiver
import io.github.Rillwyn.maceditor.TAG
import java.io.File
import java.lang.reflect.Method

/**
 * 系统框架（system_server）Hook 实现。
 *
 * - 通过 YukiHookAPI 的 [YukiHookPrefsBridge] 跨进程读取模块偏好设置
 *   （宿主进程内 XSharedPreferences 只读、模块应用内可读写）；
 * - 使用 `Member.hook`（YukiHookAPI 推荐的新写法）直接 Hook 目标方法，
 *   避免旧 finder 写法（`method { }.hook { }`）在部分环境的重载解析问题；
 * - Hook `WifiNative` 全部构造器缓存实例，保证“应用 MAC”随时可用。
 */
object WifiServiceHooker {

    /** 应用点击“应用 MAC”时发送的广播 Action */
    const val ACTION_APPLY_MAC = "${BuildConfig.APPLICATION_ID}.ACTION_APPLY_MAC"

    /** 广播中携带的目标 MAC 键名（避免首次安装后跨进程 prefs 读取时序问题） */
    const val EXTRA_MAC = "mac"

    /** 系统将当前 MAC 广播给应用（用于 UI 展示，尽力而为） */
    const val ACTION_MAC_DETECTED = "${BuildConfig.APPLICATION_ID}.ACTION_MAC_DETECTED"

    /** 模块偏好文件名（与模块应用侧保持一致，兼容历史数据） */
    private const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    private const val WIFI_NATIVE_CLASS = "com.android.server.wifi.WifiNative"
    private const val WIFI_VENDOR_HAL_CLASS = "com.android.server.wifi.WifiVendorHal"

    /** 缓存的 WifiNative 实例（构造时缓存，保证“应用 MAC”随时可用） */
    @Volatile
    private var nativeInstance: Any? = null

    /** 当前 STA 接口名（wlan0） */
    @Volatile
    private var lastIface: String? = null

    /** 缓存 setStaMacAddress 方法引用 */
    @Volatile
    private var setStaMethod: Method? = null

    /** 广播接收器是否已注册 */
    @Volatile
    private var applyReceiverRegistered = false

    /** 系统 Context（惰性获取后缓存） */
    @Volatile
    private var systemContext: Context? = null

    /** 最近一次广播的系统 MAC（用于主动拉取回退） */
    @Volatile
    private var lastBroadcastMac: String? = null

    /** 模块偏好桥接（宿主进程内只读） */
    private lateinit var modulePrefs: YukiHookPrefsBridge

    /**
     * 在 [PackageParam]（loadSystem 作用域）中安装全部 Hook。
     */
    fun hook(param: PackageParam) {
        modulePrefs = param.prefs(PREFS_NAME)
        val appLoader = param.appClassLoader ?: return

        with(param) {
            // 尝试 1：WifiNative 已在 bootclasspath 时直接 Hook
            tryHookWifiNative(appLoader)

            // 尝试 2（与可正常工作的参考实现同机制）：监听 SystemServiceManager
            // 加载 WifiService 的时刻，在 WifiNative 类加载后再次 Hook，
            // 确保任何加载时序下都能装上。
            hookSystemServiceManager(appLoader)
        }

        // 注册“应用 MAC”广播接收器（优先使用系统 Context；
        // 若 system 尚未就绪会失败，稍后由延迟任务与首次 MAC 调用时重试）。
        registerApplyReceiver(param)
        // 系统就绪后（延迟 5s）再补一次注册，确保 wifi 关闭时用户点击也能收到广播
        Thread {
            try {
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
            }
            registerApplyReceiver(null)
        }.apply { isDaemon = true }.start()

        // 通过 YukiHookDataChannel 响应应用“主动拉取系统 MAC”的请求：
        // 应用打开时发送 mac_request，这里回复当前系统 MAC（mac_result）。
        runCatching {
            param.dataChannel.with {
                wait<String>("mac_request") { _ ->
                    val mac = currentSystemMac()
                    if (mac.isNotEmpty()) {
                        put("mac_result", mac)
                        YLog.debug("Replied system MAC $mac via data channel", tag = TAG)
                    } else {
                        YLog.debug("No system MAC available to reply", tag = TAG)
                    }
                }
            }
        }.onFailure {
            YLog.debug("Data channel unavailable: $it", tag = TAG)
        }
        YLog.debug("WifiServiceHooker installed", tag = TAG)
    }

    /**
     * 获取系统原始 MAC（出厂 MAC 优先，优先级从高到低）：
     * 1. 反射 WifiNative.getFactoryMacAddress(iface)（Android 12+ 系统 API，
     *    返回硬件出厂 MAC，不受随机化与模块替换影响）；
     * 2. 解析高通 wlan_mac.bin（Intf0MacAddress，厂商出厂 MAC 存储）；
     * 3. 最近一次系统设置的原始 MAC（参数替换前捕获）；
     * 4. getStaMacAddress / /sys/class/net/wlan0/address（随机化或替换后的值，仅回退）。
     */
    private fun currentSystemMac(): String {
        val native = nativeInstance
        val iface = lastIface ?: "wlan0"
        // 诊断：打印可取 MAC 的方法（用于确定出厂 MAC 的获取 API）
        if (native != null) {
            runCatching {
                val list = native.javaClass.declaredMethods
                    .filter { it.name.contains("Mac", ignoreCase = true) }
                    .map { "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }})" }
                    .sorted()
                YLog.debug("native=${native.javaClass.simpleName} Mac methods: $list", tag = TAG)
            }
            // 1. 出厂 MAC：尝试多种方法名与签名
            //    - getStaFactoryMacAddress(String)：ColorOS/OPPO（WifiNative 与 WifiVendorHal 均有）
            //    - getFactoryMacAddress(String)：AOSP 标准（Android 12+）
            val factoryCandidates = listOf(
                "getStaFactoryMacAddress" to arrayOf<Class<*>>(String::class.java),
                "getStaFactoryMacAddress" to emptyArray<Class<*>>(),
                "getFactoryMacAddress" to arrayOf<Class<*>>(String::class.java),
                "getFactoryMacAddress" to emptyArray<Class<*>>()
            )
            for ((methodName, params) in factoryCandidates) {
                runCatching {
                    val m = native.javaClass.getDeclaredMethod(methodName, *params)
                    val args = if (params.isEmpty()) emptyArray<Any?>() else arrayOf<Any?>(iface)
                    (m.invoke(native, *args) as? MacAddress)?.toString()
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                    YLog.debug("Factory MAC via $methodName: $it", tag = TAG)
                    return it
                }
            }
        } else {
            YLog.debug("nativeInstance is null, skip getFactoryMacAddress", tag = TAG)
        }
        // 2. 高通 wlan_mac.bin（Intf0MacAddress=xxxxxxxxxxxx）
        runCatching {
            val f = File("/mnt/vendor/persist/qca6390/wlan_mac.bin")
            YLog.debug("wlan_mac.bin exists=${f.exists()} readable=${f.canRead()}", tag = TAG)
            if (f.exists()) {
                val text = f.readText()
                val m = Regex("Intf0MacAddress=([0-9A-Fa-f]{12})").find(text)
                m?.groupValues?.get(1)?.chunked(2)?.joinToString(":")?.uppercase()?.let {
                    YLog.debug("Factory MAC via wlan_mac.bin: $it", tag = TAG)
                    return it
                }
            }
        }.onFailure { YLog.debug("wlan_mac.bin read failed: $it", tag = TAG) }
        // 3. 最近一次系统设置的原始 MAC（替换前捕获）
        lastBroadcastMac?.takeIf { it.isNotEmpty() }?.let { return it }
        // 4. 回退：当前生效 MAC
        if (native != null) {
            runCatching {
                val m = native.javaClass.getDeclaredMethod("getStaMacAddress", String::class.java)
                (m.invoke(native, iface) as? MacAddress)?.toString()
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        runCatching {
            val addr = File("/sys/class/net/wlan0/address").readText().trim().uppercase()
            if (addr.matches(Regex("^[0-9A-F:]{17}$"))) return addr
        }.getOrNull()
        return ""
    }

    /**
     * 监听 SystemServiceManager.loadClassFromLoader，当 WifiService 类被加载时
     * 使用其 ClassLoader Hook WifiNative（保证类已就绪）。
     */
    private fun PackageParam.hookSystemServiceManager(loader: ClassLoader?) {
        val ssm = "com.android.server.SystemServiceManager".toClassOrNull(loader) ?: return
        val method = runCatching {
            ssm.getDeclaredMethod("loadClassFromLoader", String::class.java, ClassLoader::class.java)
        }.getOrNull() ?: return
        method.hook {
            after {
                if (args(0).cast<String>() == "com.android.server.wifi.WifiService") {
                    val cl = args(1).cast<ClassLoader>()
                    if (cl != null) {
                        YLog.debug("WifiService class loaded, (re)installing WifiNative hooks", tag = TAG)
                        tryHookWifiNative(cl)
                    }
                }
            }
        }?.ignoredHookingFailure()
    }

    /**
     * Hook WifiNative：缓存实例（构造器）、拦截 setStaMacAddress/setApMacAddress、
     * 记录 STA 接口名。
     */
    private fun PackageParam.tryHookWifiNative(loader: ClassLoader?) {
        val nativeClass = WIFI_NATIVE_CLASS.toClassOrNull(loader) ?: run {
            YLog.warn("WifiNative class not found", tag = TAG)
            return
        }
        // 诊断：打印可取 MAC 的系统方法（用于确定出厂 MAC 的获取 API）
        runCatching {
            val macMethods = (nativeClass.declaredMethods + WIFI_VENDOR_HAL_CLASS.toClassOrNull(loader)
                ?.declaredMethods.orEmpty())
                .filter { it.name.contains("Mac", ignoreCase = true) }
                .map { "${it.declaringClass.simpleName}.${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }})" }
                .distinct()
                .sorted()
            YLog.debug("Mac-related methods: $macMethods", tag = TAG)
        }.onFailure { YLog.debug("Method list dump failed: $it", tag = TAG) }
        // 缓存 WifiNative 实例：Hook 全部构造器，系统一创建实例即缓存。
        nativeClass.declaredConstructors.forEach { ctor ->
            ctor.hook {
                after {
                    instanceOrNull?.let { nativeInstance = it }
                    YLog.debug("WifiNative instance cached", tag = TAG)
                }
            }?.ignoredHookingFailure()
        }
        // 拦截 setStaMacAddress / setApMacAddress（WifiNative 与 WifiVendorHal，
        // 个别版本签名不同时静默跳过，不影响其它 Hook）。
        hookStaApMethods(WIFI_NATIVE_CLASS, loader, "STA")
        hookStaApMethods(WIFI_NATIVE_CLASS, loader, "AP")
        hookStaApMethods(WIFI_VENDOR_HAL_CLASS, loader, "STA")
        hookStaApMethods(WIFI_VENDOR_HAL_CLASS, loader, "AP")
        // 记录 STA 接口名
        nativeClass.declaredMethods.firstOrNull { it.name == "setupForClientMode" }?.hook {
            after {
                if (args.isNotEmpty()) {
                    args(0).cast<String>()?.let { lastIface = it }
                }
            }
        }?.ignoredHookingFailure()
        YLog.debug("WifiNative hooks installed", tag = TAG)
    }

    /**
     * Hook 指定类上的 setStaMacAddress / setApMacAddress（(String, MacAddress) 签名）。
     */
    private fun PackageParam.hookStaApMethods(clazzName: String, loader: ClassLoader?, type: String) {
        val clazz = clazzName.toClassOrNull(loader) ?: return
        val methodName = if (type == "STA") "setStaMacAddress" else "setApMacAddress"
        val method = runCatching {
            clazz.getDeclaredMethod(methodName, String::class.java, MacAddress::class.java)
        }.getOrNull() ?: return
        method.hook {
            before { handleMacCall(this, type) }
        }?.ignoredHookingFailure()
    }

    /**
     * 拦截 MAC 设置调用：根据偏好替换为自定义 MAC。
     */
    private fun handleMacCall(p: HookParam, type: String) {
        val iface = p.args(0).cast<String>()
        // 诊断：确认 hook 是否被系统触发（无论是否替换都会打印）
        YLog.debug("set${type}MacAddress called${iface?.let { " on $it" } ?: ""}", tag = TAG)
        if (!modulePrefs.getBoolean("hookActive", true)) {
            YLog.debug("hookActive is off, skip", tag = TAG)
            return
        }
        if (iface == null) return
        lastIface = iface
        nativeInstance = p.instanceOrNull

        // 系统就绪后（首次 MAC 调用时）确保“应用 MAC”接收器已注册
        registerApplyReceiver(null)

        // 广播系统当前 MAC 给应用（用于 UI 展示，尽力而为）
        p.args(1).cast<MacAddress>()?.let { broadcastMac(it) }

        // AP 覆写开关：非 wlan0 的 AP 接口默认不替换，避免热点无法启动
        val apOverride = modulePrefs.getBoolean("apMacOverride", false)
        if (iface.startsWith("wlan") && !iface.equals("wlan0", ignoreCase = true) && !apOverride) {
            return
        }

        val customMac = modulePrefs.getString("customMac", "")
        if (customMac.isNotEmpty()) {
            runCatching {
                p.args(1).set(MacAddress.fromString(customMac))
                YLog.debug("Replaced MAC with $customMac on $iface ($type)", tag = TAG)
            }.onFailure {
                YLog.error("Failed to parse custom MAC: $it", tag = TAG)
            }
        } else {
            YLog.debug("customMac is empty, no replacement", tag = TAG)
        }
    }

    /**
     * 注册接收 [ACTION_APPLY_MAC] 广播的接收器，收到后立即用缓存的 native 实例
     * 直接调用 setStaMacAddress 应用 MAC。system 未就绪时静默失败，稍后重试。
     *
     * @param param 当前 PackageParam（提供更可靠的系统 Context），可为 null
     */
    private fun registerApplyReceiver(param: PackageParam?) {
        if (applyReceiverRegistered) return
        val ctx = runCatching { param?.systemContext }.getOrNull() ?: getSystemContext() ?: return
        runCatching {
            ctx.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        // 广播可直接携带目标 MAC（不依赖跨进程 prefs 读取，
                        // 确保首次安装后第一次点击也能立即生效）
                        val mac = intent.getStringExtra(EXTRA_MAC)
                        if (mac.isNullOrEmpty()) applyMacDirectly()
                        else applyMacDirectly(mac)
                    }
                },
                IntentFilter(ACTION_APPLY_MAC),
                Context.RECEIVER_EXPORTED
            )
            applyReceiverRegistered = true
            YLog.debug("Apply MAC receiver registered", tag = TAG)
        }.onFailure {
            // system_server 启动早期 AMS 未就绪时首次注册会失败，
            // 属正常现象（延迟任务会重试），仅降级为 debug 日志避免误报。
            YLog.debug("Apply receiver not ready yet, will retry later", tag = TAG)
        }
    }

    /**
     * 直接应用 MAC（利用缓存的 WifiNative 实例与接口名）。
     *
     * @param intentMac 广播携带的目标 MAC；为空时回退读取偏好设置。
     *                  WifiNative 实例尚未缓存（重启后 WiFi 未初始化）时自动延迟重试，
     *                  保证第一次点击也能生效。
     */
    private fun applyMacDirectly(intentMac: String? = null) {
        val mac = intentMac ?: modulePrefs.getString("customMac", "")
        if (mac.isEmpty()) return
        val native = nativeInstance
        val iface = lastIface
        if (native == null || iface == null) {
            YLog.warn("WifiNative not cached yet, will retry", tag = TAG)
            retryApplyMac(intentMac)
            return
        }
        runCatching {
            val method = setStaMethod ?: native.javaClass
                .getDeclaredMethod("setStaMacAddress", String::class.java, MacAddress::class.java)
                .also { setStaMethod = it }
            method.invoke(native, iface, MacAddress.fromString(mac))
            YLog.debug("Directly applied MAC $mac on $iface", tag = TAG)
        }.onFailure {
            YLog.error("Direct apply failed: $it", tag = TAG)
        }
    }

    /**
     * WifiNative 实例未就绪时延迟重试（最多约 8 秒），直到实例可用。
     */
    private fun retryApplyMac(intentMac: String?) {
        Thread {
            repeat(8) {
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (nativeInstance != null && lastIface != null) {
                    applyMacDirectly(intentMac)
                    return@Thread
                }
            }
            YLog.warn("Give up applying MAC: WifiNative never became available", tag = TAG)
        }.apply { isDaemon = true }.start()
    }

    /**
     * 将系统当前 MAC 广播给模块应用（写回本地缓存，用于状态卡展示）。
     */
    private fun broadcastMac(mac: MacAddress) {
        val ctx = getSystemContext() ?: return
        runCatching {
            val intent = Intent(ACTION_MAC_DETECTED).apply {
                putExtra(MacBroadcastReceiver.EXTRA_MAC, mac.toString())
                setClassName(BuildConfig.APPLICATION_ID, MacBroadcastReceiver::class.java.name)
            }
            ctx.sendBroadcast(intent)
            lastBroadcastMac = mac.toString()
            YLog.debug("Broadcasted system MAC $mac", tag = TAG)
        }.onFailure {
            YLog.error("Broadcast MAC failed: $it", tag = TAG)
        }
    }

    /**
     * 获取系统框架 Context（惰性获取并缓存）。
     */
    private fun getSystemContext(): Context? {
        systemContext?.let { return it }
        val ctx = runCatching {
            val at = Class.forName("android.app.ActivityThread")
            at.getMethod("currentApplication").invoke(null) as? Context
        }.getOrNull()
        systemContext = ctx
        return ctx
    }
}
