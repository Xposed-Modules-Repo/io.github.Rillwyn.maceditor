package io.github.Rillwyn.maceditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 出厂/系统 MAC 地址更新静态广播接收器。
 *
 * 声明于 `AndroidManifest.xml` 中，负责在系统后台静默接收来自 `system_server` 钩子层派发的 [ACTION_MAC_DETECTED] 广播，
 * 并将捕获到的硬件 MAC 字符串自动大写化后持久化写入本地私有偏好文件 [PREFS_NAME]，为 UI 界面提供即时可用的出厂 MAC 缓存。
 *
 * @sample
 * ```kotlin
 * val intent = Intent(MacBroadcastReceiver.ACTION_MAC_DETECTED).apply {
 *     putExtra(MacBroadcastReceiver.EXTRA_MAC, "02:00:00:00:00:01")
 * }
 * context.sendBroadcast(intent)
 * ```
 */
class MacBroadcastReceiver : BroadcastReceiver() {

    /**
     * 接收并处理 MAC 地址检测广播。
     *
     * @param context 应用程序或系统广播上下文。
     * @param intent 携带 MAC 字符串附加数据的 Intent 实例。
     */
    override fun onReceive(context: Context, intent: Intent) {
        val mac = intent.getStringExtra(EXTRA_MAC) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("deviceMac", mac.uppercase()).apply()
    }

    companion object {
        /** 系统服务侧探测到出厂/当前硬件 MAC 地址时发送的广播 Action。 */
        const val ACTION_MAC_DETECTED = "${BuildConfig.APPLICATION_ID}.ACTION_MAC_DETECTED"

        /** 附加在 [ACTION_MAC_DETECTED] 意图中的 MAC 字符串 Extra 键名。 */
        const val EXTRA_MAC = "mac"

        /** 用于持久化存储本应用私有出厂 MAC 缓存的 SharedPreferences 表名。 */
        const val PREFS_NAME = "local_prefs"
    }
}
