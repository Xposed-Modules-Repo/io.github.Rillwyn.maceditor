package io.github.Rillwyn.maceditor.hookers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.MacAddress
import android.os.Build
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import io.github.Rillwyn.maceditor.BuildConfig
import io.github.Rillwyn.maceditor.MacBroadcastReceiver
import io.github.Rillwyn.maceditor.TAG
import io.github.Rillwyn.maceditor.utils.MacUtils
import java.io.File
import java.lang.reflect.Method
import java.net.NetworkInterface

/**
 * Android 系统框架（`system_server`）Wi-Fi 与移动热点核心 Hook 引擎。
 *
 * 架构设计与兼容范围：
 * 1. **全版本与多厂商覆盖 (Android 10 至 Android 16)**：深度适配 AOSP 原生及 Google Pixel、Samsung (One UI)、
 *    Xiaomi / Redmi (MIUI / HyperOS)、Oppo / OnePlus / Realme (ColorOS / OxygenOS)、Vivo / iQOO (OriginOS / Funtouch OS)、
 *    Honor / Huawei (MagicOS / EMUI)、Motorola、Sony、Asus、Transsion (Infinix / Tecno)、Nothing、HTC、ZTE 等全量厂商 ROM。
 * 2. **多层级 ClassLoader 发现机制**：
 *    - 静态 BootClassLoader 探测；
 *    - 拦截 [com.android.server.SystemServiceManager.loadClassFromLoader]（捕获 Android 12+ APEX `service-wifi.jar` 动态加载）；
 *    - 拦截 [android.os.ServiceManager.addService]（捕获 `wifi` 服务注册时机）；
 *    - 动态反射 [android.os.ServiceManager.getService] 提取已运行服务的类加载器。
 * 3. **零点击（Zero-Click）实时应用体系**：
 *    - 模块开关或 MAC 变更时，通过动态 IPC 广播直接下发目标参数；
 *    - 绕过异步持久化读写时序瓶颈，毫秒级直接调用底层 `WifiNative` / `WifiVendorHal` 实例与活跃网络接口，无需重启 Wi-Fi 或热点。
 * 4. **动态 AP 接口感知与 IEEE 802.11 协议合规**：
 *    - 自动嗅探 `ap0`、`softap0`、`swlan0`、`wlan1` 等不同芯片驱动命名；
 *    - 对 AP MAC 强制执行单播位（bit 0 = 0）与本地管理位（bit 1 = 1）校验与格式化，避免 hostapd 固件崩溃。
 *
 * @sample
 * ```kotlin
 * // 在 HookEntry 的 loadSystem 回调中挂载
 * WifiServiceHooker.hook(this)
 * ```
 */
object WifiServiceHooker {

    /** 应用点击“应用 MAC”时发送的系统级广播 Action。 */
    const val ACTION_APPLY_MAC = "${BuildConfig.APPLICATION_ID}.ACTION_APPLY_MAC"

    /** 模块设置项开关改变时发送的广播 Action，用于触发 HAL 与活跃接口的零延迟即时同步。 */
    const val ACTION_CONFIG_CHANGED = "${BuildConfig.APPLICATION_ID}.ACTION_CONFIG_CHANGED"

    /** 仅更新移动热点（AP 模式）MAC 地址的专用广播 Action。 */
    const val ACTION_APPLY_AP_MAC = "${BuildConfig.APPLICATION_ID}.ACTION_APPLY_AP_MAC"

    /** 广播意图附加数据键：目标 MAC 地址字符串。 */
    const val EXTRA_MAC = "mac"

    /** 广播意图附加数据键：MAC 覆写主开关状态。 */
    const val EXTRA_HOOK_ACTIVE = "hookActive"

    /** 广播意图附加数据键：AP MAC 覆写开关状态。 */
    const val EXTRA_AP_MAC_OVERRIDE = "apMacOverride"

    /** 广播意图附加数据键：强制开启 MAC 随机化开关状态。 */
    const val EXTRA_FORCE_RANDOMIZATION = "forceShowMacRandomization"

    /** 广播意图附加数据键：用户自定义 MAC 字符串。 */
    const val EXTRA_CUSTOM_MAC = "customMac"

    /** 系统服务向模块应用广播当前物理出厂 MAC 的 Action（用于 UI 展示）。 */
    const val ACTION_MAC_DETECTED = "${BuildConfig.APPLICATION_ID}.ACTION_MAC_DETECTED"

