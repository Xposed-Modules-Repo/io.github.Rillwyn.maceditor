package io.github.Rillwyn.maceditor.hookers

import android.content.res.Resources
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import io.github.Rillwyn.maceditor.TAG

/**
 * Android 系统资源布尔值拦截钩子套件（`loadSystem` 作用域）。
 *
 * 核心架构机制：
 * 1. **普通方法 Hook 替代 XResources**：通过拦截 `Resources.getBoolean(int)` 而非使用已被现代 Xposed（如 LSPosed）弃用的 XResources 资源替换，
 *    确保在各主流框架（Zygisk / Riru）下拥有 100% 的兼容性与稳定性。
 * 2. **动态强制开启硬件级随机化**：在系统服务请求以下 Wi-Fi 关键功能支持标志时，按资源名识别并动态返回 `true`：
 *    - `config_wifi_connected_mac_randomization_supported`（已连接 Wi-Fi 网络 MAC 随机化）
 *    - `config_wifi_p2p_mac_randomization_supported`（Wi-Fi Direct / P2P MAC 随机化）
 *    - `config_wifi_ap_mac_randomization_supported`（移动热点 AP 模式 MAC 随机化）
 * 3. **零重启即时联动**：读取 [WifiServiceHooker.isHookActive] 与 [WifiServiceHooker.isForceRandomization]，实现开关切换毫秒级生效。
 *
 * @sample
 * ```kotlin
 * // 在 HookEntry 的 loadSystem 回调中挂载
 * WifiConfigHooker.hook(this)
 * ```
 */
object WifiConfigHooker {

    /** 目标需要强制重写为 `true` 的系统级 Boolean 资源名称集合。 */
    private val TARGET_KEYS = setOf(
        "config_wifi_connected_mac_randomization_supported",
        "config_wifi_p2p_mac_randomization_supported",
        "config_wifi_ap_mac_randomization_supported"
    )

    /** 模块共享偏好存储文件名。 */
    private const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    /**
     * 向系统框架层装配 `Resources.getBoolean(int)` 拦截钩子。
     *
     * @param param YukiHookAPI 注入的 [PackageParam] 系统包环境参数。
     */
    fun hook(param: PackageParam) {
        with(param) {
            val method = runCatching {
                Resources::class.java.getDeclaredMethod("getBoolean", Int::class.javaPrimitiveType)
            }.getOrNull() ?: return
            method.hook {
                after {
                    if (!WifiServiceHooker.isHookActive) return@after
                    if (!WifiServiceHooker.isForceRandomization) return@after
                    val res = instanceOrNull as? Resources ?: return@after
                    val id = args(0).int()
                    val name = runCatching { res.getResourceEntryName(id) }.getOrNull() ?: return@after
                    if (name in TARGET_KEYS) {
                        result = true
                        YLog.debug("Forced $name to true", tag = TAG)
                    }
                }
            }?.ignoredHookingFailure()
        }
    }
}
