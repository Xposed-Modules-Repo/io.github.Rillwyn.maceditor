package io.github.Rillwyn.maceditor

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.dataChannel
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

/**
 * 模块宿主应用程序入口点。
 *
 * 继承自 YukiHookAPI 的 [ModuleApplication]，负责初始化模块自身进程的数据通道环境与模块状态监听。
 * 在应用进程冷启动期间，异步派发轻量级守护线程，通过 YukiHook 数据通道向 `system_server` 宿主拉取
 * 底层真实出厂 Wi-Fi MAC 地址，并持久化到本地独立缓存，使主界面打开时能够零延迟呈现物理出厂 MAC。
 *
 * @sample
 * ```xml
 * <!-- AndroidManifest.xml 声明 -->
 * <application
 *     android:name=".App"
 *     android:label="@string/app_name" ...>
 * </application>
 * ```
 */
class App : ModuleApplication() {

    /**
     * 应用程序主进程创建时的生命周期回调。
     *
     * 调用父类实现以完成 Xposed 模块框架运行时的绑定，并启动后台异步出厂 MAC 预加载流程。
     */
    override fun onCreate() {
        super.onCreate()
        pullSystemMac()
    }

    /**
     * 异步向系统服务进程拉取硬件出厂 MAC 地址。
     *
     * 内部流程：
     * 1. 延迟等待 [ModuleApplication] 内部的数据通道接收器完全就绪。
     * 2. 向目标频道 `"android"`（对应 `system_server` 进程）发送 `"mac_request"` 探测信号。
     * 3. 注册 `"mac_result"` 异步监听，在收到回包后将真实出厂 MAC 规范化持久化至私有 SharedPreferences。
     * 4. 捕获并静默处理所有连接异常（如模块未激活或框架不支持 DataChannel 场景）。
     */
    private fun pullSystemMac() {
        Thread {
            try {
                // 等待 ModuleApplication 内置的 dataChannel 广播监听器彻底完成生命周期挂载
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
            }.onFailure {
                // 当处于未激活状态或宿主进程通道未响应时静默容错，避免阻塞应用正常启动
            }
        }.apply { isDaemon = true }.start()
    }
}