    /** 跨进程共享偏好存储文件名。 */
    private const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    /** 目标待拦截的 Wi-Fi Native 类全限定名列表（涵盖 AOSP 及各大厂商专有定制扩展）。 */
    private val TARGET_WIFI_NATIVE_CLASSES = listOf(
        "com.android.server.wifi.WifiNative",
        "com.android.server.wifi.SemWifiNative",
        "com.samsung.android.server.wifi.SemWifiNative",
        "com.android.server.wifi.MiuiWifiNative",
        "com.mediatek.server.wifi.MtkWifiNative",
        "com.android.server.wifi.HwWifiNative",
        "com.huawei.server.wifi.HwWifiNative"
    )

    /** 目标待拦截的 Wi-Fi HAL 硬件抽象层类全限定名列表。 */
    private val TARGET_WIFI_HAL_CLASSES = listOf(
        "com.android.server.wifi.WifiVendorHal",
        "com.android.server.wifi.SemWifiVendorHal",
        "com.samsung.android.server.wifi.SemWifiVendorHal"
    )

    /** MAC 覆写主开关内存缓存（支持多线程高并发可见性）。 */
    @Volatile
    var isHookActive: Boolean = true

    /** 移动热点（AP 模式）MAC 独立覆写开关内存缓存。 */
    @Volatile
    var isApMacOverride: Boolean = false

    /** 强制开启系统 MAC 随机化支持标志位内存缓存。 */
    @Volatile
    var isForceRandomization: Boolean = true

    /** 当前用户配置的自定义 MAC 地址内存缓存。 */
    @Volatile
    var currentCustomMac: String = ""

    /** 缓存的活跃 WifiNative 实例对象（在构造器执行时即时捕获）。 */
    @Volatile
    private var nativeInstance: Any? = null

    /** 记录最近一次捕获到的 STA 客户端网卡接口名（如 `"wlan0"`）。 */
    @Volatile
    private var lastIface: String? = null

    /** 记录最近一次捕获到的 AP 移动热点网卡接口名（如 `"ap0"`, `"softap0"`, `"swlan0"`, `"wlan1"`）。 */
    @Volatile
    private var lastApIface: String? = null

    /** 动态广播接收器是否已成功注册的标志位。 */
    @Volatile
    private var applyReceiverRegistered = false

    /** 缓存的系统框架级 Context 引用。 */
    @Volatile
    private var systemContext: Context? = null

    /** 记录最近一次向应用端广播的出厂/硬件 MAC 地址字符串。 */
    @Volatile
    private var lastBroadcastMac: String? = null

    /** 模块共享偏好设置桥接（在 `system_server` 宿主内为只读映射）。 */
    private lateinit var modulePrefs: YukiHookPrefsBridge

    /**
     * 在 `system_server` 系统包环境中安装与初始化所有 Wi-Fi 服务相关的 Hook 拦截点。
     *
     * @param param YukiHookAPI 注入的 [PackageParam] 系统包环境参数。
     */
    fun hook(param: PackageParam) {
        modulePrefs = param.prefs(PREFS_NAME)
        isHookActive = modulePrefs.getBoolean("hookActive", true)
        isApMacOverride = modulePrefs.getBoolean("apMacOverride", false)
        isForceRandomization = modulePrefs.getBoolean("forceShowMacRandomization", true)
        currentCustomMac = modulePrefs.getString("customMac", "")
        val appLoader = param.appClassLoader ?: return

        with(param) {
            // 策略 1：WifiNative 处于 bootclasspath 时直接挂载（适用于 Android 10/11 及部分传统架构系统）
            tryHookWifiClasses(appLoader)

            // 策略 2：拦截 SystemServiceManager.loadClassFromLoader（适用于 Android 12+ APEX service-wifi.jar 模块化加载）
            hookSystemServiceManager(appLoader)

            // 策略 3：拦截 ServiceManager.addService（捕获 Wi-Fi Binder 服务向系统注册时的特定 ClassLoader）
            hookServiceManager(appLoader)
        }

        // 注册动态 IPC 广播接收器以响应 UI 界面的零延迟切换
        registerApplyReceiver(param)

        // 启动守护线程执行延迟兜底初始化，确保在极端加载时序下补救未就绪的上下文与服务
        Thread {
            try {
                Thread.sleep(4000)
            } catch (_: InterruptedException) {
            }
            registerApplyReceiver(null)
            if (nativeInstance == null) {
                resolveWifiClassLoader()?.let {
                    with(param) { tryHookWifiClasses(it) }
                }
            }
        }.apply { isDaemon = true }.start()

        // 通过 YukiHookDataChannel 注册跨进程实时请求与配置变更监听
        runCatching {
            param.dataChannel.with {
                wait<String>("mac_request") { _ ->
                    val mac = currentSystemMac()
                    if (mac.isNotEmpty()) {
                        put("mac_result", mac)
                        YLog.debug("Replied system MAC $mac via data channel", tag = TAG)
                    }
                }
                wait<Boolean>(ACTION_CONFIG_CHANGED) { _ ->
                    isHookActive = modulePrefs.getBoolean("hookActive", true)
                    isApMacOverride = modulePrefs.getBoolean("apMacOverride", false)
                    isForceRandomization = modulePrefs.getBoolean("forceShowMacRandomization", true)
                    currentCustomMac = modulePrefs.getString("customMac", "")
                    YLog.debug("Config updated via dataChannel: hookActive=$isHookActive, apOverride=$isApMacOverride", tag = TAG)
                    applyMacDirectly()
                }
            }
        }.onFailure {
            YLog.debug("Data channel unavailable: $it", tag = TAG)
        }
        YLog.debug("WifiServiceHooker installed", tag = TAG)
    }

