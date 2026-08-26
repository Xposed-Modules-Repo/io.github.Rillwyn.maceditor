# MAC Editor for Android (Fork)

[![Stars](https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/stargazers)
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=LSPosed&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![GitHub](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=GitHub&logo=GitHub)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![build](https://img.shields.io/github/actions/workflow/status/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/apk.yml)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/Xposed-Modules-Repo/io.github.Rillwyn.maceditor?color=green)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/master/LICENSE)

> **Note**: This is a modified fork of the original [MAC Editor](https://github.com/jqssun/android-mac-editor) by [jqssun](https://github.com/jqssun). All credits go to the original author. This version adds several enhancements (see below).

**MAC Editor** is a free and open-source Xposed module that gives you granular control over the Wi-Fi MAC address on Android devices. It supports manual MAC override and enables native MAC randomization support exposed by Android on supported hardware regardless of the OEM's implementation.

You can use it to:
- Customize MAC behavior for privacy.
- Override the randomized MAC with a fixed value.
- Force-enable MAC randomization on devices where the vendor disabled it.
- **Optionally control whether the mobile hotspot (AP) interface uses the custom MAC** – disable this if your device fails to start hotspot when MAC is overridden.

## Features

- **Manual MAC override** – set any valid unicast MAC address (first octet even).
- **Force MAC randomization** – enable hidden randomization support for standard Wi-Fi, Wi‑Fi Direct, and mobile hotspot.
- **Per‑network or per‑connection control** – works when “Use randomized MAC” is selected in Wi‑Fi network details.
- **AP MAC override toggle** – independently enable/disable MAC replacement for the hotspot interface (default: **off**). Helps devices where changing AP MAC breaks hotspot functionality.
- **Multi‑language UI** – supports English and Chinese, switchable via the app menu.

## Compatibility

- Android 12+ (tested up to Android 16 QPR2)
- Rooted devices with **LSPosed** framework installed

## Implementation Details

This module is built on **YukiHookAPI 1.3.2** (fully refactored since v0.0.10). The entry uses the `@InjectYukiHookWithXposed` annotation, and the KSP processor auto-generates the Xposed entry assets (`assets/xposed_init`) and the module-status class.

On modern Android, the Wi-Fi subsystem (via `WifiNative`) can randomize MAC addresses per network or per connection. This module hooks the following system server methods to allow manual MAC assignment when randomization is enabled:

- `WifiNative.setStaMacAddress()` / `WifiVendorHal.setStaMacAddress()` – for station (client) Wi‑Fi.
- `WifiNative.setApMacAddress()` / `WifiVendorHal.setApMacAddress()` – for access point (hotspot) mode.

The module also forces the system to believe that MAC randomization is supported by hooking **`Resources.getBoolean(int)` in `system_server`** and returning true for the following resource names:
- `config_wifi_connected_mac_randomization_supported`
- `config_wifi_p2p_mac_randomization_supported`
- `config_wifi_ap_mac_randomization_supported`

This is useful on devices where the hardware and chipset drivers do support MAC randomization, but the vendor did not enable it in software. Being a plain method hook, it works on frameworks such as LSPosed that do not support XResources replacement, and the toggle takes effect **immediately** (no reboot needed).

### Cross-process Preferences (YukiHookPrefsBridge)

The module app and `system_server` share the same preference data through YukiHookAPI's `YukiHookPrefsBridge`:
- Inside the module app: `context.prefs()` is readable and writable.
- Inside `system_server` (host process): read-only via `XSharedPreferences` on the same file.

So the MAC address and switches you set in the app take effect in the hook logic **in real time**, without a custom broadcast bridge.

### Activation State Detection (YukiHookAPI.Status)

The app determines whether the module is activated in LSPosed via `YukiHookAPI.Status.isModuleActive`. LSPosed injects the real activation state into the module's own process, so the app shows the correct status **immediately after reboot** — no more transient-signal based detection.

### Factory MAC Retrieval ("System MAC")

The module reflects `WifiVendorHal.getStaFactoryMacAddress(iface)` (ColorOS/OPPO method name; the AOSP standard `getFactoryMacAddress` is also tried) to read the **hardware factory MAC**, which is not affected by MAC randomization or the module's replacement. On app process start it actively pulls the value from `system_server` via **YukiHookAPI's `YukiHookDataChannel`** and caches it locally, so the "System MAC" is shown **immediately when the UI opens** (no need to wait for a Wi-Fi broadcast).

### Reliable "Apply MAC Address"

- The module hooks **every constructor** of `WifiNative`, so the instance is cached as soon as the system creates it.
- Tapping "Apply MAC Address" sends a broadcast that **carries the target MAC directly** (no longer dependent on cross-process prefs timing); if the `WifiNative` instance is not ready yet, it automatically retries with a delay — so **the first tap after reboot works immediately**.
- The status-card subtitle **dynamically shows the MAC actually in use** (custom MAC and system MAC on separate lines).

### AP MAC Override Switch

Some devices fail to start the mobile hotspot when the MAC address is modified (error logs show `Could not set interface MAC address for wlan2`). To avoid this, the module provides a dedicated switch in the UI to **disable MAC override for AP mode** (default: off). When this switch is **off**, the module will not intercept calls to `setApMacAddress`, letting the system use the default random MAC for the hotspot.

If you need a custom MAC for hotspot as well, simply turn this switch **on**.

## Usage

1. Install the module and activate it in LSPosed (scope: **System Framework**).
2. Reboot your device.
3. Open the **MAC Editor** app.
4. Enable **“Override randomized MAC”** if you want to replace the randomized MAC with a custom one.
5. Enter a valid MAC address (e.g., `02:00:00:00:00:01`) or tap **“Generate Random MAC”**.
6. Tap **“Apply MAC Address”**.
7. For Wi‑Fi connections, ensure **“Use randomized MAC”** is selected in the network’s “Privacy” setting.
8. To use the custom MAC for hotspot, enable **“Override AP MAC address”** (recommended to keep it off if hotspot fails to start).
9. Reconnect Wi‑Fi or restart hotspot to apply changes.

## Language Switching

The app supports English and Chinese. To switch:
- Tap the three-dot menu in the top‑right corner.
- Select **“Language”** and choose your preferred language.
- The UI will refresh immediately.

## Notes for Qualcomm Devices

Hardware support on certain chipsets can be checked by looking at:
- `/vendor/etc/wifi/kiwi_v2/WCNSS_qcom_cfg.ini`
- `/vendor/firmware/wlan/qca_cld/WCNSS_qcom_cfg.ini`

For legacy Qualcomm devices without MAC randomization support, consider editing `wlan_mac.bin` or `/sys/wifi/mac_addr` directly instead of using this module.

## Acknowledgements

This project is a fork of the original [MAC Editor](https://github.com/jqssun/android-mac-editor) by [jqssun](https://github.com/jqssun).  
The initial open‑source system server hook implementation was provided by [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz).  
We thank the original authors for their great work.

## AI Assistance

This project was developed with the assistance of AI tools.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). All original copyright notices are retained.