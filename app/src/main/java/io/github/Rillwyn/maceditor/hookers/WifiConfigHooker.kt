package io.github.Rillwyn.maceditor.hookers

import android.content.res.Resources
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import io.github.Rillwyn.maceditor.TAG

/**
 * 系统资源 Hook 实现（loadSystem 作用域）。
 *
 * 在 system_server 中 Hook `Resources.getBoolean(int)`，每次调用时按资源名拦截，
 * 将以下系统 bool 资源强制返回 true（按用户开关决定），使系统认为
 * Wi-Fi / Wi-Fi Direct / 热点均支持 MAC 随机化：
 *
 * - config_wifi_connected_mac_randomization_supported
 * - config_wifi_p2p_mac_randomization_supported
 * - config_wifi_ap_mac_randomization_supported
 *
 * 采用普通方法 Hook（`Member.hook`），在 LSPosed（含 Zygisk 版）等框架上均可用，
 * 且开关切换即时生效（无需重启）。
 */
object WifiConfigHooker {

    private val TARGET_KEYS = setOf(
        "config_wifi_connected_mac_randomization_supported",
        "config_wifi_p2p_mac_randomization_supported",
        "config_wifi_ap_mac_randomization_supported"
    )

    /** 模块偏好文件名（与模块应用侧保持一致） */
    private const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    fun hook(param: PackageParam) {
        val prefs = param.prefs(PREFS_NAME)
        with(param) {
            val method = runCatching {
                Resources::class.java.getDeclaredMethod("getBoolean", Int::class.javaPrimitiveType)
            }.getOrNull() ?: return
            method.hook {
                after {
                    if (!prefs.getBoolean("forceShowMacRandomization", true)) return@after
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