    /**
     * 拦截 [android.os.ServiceManager.addService]，在 `"wifi"` 服务注册时提取其独立的 [ClassLoader]。
     *
     * @param loader 系统基础 ClassLoader。
     */
    private fun PackageParam.hookServiceManager(loader: ClassLoader?) {
        val sm = "android.os.ServiceManager".toClassOrNull(loader) ?: return
        sm.declaredMethods.filter { it.name == "addService" }.forEach { method ->
            method.hook {
                before {
                    val name = args(0).cast<String>()
                    if (name == "wifi" || name?.contains("wifi", ignoreCase = true) == true) {
                        val binder = args(1).cast<Any>() ?: return@before
                        val cl = binder.javaClass.classLoader
                        if (cl != null) {
                            YLog.debug("ServiceManager.addService($name) captured, cl=$cl", tag = TAG)
                            tryHookWifiClasses(cl)
                        }
                    }
                }
            }?.ignoredHookingFailure()
        }
    }

    /**
     * 通过反射调用 `ServiceManager.getService("wifi")` 动态定位 Wi-Fi 服务的 ClassLoader。
     *
     * @return Wi-Fi 核心服务所在的 [ClassLoader] 实例；若服务尚未就绪则返回 `null`。
     */
    private fun resolveWifiClassLoader(): ClassLoader? {
        return runCatching {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "wifi")
            binder?.javaClass?.classLoader
        }.getOrNull()
    }

    /**
     * 探测并获取设备的物理出厂/真实系统 MAC 地址。
     *
     * 采用多级降级回退策略（Fallback Pipeline）：
     * 1. **HAL 原生反射**：优先调用 `WifiNative` / `WifiVendorHal` 的 `getStaFactoryMacAddress` 或 `getFactoryMacAddress`；
     * 2. **三星专有 EFS 存储**：读取 `/efs/wifi/.mac.info`、`/sec_efs/wifi/.mac.info` 或 `/data/vendor/mac_addr`；
     * 3. **高通芯片校准文件**：解析 `/mnt/vendor/persist/qca6390/wlan_mac.bin`；
     * 4. **最近捕获的系统广播 MAC**：回退至上次系统上报值；
     * 5. **Linux 内核 Sysfs 节点**：读取 `/sys/class/net/wlan0/address` 等；
     * 6. **Android 系统属性**：检索 `ro.boot.wifimacaddr`、`persist.vendor.wifi.mac` 等。
     *
     * @return 格式化的大写单播 MAC 字符串；若未能探测到则返回空字符串 `""`。
     */
    private fun currentSystemMac(): String {
        val native = nativeInstance
        val iface = lastIface ?: "wlan0"

        if (native != null) {
            val factoryCandidates = listOf(
                "getStaFactoryMacAddress" to arrayOf<Class<*>>(String::class.java),
                "getStaFactoryMacAddress" to emptyArray<Class<*>>(),
                "getFactoryMacAddress" to arrayOf<Class<*>>(String::class.java),
                "getFactoryMacAddress" to emptyArray<Class<*>>()
            )
            for ((methodName, params) in factoryCandidates) {
                runCatching {
                    val m = native.javaClass.getDeclaredMethod(methodName, *params)
                    m.isAccessible = true
                    val args = if (params.isEmpty()) emptyArray<Any?>() else arrayOf<Any?>(iface)
                    val res = m.invoke(native, *args)
                    val raw = when (res) {
                        is MacAddress -> res.toString()
                        is String -> res
                        is ByteArray -> res.joinToString(":") { "%02X".format(it) }
                        else -> res?.toString()
                    }
                    raw?.let { MacUtils.extractMac(it) }
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                    return it
                }
            }
        }

        // 三星专有 EFS 分区出厂 MAC 节点探测
        val samsungEfsPaths = listOf(
            "/efs/wifi/.mac.info",
            "/sec_efs/wifi/.mac.info",
            "/efs/wifi/.mac.cob",
            "/data/vendor/mac_addr"
        )
        for (path in samsungEfsPaths) {
            runCatching {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    MacUtils.extractMac(f.readText())?.let { return it }
                }
            }
        }

        // 高通芯片底层 Persist 校验文件读取
        runCatching {
            val f = File("/mnt/vendor/persist/qca6390/wlan_mac.bin")
            if (f.exists() && f.canRead()) {
                MacUtils.extractMac(f.readText())?.let { return it }
            }
        }

        lastBroadcastMac?.takeIf { it.isNotEmpty() }?.let { return it }

        if (native != null) {
            runCatching {
                val m = runCatching {
                    native.javaClass.getDeclaredMethod("getStaMacAddress", String::class.java)
                }.getOrNull() ?: runCatching {
                    native.javaClass.getDeclaredMethod("getMacAddress", String::class.java)
                }.getOrNull()
                m?.let {
                    it.isAccessible = true
                    val res = it.invoke(native, iface)
                    val raw = when (res) {
                        is MacAddress -> res.toString()
                        is String -> res
                        else -> res?.toString()
                    }
                    raw?.let { str -> MacUtils.extractMac(str) }
                }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        // Linux 内核 sysfs 虚拟文件系统节点
        val sysPaths = listOf(
            "/sys/class/net/wlan0/address",
            "/sys/class/net/wlan1/address",
            "/sys/class/net/ap0/address",
            "/sys/class/net/softap0/address"
        )
        for (p in sysPaths) {
            runCatching {
                val f = File(p)
                if (f.exists() && f.canRead()) {
                    MacUtils.extractMac(f.readText())?.let { return it }
                }
            }
        }

        // Android 基础属性与 OEM 引导属性检索
        val sysProps = listOf(
            "ro.boot.wifimacaddr",
            "persist.vendor.wifi.mac",
            "ro.ril.oem.wifi.mac",
            "ro.wifi.mac"
        )
        for (prop in sysProps) {
            runCatching {
                val spClass = Class.forName("android.os.SystemProperties")
                val getMethod = spClass.getMethod("get", String::class.java)
                val v = getMethod.invoke(null, prop) as? String
                v?.let { MacUtils.extractMac(it) }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }

        return ""
    }

    /**
     * 拦截 `SystemServiceManager.loadClassFromLoader`，捕获系统服务动态类加载。
     *
     * @param loader 系统基础 ClassLoader。
     */
    private fun PackageParam.hookSystemServiceManager(loader: ClassLoader?) {
        val ssm = "com.android.server.SystemServiceManager".toClassOrNull(loader) ?: return
        val method = runCatching {
            ssm.getDeclaredMethod("loadClassFromLoader", String::class.java, ClassLoader::class.java)
        }.getOrNull() ?: return
        method.hook {
            after {
                val className = args(0).cast<String>().orEmpty()
                if (className.contains("WifiService", ignoreCase = true) ||
                    className.startsWith("com.android.server.wifi.") ||
                    className.contains("SemWifi", ignoreCase = true) ||
                    className.contains("wifi", ignoreCase = true)
                ) {
                    val cl = args(1).cast<ClassLoader>()
                    if (cl != null) {
                        YLog.debug("Wi-Fi service class loaded ($className), installing hooks", tag = TAG)
                        tryHookWifiClasses(cl)
                    }
                }
            }
        }?.ignoredHookingFailure()
    }

    /**
     * 遍历并尝试在指定类加载器中挂载所有 Wi-Fi Native 与 HAL 目标类。
     *
     * @param loader 待检索的目标 [ClassLoader]。
     */
    private fun PackageParam.tryHookWifiClasses(loader: ClassLoader?) {
        for (target in TARGET_WIFI_NATIVE_CLASSES) {
            val clazz = target.toClassOrNull(loader) ?: continue
            hookWifiNativeClass(clazz)
        }
        for (target in TARGET_WIFI_HAL_CLASSES) {
            val clazz = target.toClassOrNull(loader) ?: continue
            hookWifiHalClass(clazz)
        }
    }

    /**
     * 装配 `WifiNative` 类相关的构造器缓存、模式设置与 MAC 修改钩子。
     *
     * @param clazz 解析出的目标 `WifiNative` 类。
     */
    private fun PackageParam.hookWifiNativeClass(clazz: Class<*>) {
        YLog.debug("Found WifiNative class: ${clazz.name}", tag = TAG)

        // 缓存单例实例：拦截所有构造器调用
        clazz.declaredConstructors.forEach { ctor ->
            ctor.hook {
                after {
                    instanceOrNull?.let { nativeInstance = it }
                    YLog.debug("WifiNative instance cached from ${clazz.name}", tag = TAG)
                }
            }?.ignoredHookingFailure()
        }

        // 监听 STA 客户端网卡初始化方法，捕获实时接口名称（如 "wlan0"）
        val clientMethods = listOf("setupForClientMode", "setupInterfaceForClientMode")
        for (name in clientMethods) {
            clazz.declaredMethods.filter { it.name == name }.forEach { method ->
                method.hook {
                    after {
                        if (args.isNotEmpty()) {
                            args(0).cast<String>()?.let {
                                lastIface = it
                                YLog.debug("Captured STA iface: $it", tag = TAG)
                            }
                        }
                    }
                }?.ignoredHookingFailure()
            }
        }

        // 监听 AP 移动热点网卡初始化方法，捕获实时热点接口名称（如 "ap0", "softap0"）
        val apMethods = listOf("setupForSoftApMode", "setupInterfaceForSoftApMode")
        for (name in apMethods) {
            clazz.declaredMethods.filter { it.name == name }.forEach { method ->
                method.hook {
                    after {
                        if (args.isNotEmpty()) {
                            args(0).cast<String>()?.let {
                                lastApIface = it
                                YLog.debug("Captured AP iface: $it", tag = TAG)
                            }
                        }
                    }
                }?.ignoredHookingFailure()
            }
        }

        hookMacMethodsOnClass(clazz)
    }

    /**
     * 装配 `WifiVendorHal` 类相关的 MAC 覆写钩子。
     *
     * @param clazz 解析出的目标 `WifiVendorHal` 类。
     */
    private fun PackageParam.hookWifiHalClass(clazz: Class<*>) {
        YLog.debug("Found WifiHal class: ${clazz.name}", tag = TAG)
        hookMacMethodsOnClass(clazz)
    }

    /**
     * 在目标类上挂载所有已知的 MAC 地址设置方法重载。
     *
     * 兼容方法名：`setStaMacAddress`, `setApMacAddress`, `setMacAddress`, `setWifiApMacAddress`。
     * 兼容参数签名：`(String, MacAddress)` 与 `(String, String)`。
     *
     * @param clazz 待挂载的目标类。
     */
    private fun PackageParam.hookMacMethodsOnClass(clazz: Class<*>) {
        val targetNames = setOf("setStaMacAddress", "setApMacAddress", "setMacAddress", "setWifiApMacAddress")
        clazz.declaredMethods.filter { it.name in targetNames }.forEach { method ->
            val pTypes = method.parameterTypes
            if (pTypes.size == 2 && pTypes[0] == String::class.java) {
                if (pTypes[1] == MacAddress::class.java) {
                    method.hook {
                        before { handleMacCall(this, method.name) }
                    }?.ignoredHookingFailure()
                    YLog.debug("Hooked ${clazz.simpleName}.${method.name}(String, MacAddress)", tag = TAG)
                } else if (pTypes[1] == String::class.java) {
                    method.hook {
                        before { handleMacCallString(this, method.name) }
                    }?.ignoredHookingFailure()
                    YLog.debug("Hooked ${clazz.simpleName}.${method.name}(String, String)", tag = TAG)
                }
            }
        }
    }

    /**
     * 判定指定的网络接口名称是否属于移动热点（AP / SoftAP / P2P）接口。
     *
     * @param iface 待检测的网卡名称（例如 `"wlan0"`, `"ap0"`, `"softap0"`, `"swlan0"`, `"wlan1"`）。
     * @return 若判定为 AP/热点 接口则返回 `true`。
     */
    fun isApInterface(iface: String?): Boolean {
        if (iface == null) return false
        val lower = iface.lowercase()
        return lower.startsWith("ap") ||
                lower.startsWith("softap") ||
                lower.startsWith("swlan") ||
                lower.startsWith("p2p") ||
                lower.contains("ap") ||
                lower.startsWith("wigig") ||
                (lower.startsWith("wlan") && !lower.equals("wlan0", ignoreCase = true))
    }

    /**
     * 动态探测并返回当前系统中处于激活运行状态的 AP 热点接口名称。
     *
     * @return 活跃的热点接口名（如 `"ap0"`）；若未探测到则返回缓存值或默认兜底名 `"ap0"`。
     */
    fun getActiveApInterface(): String? {
        lastApIface?.let { if (isInterfaceUp(it)) return it }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                val name = intf.name
                if (isApInterface(name)) {
                    lastApIface = name
                    return name
                }
            }
        }
        return lastApIface ?: "ap0"
    }

    /**
     * 查询指定网络接口是否处于 UP 状态。
     *
     * @param iface 网络接口名。
     * @return 若接口处于运行状态则返回 `true`。
     */
    private fun isInterfaceUp(iface: String): Boolean {
        return runCatching {
            val intf = NetworkInterface.getByName(iface)
            intf?.isUp == true
        }.getOrDefault(true)
    }

    /**
     * 规范化并校验适用于移动热点（AP 模式）的 MAC 地址。
     *
     * 依据 IEEE 802.11 协议标准与各厂商 Wi-Fi HAL 规范：
     * AP 热点模式下的 MAC 地址首字节必须满足：
     * - Bit 0 (I/G 位) = 0（Individual，单播地址）；
     * - Bit 1 (U/L 位) = 1（Locally Administered，本地管理地址）。
     * 若不满足上述位特征，底层驱动固件（如 hostapd）在热点启动时会抛出错误并拒绝开启热点。
     *
     * @param mac 原始 MAC 字符串。
     * @return 经过位运算修正后的安全 AP MAC 字符串。
     */
    fun ensureValidApMac(mac: String): String {
        val parts = mac.split(":")
        if (parts.size != 6) return mac
        val first = parts[0].toIntOrNull(16) ?: return mac
        val validFirst = (first and 0xFE) or 0x02
        val formattedFirst = "%02X".format(validFirst)
        return (listOf(formattedFirst) + parts.drop(1)).joinToString(":")
    }

    /**
     * 处理 `(String, MacAddress)` 签名的 MAC 设置拦截调用。
     *
     * @param p YukiHook 拦截上下文参数。
     * @param methodName 被拦截的原方法名。
     */
    private fun handleMacCall(p: HookParam, methodName: String) {
        val iface = p.args(0).cast<String>()
        val isAp = methodName.contains("Ap", ignoreCase = true) || isApInterface(iface)
        YLog.debug("$methodName called on $iface (isAp=$isAp, active=$isHookActive)", tag = TAG)

        if (!isAp) {
            lastIface = iface
        } else {
            lastApIface = iface
        }
        nativeInstance = p.instanceOrNull

        registerApplyReceiver(null)

        // 仅在 STA 客户端接口上将探测到的硬件 MAC 广播给模块应用展示
        if (!isAp) {
            p.args(1).cast<MacAddress>()?.let { broadcastMac(it) }
        }

        if (!isHookActive) {
            YLog.debug("hookActive is off, letting system default pass through", tag = TAG)
            return
        }

        if (iface == null) return

        // 若当前为 AP 接口且未开启热点覆写开关，放行系统默认生成的随机 MAC
        if (isAp && !isApMacOverride) {
            YLog.debug("Skipping AP MAC replacement on $iface because isApMacOverride is false", tag = TAG)
            return
        }

        val targetMac = currentCustomMac.ifEmpty { modulePrefs.getString("customMac", "") }
        if (targetMac.isNotEmpty()) {
            val macToSet = if (isAp) ensureValidApMac(targetMac) else targetMac
            runCatching {
                p.args(1).set(MacAddress.fromString(macToSet))
                YLog.debug("Replaced MAC with $macToSet on $iface ($methodName)", tag = TAG)
            }.onFailure {
                YLog.error("Failed to parse custom MAC $macToSet: $it", tag = TAG)
            }
        }
    }

    /**
     * 处理 `(String, String)` 签名的 MAC 设置拦截调用。
     *
     * @param p YukiHook 拦截上下文参数。
     * @param methodName 被拦截的原方法名。
     */
    private fun handleMacCallString(p: HookParam, methodName: String) {
        val iface = p.args(0).cast<String>()
        val isAp = methodName.contains("Ap", ignoreCase = true) || isApInterface(iface)
        YLog.debug("$methodName(String, String) called on $iface (isAp=$isAp, active=$isHookActive)", tag = TAG)

        if (!isAp) {
            lastIface = iface
        } else {
            lastApIface = iface
        }
        nativeInstance = p.instanceOrNull
        registerApplyReceiver(null)

        val rawMac = p.args(1).cast<String>()
        if (!rawMac.isNullOrEmpty() && !isAp) {
            MacUtils.extractMac(rawMac)?.let {
                runCatching { broadcastMac(MacAddress.fromString(it)) }
            }
        }

        if (!isHookActive) {
            YLog.debug("hookActive is off, skipping String replacement", tag = TAG)
            return
        }

        if (iface == null) return

        if (isAp && !isApMacOverride) {
            YLog.debug("Skipping AP MAC (String) replacement on $iface because isApMacOverride is false", tag = TAG)
            return
        }

        val targetMac = currentCustomMac.ifEmpty { modulePrefs.getString("customMac", "") }
        if (targetMac.isNotEmpty()) {
            val macToSet = if (isAp) ensureValidApMac(targetMac) else targetMac
            p.args(1).set(macToSet)
            YLog.debug("Replaced MAC with $macToSet on $iface ($methodName) [String]", tag = TAG)
        }
    }

    /**
     * 注册系统级动态广播接收器以监听应用发出的即时生效指令。
     *
     * @param param YukiHookAPI 注入的 [PackageParam]（可选）。
     */
    private fun registerApplyReceiver(param: PackageParam?) {
        if (applyReceiverRegistered) return
        val ctx = runCatching { param?.systemContext }.getOrNull() ?: getSystemContext() ?: return
        runCatching {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        ACTION_APPLY_MAC -> {
                            if (intent.hasExtra(EXTRA_HOOK_ACTIVE)) {
                                isHookActive = intent.getBooleanExtra(EXTRA_HOOK_ACTIVE, true)
                            }
                            val mac = intent.getStringExtra(EXTRA_MAC)
                            applyMacDirectly(intentMac = mac)
                        }
                        ACTION_APPLY_AP_MAC -> {
                            if (intent.hasExtra(EXTRA_AP_MAC_OVERRIDE)) {
                                isApMacOverride = intent.getBooleanExtra(EXTRA_AP_MAC_OVERRIDE, false)
                            }
                            val mac = intent.getStringExtra(EXTRA_MAC)
                            applyMacDirectly(intentMac = mac, apOnly = true)
                        }
                        ACTION_CONFIG_CHANGED -> {
                            if (intent.hasExtra(EXTRA_HOOK_ACTIVE)) {
                                isHookActive = intent.getBooleanExtra(EXTRA_HOOK_ACTIVE, true)
                            }
                            if (intent.hasExtra(EXTRA_AP_MAC_OVERRIDE)) {
                                isApMacOverride = intent.getBooleanExtra(EXTRA_AP_MAC_OVERRIDE, false)
                            }
                            if (intent.hasExtra(EXTRA_FORCE_RANDOMIZATION)) {
                                isForceRandomization = intent.getBooleanExtra(EXTRA_FORCE_RANDOMIZATION, true)
                            }
                            if (intent.hasExtra(EXTRA_CUSTOM_MAC)) {
                                currentCustomMac = intent.getStringExtra(EXTRA_CUSTOM_MAC) ?: ""
                            }
                            YLog.debug("Config changed via broadcast: active=$isHookActive, apOverride=$isApMacOverride, forceRand=$isForceRandomization, customMac=$currentCustomMac", tag = TAG)
                            applyMacDirectly()
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(ACTION_APPLY_MAC)
                addAction(ACTION_APPLY_AP_MAC)
                addAction(ACTION_CONFIG_CHANGED)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                ctx,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
            applyReceiverRegistered = true
            YLog.debug("Apply MAC & Config receiver registered successfully", tag = TAG)
        }.onFailure {
            YLog.debug("Apply receiver not ready yet: $it, will retry later", tag = TAG)
        }
    }

    /**
     * 实时直接将目标 MAC 写入底层 Wi-Fi / AP HAL 硬件抽象层与活跃网络接口。
     *
     * 业务行为分支：
     * - **主开关开启 ([isHookActive] = true)**：将目标自定义 MAC 应用于 STA 接口；若开启 AP 覆写，同时合规化并应用于 AP 接口。
     * - **主开关关闭 ([isHookActive] = false)**：立即将探测到的出厂/系统物理 MAC 还原写入 STA 与 AP 接口，实现平滑复原。
     *
     * @param intentMac 广播传递的目标 MAC 字符串（可选，优先于内存缓存）。
     * @param apOnly 是否仅针对 AP 移动热点接口执行定向更新。
     */
    private fun applyMacDirectly(intentMac: String? = null, apOnly: Boolean = false) {
        var native = nativeInstance
        if (native == null) {
            val resolved = resolveWifiClassLoader()
            if (resolved != null) {
                for (target in TARGET_WIFI_NATIVE_CLASSES) {
                    val clazz = target.toClassOrNull(resolved) ?: continue
                    runCatching {
                        val field = clazz.declaredFields.firstOrNull { it.type == clazz }
                        if (field != null) {
                            field.isAccessible = true
                            nativeInstance = field.get(null)
                        }
                    }
                    if (nativeInstance != null) break
                }
            }
            native = nativeInstance
        }

        if (native == null) {
            YLog.warn("WifiNative instance not cached yet, scheduling retry", tag = TAG)
            retryApplyMac(intentMac, apOnly)
            return
        }

        val customMac = currentCustomMac.ifEmpty { modulePrefs.getString("customMac", "") }
        val factoryMac = currentSystemMac()

        // 1. STA 客户端网卡接口处理
        if (!apOnly) {
            val staIface = lastIface ?: "wlan0"
            val staTarget = if (isHookActive) {
                intentMac?.takeIf { it.isNotEmpty() } ?: customMac
            } else {
                factoryMac
            }
            if (staTarget.isNotEmpty()) {
                invokeNativeSetMac(native, staIface, staTarget, isAp = false)
            }
        }

        // 2. AP 移动热点网卡接口处理
        val apIface = getActiveApInterface()
        if (apIface != null) {
            val apTarget = if (isHookActive && isApMacOverride) {
                val base = intentMac?.takeIf { it.isNotEmpty() } ?: customMac
                if (base.isNotEmpty()) ensureValidApMac(base) else ""
            } else {
                factoryMac
            }
            if (apTarget.isNotEmpty()) {
                invokeNativeSetMac(native, apIface, apTarget, isAp = true)
            }
        }
    }

    /**
     * 通过反射直接调用 `WifiNative` 实例上的底层 MAC 写入方法。
     *
     * @param native `WifiNative` 目标实例。
     * @param iface 目标接口名称（如 `"wlan0"`, `"ap0"`）。
     * @param macStr 待写入的规范化 MAC 地址字符串。
     * @param isAp 是否为 AP 热点接口操作。
     */
    private fun invokeNativeSetMac(native: Any, iface: String, macStr: String, isAp: Boolean) {
        runCatching {
            val methodNames = if (isAp) {
                listOf("setApMacAddress", "setWifiApMacAddress", "setMacAddress")
            } else {
                listOf("setStaMacAddress", "setMacAddress")
            }
            for (mName in methodNames) {
                // 优先匹配 (String, MacAddress) 签名
                val m = runCatching {
                    native.javaClass.getDeclaredMethod(mName, String::class.java, MacAddress::class.java)
                }.getOrNull()
                if (m != null) {
                    m.isAccessible = true
                    val res = m.invoke(native, iface, MacAddress.fromString(macStr))
                    YLog.debug("Directly applied ${if (isAp) "AP" else "STA"} MAC $macStr on $iface via $mName(String, MacAddress), result=$res", tag = TAG)
                    return
                }
                // 兼容匹配 (String, String) 签名
                val mStr = runCatching {
                    native.javaClass.getDeclaredMethod(mName, String::class.java, String::class.java)
                }.getOrNull()
                if (mStr != null) {
                    mStr.isAccessible = true
                    val res = mStr.invoke(native, iface, macStr)
                    YLog.debug("Directly applied ${if (isAp) "AP" else "STA"} MAC $macStr on $iface via $mName(String, String), result=$res", tag = TAG)
                    return
                }
            }
            YLog.warn("No compatible setter method found on ${native.javaClass.name} for $iface", tag = TAG)
        }.onFailure {
            YLog.error("invokeNativeSetMac error for $iface ($macStr): $it", tag = TAG)
        }
    }

    /**
     * 在 `WifiNative` 实例未就绪时启动后台守护线程进行分阶段轮询重试。
     *
     * @param intentMac 待应用的 MAC 字符串。
     * @param apOnly 是否仅针对 AP 热点。
     */
    private fun retryApplyMac(intentMac: String?, apOnly: Boolean = false) {
        Thread {
            repeat(8) {
                try {
                    Thread.sleep(800)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (nativeInstance != null) {
                    applyMacDirectly(intentMac, apOnly)
                    return@Thread
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /**
     * 将系统探测到的真实 MAC 地址封装为广播向模块应用分发。
     *
     * @param mac 捕获到的 [MacAddress] 实例。
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
     * 惰性获取并缓存系统级 [Context]（通过反射 `ActivityThread.currentApplication`）。
     *
     * @return 宿主进程内的系统 Context 实例；若尚未初始化则返回 `null`。
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
