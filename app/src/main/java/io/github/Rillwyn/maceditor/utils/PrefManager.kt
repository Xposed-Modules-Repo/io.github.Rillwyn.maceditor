package io.github.Rillwyn.maceditor.utils

import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import io.github.Rillwyn.maceditor.MacBroadcastReceiver
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker

/**
 * 模块跨进程配置持久化与实时同步管理器。
 *
 * 统一依托 YukiHookAPI 的 [YukiHookPrefsBridge] 机制构建：
 * - **应用侧**：拥有偏好设置的完全读写权限，所有修改实时持久化至磁盘。
 * - **系统侧 (`system_server`)**：通过只读共享文件句柄直接映射同一配置数据源。
 * - **即时生效体系**：配置变更时并发触发系统级 IPC 广播及 [dataChannel] 双通道通知，
 *   使 Wi-Fi HAL 与活跃网络接口在毫秒级内完成 MAC 刷新，无需重启手机或重启网络服务。
 *
 * @sample
 * ```kotlin
 * // 保存并立即应用新的自定义 MAC 地址
 * PrefManager.setCustomMac(context, "02:AA:BB:CC:DD:EE")
 * val isEnabled = PrefManager.isHookOn(context)
 * ```
 */
object PrefManager {

    /** 跨进程共享偏好存储文件名（与 system_server 端保持一致以保障兼容性）。 */
    const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    /**
     * 获取当前上下文绑定的 [YukiHookPrefsBridge] 实例。
     *
     * @param context 应用程序或组件上下文。
     * @return 具备世界可读权限的跨进程偏好桥接对象。
     */
    fun prefs(context: Context): YukiHookPrefsBridge = context.prefs(PREFS_NAME)

    /**
     * 获取 MAC 覆写主开关的当前激活状态。
     *
     * @param context 应用程序或组件上下文。
     * @return 若已启用 MAC 覆写则返回 `true`，默认值为 `true`。
     */
    fun isHookOn(context: Context): Boolean = prefs(context).getBoolean("hookActive", true)

    /**
     * 设置 MAC 覆写主开关状态并立即触发硬件/HAL 层级的地址切换。
     *
     * - 当设置为 `true` 且存在有效自定义 MAC 时：广播目标自定义地址至 `system_server` 立即生效。
     * - 当设置为 `false` 时：读取本地缓存的出厂/系统 MAC 并广播恢复，使设备网络接口回归原始出厂状态。
     *
     * @param context 应用程序上下文。
     * @param on 主开关目标状态（`true` 表示启用，`false` 表示关闭并还原出厂 MAC）。
     */
    fun setHookState(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("hookActive", on) }
        notifyConfigChanged(context)

