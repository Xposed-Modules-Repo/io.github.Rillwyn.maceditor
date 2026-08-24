package io.github.Rillwyn.maceditor.utils

import android.content.SharedPreferences
import io.github.Rillwyn.maceditor.BuildConfig
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class PrefManager {
    companion object {
        private var prefs: SharedPreferences? = null
        private var loaded = false

        fun loadPrefs(onReady: (() -> Unit)? = null) {
            if (loaded) {
                if (XposedChecker.isEnabled()) onReady?.invoke()
                return
            }
            loaded = true
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    XposedChecker.flagAsEnabled()
                    prefs = service.getRemotePreferences(BuildConfig.APPLICATION_ID)
                    onReady?.invoke()
                }

                override fun onServiceDied(service: XposedService) {}
            })
        }

        fun isHookOn(): Boolean {
            return prefs?.getBoolean("hookActive", true) ?: true
        }

        fun setHookState(on: Boolean) {
            prefs?.edit()?.putBoolean("hookActive", on)?.apply()
        }

        fun getCustomMac(): String {
            return prefs?.getString("customMac", "") ?: ""
        }

        fun setCustomMac(mac: String) {
            prefs?.edit()?.putString("customMac", mac)?.apply()
        }

        fun isForceShowMacRandomization(): Boolean {
            return prefs?.getBoolean("forceShowMacRandomization", true) ?: true
        }

        fun setForceShowMacRandomization(on: Boolean) {
            prefs?.edit()?.putBoolean("forceShowMacRandomization", on)?.apply()
        }

        // New: AP MAC override
        fun isApMacOverride(): Boolean {
            return prefs?.getBoolean("apMacOverride", false) ?: false
        }

        fun setApMacOverride(on: Boolean) {
            prefs?.edit()?.putBoolean("apMacOverride", on)?.apply()
        }

        // New: Language preference (default empty means system default)
        fun getLanguage(): String {
            return prefs?.getString("language", "") ?: ""
        }

        fun setLanguage(lang: String) {
            prefs?.edit()?.putString("language", lang)?.apply()
        }
    }
}