package io.github.Rillwyn.maceditor

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.Rillwyn.maceditor.hookers.WifiConfigHooker
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker

/** 模块全局统一日志过滤标签。 */
const val TAG = "MACEditor"

/**
 * Xposed 模块框架主初始化入口类。
 *
 * 基于 YukiHookAPI 1.3.2 规范构建，标注 [@InjectYukiHookWithXposed] 注解。
 * 由 KSP 编译器插件自动生成对应的 `assets/xposed_init` 入口声明与模块状态注入桥接逻辑。
 *
 * 架构职责：
 * 1. **环境初始化 ([onInit])**：配置调试日志输出级别与跨进程 SharedPreferences 文件的世界可读权限（0664）。
 * 2. **钩子装配 ([onHook])**：在系统级宿主进程（`system_server` / Android 框架层）中并行挂载：
 *    - [WifiServiceHooker]：拦截 Wi-Fi / AP 底层硬件 HAL MAC 地址设置与读取。
 *    - [WifiConfigHooker]：拦截系统资源布尔值，强制开启系统级 MAC 随机化支持标志位。
 *
 * @sample
 * ```kotlin
 * // 本类由 Xposed 框架加载器通过 assets/xposed_init 自动实例化，无需手动调用
 * ```
 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {

    /**
     * YukiHookAPI 框架全局参数配置回调。
     *
     * 启用 `isEnableHookSharedPreferences = true` 强制将偏好文件权限调整为 0664，
     * 确保 `system_server` 运行在隔离沙箱或多用户环境下仍可顺利通过 XSharedPreferences 读取配置。
     */
    override fun onInit() {
        YukiHookAPI.configs {
            isDebug = BuildConfig.DEBUG
            isEnableHookSharedPreferences = true
        }
    }

    /**
     * 模块目标宿主进程 Hook 注册与分发入口。
     *
     * 仅在 Android 系统服务宿主进程（`android` / `system_server`）触发时挂载业务 Hooker。
     */
    override fun onHook() = encase {
        loadSystem {
            WifiServiceHooker.hook(this)
            WifiConfigHooker.hook(this)
        }
    }
}