        // 开关切换时立即更新实际生效的 MAC 到底层网络接口
        if (on) {
            val customMac = getCustomMac(context)
            if (customMac.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(WifiServiceHooker.ACTION_APPLY_MAC)
                        .putExtra(WifiServiceHooker.EXTRA_MAC, customMac)
                        .putExtra(WifiServiceHooker.EXTRA_HOOK_ACTIVE, true)
                )
            }
        } else {
            // 关闭 Hook 时，恢复设备出厂/系统 MAC
            val localPrefs = context.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val deviceMac = localPrefs.getString("deviceMac", null) ?: ""
            context.sendBroadcast(
                Intent(WifiServiceHooker.ACTION_APPLY_MAC)
                    .putExtra(WifiServiceHooker.EXTRA_MAC, deviceMac)
                    .putExtra(WifiServiceHooker.EXTRA_HOOK_ACTIVE, false)
            )
        }
    }

    /**
     * 获取用户设定的自定义 MAC 地址字符串。
     *
     * @param context 应用程序上下文。
     * @return 格式化的 MAC 地址字符串；若尚未配置则返回空字符串 `""`。
     */
    fun getCustomMac(context: Context): String = prefs(context).getString("customMac", "")

    /**
     * 持久化用户自定义 MAC 地址，并在主开关开启时实时向系统层分发应用指令。
     *
     * @param context 应用程序上下文。
     * @param mac 待保存的规范化 MAC 地址字符串（大写冒号分隔）。
     */
    fun setCustomMac(context: Context, mac: String) {
        prefs(context).edit { putString("customMac", mac) }
        notifyConfigChanged(context)
        if (isHookOn(context) && mac.isNotEmpty()) {
            context.sendBroadcast(
                Intent(WifiServiceHooker.ACTION_APPLY_MAC)
                    .putExtra(WifiServiceHooker.EXTRA_MAC, mac)
                    .putExtra(WifiServiceHooker.EXTRA_HOOK_ACTIVE, true)
            )
        }
    }

    /**
     * 查询是否强制启用系统的 MAC 随机化底层支持。
     *
     * @param context 应用程序上下文。
     * @return 若强制开启系统随机化则返回 `true`，默认值为 `true`。
     */
    fun isForceShowMacRandomization(context: Context): Boolean =
        prefs(context).getBoolean("forceShowMacRandomization", true)

    /**
     * 设置是否强制开启系统的 MAC 随机化底层支持。
     *
     * @param context 应用程序上下文。
     * @param on 目标开关状态。
     */
    fun setForceShowMacRandomization(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("forceShowMacRandomization", on) }
        notifyConfigChanged(context)
    }

    /**
     * 查询移动热点（AP 模式）MAC 覆写开关状态。
     *
     * @param context 应用程序上下文。
     * @return 若允许覆写 AP 接口 MAC 则返回 `true`，默认值为 `false`。
     */
    fun isApMacOverride(context: Context): Boolean = prefs(context).getBoolean("apMacOverride", false)

    /**
     * 设置移动热点（AP 模式）MAC 覆写开关并触发即时 AP HAL 同步。
     *
     * @param context 应用程序上下文。
     * @param on 目标开关状态（`true` 表示对热点启用覆写，`false` 表示热点使用系统默认随机 MAC）。
     */
    fun setApMacOverride(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("apMacOverride", on) }
        notifyConfigChanged(context)

        // 立即触发 AP MAC 广播，热点无需重启即时生效
        val customMac = getCustomMac(context)
        context.sendBroadcast(
            Intent(WifiServiceHooker.ACTION_APPLY_AP_MAC)
                .putExtra(WifiServiceHooker.EXTRA_AP_MAC_OVERRIDE, on)
                .putExtra(WifiServiceHooker.EXTRA_MAC, if (on) customMac else "")
        )
    }

    /**
     * 向 `system_server` 宿主进程广播全量配置变更通知。
     *
     * 采用双通道冗余机制保证实时性：
     * 1. **系统级动态 Broadcast**：分发至注册在系统上下文上的 [WifiServiceHooker] 接收器。
     * 2. **[YukiHookDataChannel] 管道**：直接向 `"android"` 系统频道推送键值对载荷。
     *
     * @param context 应用程序上下文。
     */
    fun notifyConfigChanged(context: Context) {
        val hookActive = isHookOn(context)
        val apOverride = isApMacOverride(context)
        val forceRand = isForceShowMacRandomization(context)
        val customMac = getCustomMac(context)

        // 1. 系统级动态广播分发
        runCatching {
            val intent = Intent(WifiServiceHooker.ACTION_CONFIG_CHANGED).apply {
                putExtra(WifiServiceHooker.EXTRA_HOOK_ACTIVE, hookActive)
                putExtra(WifiServiceHooker.EXTRA_AP_MAC_OVERRIDE, apOverride)
                putExtra(WifiServiceHooker.EXTRA_FORCE_RANDOMIZATION, forceRand)
                putExtra(WifiServiceHooker.EXTRA_CUSTOM_MAC, customMac)
            }
            context.sendBroadcast(intent)
        }

        // 2. DataChannel IPC 管道双保险分发
        runCatching {
            context.dataChannel("android").with {
                put(WifiServiceHooker.ACTION_CONFIG_CHANGED, true)
                put(WifiServiceHooker.EXTRA_HOOK_ACTIVE, hookActive)
                put(WifiServiceHooker.EXTRA_AP_MAC_OVERRIDE, apOverride)
                put(WifiServiceHooker.EXTRA_FORCE_RANDOMIZATION, forceRand)
                put(WifiServiceHooker.EXTRA_CUSTOM_MAC, customMac)
            }
        }
    }
}
