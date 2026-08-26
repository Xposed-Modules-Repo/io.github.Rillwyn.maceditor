package io.github.Rillwyn.maceditor

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

/**
 * 模块应用入口 Application。
 *
 * 继承 YukiHookAPI 的 [ModuleApplication]（自动注册数据通道与模块状态）。
 * 在应用进程启动（即模块处于激活环境）时，通过 [YukiHookDataChannel]
 * 主动向 system_server 拉取当前系统 MAC 并写入本地缓存，
 * 这样无论何时打开主界面，都能立即显示系统 MAC，无需等待 WiFi 广播。
 */
class App : ModuleApplication() {

    override fun onCreate() {
        super.onCreate()
        pullSystemMac()
    }

    /**
     * 主动拉取系统 MAC：等待数据通道注册完成后发送请求，
     * 收到回包写入本地偏好（deviceMac），供主界面直接读取。
     * 模块未激活 / 通道不可用时静默失败，不影响其它功能。
     */
    private fun pullSystemMac() {
        Thread {
            try {
                // 等待 dataChannel 广播接收器注册完成（ModuleApplication.onCreate 中完成）
                Thread.sleep(1500)
            } catch (_: InterruptedException) {
            }
            runCatching {
                dataChannel("android").with {
                    wait<String>("mac_result") { mac ->
                        val ctx = this@App
                        val macStr = mac.uppercase()
                        ctx.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString("deviceMac", macStr).apply()
                    }
                    put("mac_request", "true")
                }
            }.onFailure { /* 数据通道不可用（如模块未激活），忽略 */ }
        }.apply { isDaemon = true }.start()
    }
}
