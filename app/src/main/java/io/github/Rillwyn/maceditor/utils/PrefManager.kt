package io.github.Rillwyn.maceditor.utils

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

/**
 * 模块偏好设置管理。
 *
 * 统一使用 YukiHookAPI 的 [YukiHookPrefsBridge]：
 * - 模块应用进程内可读可写；
 * - system_server（宿主进程）内通过 XSharedPreferences 只读同一份数据，
 *   从而让 Hook 逻辑实时读取用户在应用里设置的 MAC 与开关。
 */
object PrefManager {

    /** 偏好文件名（与 system_server 侧保持一致，兼容历史数据） */
    const val PREFS_NAME = "io.github.Rillwyn.maceditor"

    fun prefs(context: Context): YukiHookPrefsBridge = context.prefs(PREFS_NAME)

    fun isHookOn(context: Context): Boolean = prefs(context).getBoolean("hookActive", true)

    fun setHookState(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("hookActive", on) }
    }

    fun getCustomMac(context: Context): String = prefs(context).getString("customMac", "")

    fun setCustomMac(context: Context, mac: String) {
        prefs(context).edit { putString("customMac", mac) }
    }

    fun isForceShowMacRandomization(context: Context): Boolean =
        prefs(context).getBoolean("forceShowMacRandomization", true)

    fun setForceShowMacRandomization(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("forceShowMacRandomization", on) }
    }

    fun isApMacOverride(context: Context): Boolean = prefs(context).getBoolean("apMacOverride", false)

    fun setApMacOverride(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean("apMacOverride", on) }
    }
}
