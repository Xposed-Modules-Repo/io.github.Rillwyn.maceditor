# MAC Editor for Android

[English](README.md) | [中文](README_CN.md) | [العربية](README_AR.md)

[![Stars](https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/stargazers)
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=LSPosed&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![GitHub](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=GitHub&logo=GitHub)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![build](https://img.shields.io/github/actions/workflow/status/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/apk.yml)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/Xposed-Modules-Repo/io.github.Rillwyn.maceditor?color=green)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/master/LICENSE)

> **Note**: This is an enhanced fork of the original [MAC Editor](https://github.com/jqssun/android-mac-editor) by [jqssun](https://github.com/jqssun), actively maintained and expanded by **Rillwyn** and **Eng. Amr Eldeeb**. All original copyrights and credits are preserved under the GPL-3.0 license.

**MAC Editor** is a modern, high-performance Xposed module built with **YukiHookAPI 1.3.2** that gives you granular, reliable control over Wi-Fi and Mobile Hotspot (AP) MAC addresses across Android devices and OEM skins.

You can use it to:

- Customize MAC address behavior for privacy and testing.
- Override randomized MAC addresses with custom or generated unicast MACs.
- Force-enable hardware-level MAC randomization on devices where vendors locked it in software.
- **Instant Zero-Click Apply**: Toggling switches in the UI automatically applies MAC changes in real time.
- Independently control client Wi-Fi and Access Point (Hotspot) MAC address overrides.

---

## Key Features

- **⚡ Zero-Click Instant Apply**:
  Toggling the **"Override randomized MAC"** or **"Override AP MAC address"** switches immediately synchronizes changes with the Wi-Fi/AP HAL and active network interfaces via real-time IPC broadcasts — no manual button tap or hotspot reset needed!
- **🌐 Universal Multi-Vendor Compatibility (Android 10 – Android 16)**:
  Thoroughly engineered to support all major OEM skins:
  - **Google Pixel** (AOSP / Pixel UI)
  - **Samsung** (One UI — `SemWifiNative` / `SemWifiVendorHal`)
  - **Xiaomi / Redmi / POCO** (MIUI / HyperOS — `MiuiWifiNative`)
  - **Oppo / OnePlus / Realme** (ColorOS / OxygenOS / Realme UI)
  - **Vivo / iQOO** (OriginOS / Funtouch OS)
  - **Honor / Huawei** (MagicOS / EMUI — `HwWifiNative`)
  - **Motorola, Sony, Asus, Nothing, Transsion (Infinix / Tecno), HTC, ZTE**
- **🔍 Dynamic AP Interface Detection**:
  Dynamically scans and hooks active mobile hotspot interfaces (`ap0`, `softap0`, `swlan0`, `wlan1`, etc.), ensuring full compatibility regardless of chipset driver conventions.
- **🌍 Full Arabic Localization & RTL Layout Support**:
  - Complete Arabic translation (`values-ar/strings.xml`).
  - Native Right-to-Left (RTL) layout mirroring.
  - Enforced Left-to-Right (LTR) text direction for MAC address input fields, display chips, and hexadecimal addresses.
  - 3-way quick language switcher in Settings: **English / 中文 / العربية**.
- **🎨 Modern Three-Page Material 3 UI**:
  - **Home**: Module status card, instant override switch, MAC address card (system MAC / current active MAC / custom MAC input / generator / apply button), and live dynamic status info.
  - **Settings**: Inline language selector, force MAC randomization toggle, and hotspot (AP) MAC override toggle.
  - **About**: App details, version info, repository links, source credits, and maintainer information.
  - Swiping gestures and bottom navigation bar integration via `ViewPager2`.
- **🏭 Real Factory MAC Detection**:
  Directly queries the physical Wi-Fi hardware chip via OEM reflection (`WifiVendorHal.getStaFactoryMacAddress` / `getFactoryMacAddress`) to display the genuine factory MAC, cached seamlessly via `YukiHookDataChannel`.

---

## Compatibility

- **Android Versions**: Android 10, 11, 12, 12L, 13, 14, 15, and Android 16 (`minSdk = 29`, `targetSdk = 37`).
- **Frameworks**: Rooted devices running **LSPosed** (Zygisk / Riru) or compatible modern Xposed frameworks.
- **Scopes**: System Framework (`android` / `system`) and Settings (`com.android.settings`).

---

## Installation & Usage

1. Install the APK and open your Xposed manager (**LSPosed**).
2. Enable the module and verify that **System Framework** and **Settings** are checked in the scope list.
3. **Reboot your device** (required upon first installation).
4. Open the **MAC Editor** app:
   - To customize your MAC, enter a valid address (e.g., `02:00:00:00:00:01`) or tap **Generate Random MAC**.
   - Tap **Apply MAC Address** (or simply toggle the **Override randomized MAC** switch for instant zero-click application).
   - In your Wi-Fi network settings, set Privacy to **"Use randomized MAC"**.
   - To customize hotspot MAC, toggle **"Override AP MAC address"** in Settings.

---

## Architecture & Implementation

### YukiHookAPI 1.3.2 Architecture

The module uses `@InjectYukiHookWithXposed` with the modern KSP compiler (`ksp-xposed`), automatically producing `assets/xposed_init` and module status verification components.

### Resilient System Server Hooks

- **Multi-tiered Wi-Fi service discovery**: Resolves Wi-Fi classes through APEX `service-wifi.jar` classloader, `SystemServiceManager.loadClassFromLoader`, dynamic `ServiceManager` registration hooks, and vendor-specific HAL hooks.
- **Resource Hooking**: Hooks `Resources.getBoolean(int)` to force-enable:
  - `config_wifi_connected_mac_randomization_supported`
  - `config_wifi_p2p_mac_randomization_supported`
  - `config_wifi_ap_mac_randomization_supported`
- **Zero-Latency IPC**: Communicates state between the user application and `system_server` via `YukiHookPrefsBridge` and targeted dynamic broadcasts with `ContextCompat.RECEIVER_EXPORTED`.

---

## Project Maintainers

- **Maintainers**: [Rillwyn](https://github.com/Rillwyn) & [Eng. Amr Eldeeb](https://github.com/engamreldeeb)
- **Original Project**: [jqssun/android-mac-editor](https://github.com/jqssun/android-mac-editor)
- **Initial Concept**: [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz)

---

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE). All original copyright notices are retained.
