# Changelog

This file documents all modifications made to the original [MAC Editor](https://github.com/jqssun/android-mac-editor) project by [jqssun](https://github.com/jqssun).

All changes are made under the terms of the original license (GPL-3.0), and all original copyright notices are retained.

---

## [0.0.10] - 2026-08-25

### Core Refactor
- **Fully migrated to YukiHookAPI 1.3.2**: The module entry and hook implementations were rebuilt on official YukiHookAPI mechanics, fixing the previous source code that referenced non-existent APIs (`YukiModule`, `Preferences.default`, `module.encounter`, `module.injectResource`, etc.) and could not compile at all.
  - New entry `HookEntry` uses `@InjectYukiHookWithXposed` + `IYukiHookXposedInit`; the KSP processor auto-generates `assets/xposed_init` and the module-status class, so the hand-maintained `META-INF/xposed` assets were removed.

### Fixed
- **“Module inactive” shown after reboot**: Activation state now uses `YukiHookAPI.Status.isModuleActive` (LSPosed injects the real activation state into the module's own process), instead of relying on the transient “system MAC broadcast” signal. The app shows the correct status immediately after reboot, no background-clearing needed.
- **First “Apply MAC Address” tap did nothing after reboot**: The module now hooks every `WifiNative` constructor and caches the instance as soon as the system creates it; `WifiVendorHal` overloads are also hooked for compatibility. The native instance is always available when the user taps “Apply MAC Address”.
- **Broken cross-process preferences**: Module app and `system_server` now share preferences through YukiHookAPI's `YukiHookPrefsBridge` (read/write inside the module app, XSharedPreferences read-only in the host process), replacing the old custom “prefs bridge + broadcast” scheme. `customMac`, `hookActive`, `apMacOverride` are shared in real time.
- **Preference file readability**: Enabled `isEnableHookSharedPreferences` to force the module preference file to mode 0664 (world-readable), so `system_server` can read it under any framework mode.

### Changed
- Build config: `compileSdk` raised to 37 (required by YukiHookAPI 1.3.2 dependencies); KSP (`ksp-xposed`) added; local `libs/api-82.jar` provides the XposedBridge compile-time API; the dead `maven.highcapable.me` repository was removed.
- **Resource hook now hooks `Resources.getBoolean`**: intercepts `getBoolean(int)` in `system_server` and returns true for the target resource names (plain method hook, compatible with frameworks like LSPosed that do not support XResources replacement; the toggle takes effect immediately, no reboot needed).
- **Hooks rewritten with `Member.hook`**: target members are resolved via reflection and hooked directly, avoiding the overload-resolution issue of the legacy finder style (`method { }.hook { }`) in some environments.
- **Double installation of hooks**: hooks are installed directly in `loadSystem` and again when the `WifiService` class is loaded (via `SystemServiceManager.loadClassFromLoader`), covering any loading timing.
- **Xposed assets & module declarations**: `module.prop` and `scope.list` restored under `src/main/resources/META-INF/xposed/` (packaged into the **APK root `META-INF/xposed/`**); the Manifest now carries the legacy Xposed module declarations (`MODULE_SETTINGS`, `xposedmodule`/`xposeddescription`/`xposedminversion`/`xposedscope`) so LSPosed recognizes the module reliably. The entry is still provided by the KSP-generated `assets/xposed_init`; `java_init.list` is deliberately **not** created — if present, LSPosed would switch to the libxposed loading path, which would make the module fail to load.
- **"System MAC" now shows the factory MAC**: Reflects `WifiVendorHal.getStaFactoryMacAddress(iface)` (ColorOS/OPPO method name; AOSP standard is `getFactoryMacAddress`, both are tried) to read the hardware factory MAC, instead of the randomized one. The app actively pulls it via `YukiHookDataChannel` on process start and caches it locally, so the UI shows it immediately.
- **One-tap "Apply MAC Address" works immediately**: The broadcast now carries the target MAC directly (no longer depends on cross-process prefs timing); if the `WifiNative` instance is not ready yet, it automatically retries with a delay.
- **Status display polish**: The status-card subtitle dynamically shows the MAC actually in use (custom MAC and system MAC on separate lines); "Active MAC" falls back to the system MAC when no custom MAC is set.

### Documentation
- Updated `README.md` and `README_CN.md`.
- Added `CODE_SUMMARY.md` code summary document.
- Bumped version to `0.0.10` in `app/build.gradle.kts`.

---

## [0.0.9] - 2026-08-24

### Fixed
- **Release build activation issue**: Corrected the package name and scope in `META-INF/xposed` files (`java_init.list`, `module.prop`, `scope.list`) to ensure LSPosed properly loads the module. The previous files still referenced the original author's package name (`io.github.jqssun.maceditor`) and used an invalid scope (`system` instead of `android`), preventing the module from being injected into the system server.

---

## [0.0.8] - 2026-08-24

### Added
- **Multi‑language UI**: Support for English and Chinese, switchable via the app menu. The UI refreshes instantly after selection.
- **AP MAC override toggle**: A dedicated switch to control whether the custom MAC address is applied to the mobile hotspot (AP) interface. **Default is OFF**, preventing hotspot startup failures on devices that reject MAC changes on `wlan2`.

### Changed
- **Hotspot compatibility fix**: Replaced method‑based detection with interface‑name‑based detection (`wlan2`) to accurately identify AP calls, ensuring the AP override switch works reliably.
- **Language storage optimization**: Language preference is now stored in local `SharedPreferences` instead of remote Xposed prefs, fixing the previous issue where language switching had no effect.

### Technical Details
- Modified `WifiServiceHooker.kt`: Uses `ifaceName` to determine if the call targets AP mode; checks `apMacOverride` preference before replacing MAC.
- Modified `MainActivity.kt`: Reads language setting in `attachBaseContext` from local prefs, uses `recreate()` to refresh UI.
- Added `values-zh/strings.xml` for Chinese strings.
- Updated `activity_main.xml`: Added AP MAC override card.
- Bumped version to `0.0.8` in `app/build.gradle.kts`.

### Documentation
- Updated `README.md` and added `README_CN.md` to describe new features and usage.
- Created `CHANGELOG.md` and `CHANGELOG_CN.md` to record all modifications.

---

## Original Releases (upstream)

The original project was based on [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz)'s `MacSposed` implementation, later improved and open‑sourced by [jqssun](https://github.com/jqssun).

Original features included:
- Hooking `setStaMacAddress` and `setApMacAddress`.
- Forcing MAC randomization resources.
- Basic UI for setting a custom MAC address.