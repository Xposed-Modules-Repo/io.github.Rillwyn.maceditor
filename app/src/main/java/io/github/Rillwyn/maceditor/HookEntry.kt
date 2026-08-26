package io.github.Rillwyn.maceditor

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.Rillwyn.maceditor.hookers.WifiConfigHooker
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker

/** 全局日志标签 */
const val TAG = "MACEditor"

/**
 * 模块 Hook 入口（YukiHookAPI 1.3.2 标准入口）。
 *
 * - KSP 处理器会根据本类自动生成 `assets/xposed_init` 及入口代理类，
 *   因此不再需要手动维护 `META-INF/xposed/java_init.list`。
 * - `isUsingXposedModuleStatus = true`（默认）会让 LSPosed 自动向模块自身
 *   应用进程注入激活状态，应用内可通过 [YukiHookAPI.Status.isModuleActive]
 *   实时、准确地判断模块是否已激活（修复重启后误显示“未激活”的问题）。
 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    override fun onInit() {
        YukiHookAPI.configs {
            isDebug = BuildConfig.DEBUG
            // 强制模块 SharedPreferences 文件为 0664（world-readable），
            // 确保 system_server 内的 XSharedPreferences 无论何种框架模式都能读取。
            isEnableHookSharedPreferences = true
        }
    }

    override fun onHook() = encase {
        // 系统框架（system_server）：WifiNative / WifiVendorHal MAC 覆写
        // + Resources.getBoolean 拦截（强制开启 MAC 随机化支持位，普通方法 Hook，
        //   兼容 LSPosed 等不支持 XResources 资源替换的框架）
        loadSystem {
            WifiServiceHooker.hook(this)
            WifiConfigHooker.hook(this)
        }
    }
}
