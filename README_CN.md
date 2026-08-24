# MAC Editor for Android（修改版）

[![Stars](https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/stargazers)
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=LSPosed&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![GitHub](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=GitHub&logo=GitHub)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![build](https://img.shields.io/github/actions/workflow/status/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/apk.yml)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/Xposed-Modules-Repo/io.github.Rillwyn.maceditor?color=green)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/master/LICENSE)

> **注意**：本项目是基于 [MAC Editor](https://github.com/jqssun/android-mac-editor) 原项目（作者 [jqssun](https://github.com/jqssun)）的修改版。所有原始版权归原作者所有。此版本增加了若干增强功能（详见下文）。

**MAC Editor** 是一款免费开源的 Xposed 模块，让您精细控制 Android 设备的 Wi-Fi MAC 地址。它支持手动覆写 MAC，并能强制开启 Android 原生 MAC 随机化功能（只要硬件支持，无需 OEM 额外实现）。

您可以用它来：
- 自定义 MAC 地址以增强隐私保护。
- 将随机 MAC 固定为您指定的值。
- 在厂商禁用 MAC 随机化的设备上强制开启该功能。
- **独立控制移动热点（AP）接口是否使用自定义 MAC** – 如果您的设备在修改热点 MAC 后无法开启热点，可关闭此开关（默认关闭）。

## 功能特性

- **手动覆写 MAC** – 设置任意有效的单播 MAC 地址（首字节必须为偶数）。
- **强制 MAC 随机化** – 对标准 Wi-Fi、Wi‑Fi Direct 和移动热点启用隐藏的随机化支持。
- **按网络/按连接控制** – 在 Wi‑Fi 网络详情中勾选“使用随机 MAC”后生效。
- **AP MAC 覆写开关** – 独立开启/关闭对热点接口（`wlan2`）的 MAC 替换（默认 **关闭**）。可解决部分设备因修改热点 MAC 导致热点无法启动的问题。
- **多语言界面** – 支持英文和中文，可在应用菜单中随时切换。

## 兼容性

- Android 12+（已测试至 Android 16 QPR2）
- 已 Root 设备，并安装 **LSPosed** 框架

## 实现原理

在较新 Android 版本中，Wi-Fi 子系统（通过 `WifiNative`）支持按网络或按连接随机化 MAC 地址。本模块钩住系统服务的以下方法，在随机化启用时手动指定 MAC：

- `WifiVendorHal.setStaMacAddress()` – 用于 Wi-Fi 客户端模式
- `WifiVendorHal.setApMacAddress()` – 用于 Wi-Fi 接入点（热点）模式

同时，模块会强制系统认为 MAC 随机化受支持，通过覆写以下资源布尔值：
- `config_wifi_connected_mac_randomization_supported`
- `config_wifi_p2p_mac_randomization_supported`
- `config_wifi_ap_mac_randomization_supported`

这对于硬件驱动支持随机化、但厂商未在软件中开启的设备尤其有用。

### AP MAC 覆写开关说明

部分设备在修改热点 MAC 地址后无法正常启动热点（日志中会出现 `Could not set interface MAC address for wlan2` 错误）。为避免此问题，模块在 UI 中提供了 **“覆写 AP MAC 地址”** 开关（默认关闭）。当开关关闭时，模块不会拦截 `setApMacAddress` 调用，系统将使用默认随机 MAC 启动热点。

如果您需要为热点也使用自定义 MAC，只需打开此开关即可。

## 使用方法

1. 安装模块，并在 LSPosed 中激活（作用域：**系统框架**）。
2. 重启设备。
3. 打开 **MAC Editor** 应用。
4. 如需替换随机 MAC，开启 **“覆写随机 MAC”**。
5. 输入有效的 MAC 地址（例如 `02:00:00:00:00:01`），或点击 **“生成随机 MAC”**。
6. 点击 **“应用 MAC 地址”**。
7. 对于 Wi‑Fi 连接，请确保在网络的“隐私”设置中选中 **“使用随机 MAC”**。
8. 如需为热点使用自定义 MAC，请开启 **“覆写 AP MAC 地址”**（如果热点无法启动，建议保持关闭）。
9. 重新连接 Wi‑Fi 或重启热点以应用更改。

## 语言切换

应用支持英文和中文。切换方法：
- 点击右上角菜单（三个点）。
- 选择 **“语言”**，然后选择您偏好的语言。
- 界面会立即刷新。

## 高通设备注意事项

某些高通芯片组的硬件支持可通过查看以下文件确认：
- `/vendor/etc/wifi/kiwi_v2/WCNSS_qcom_cfg.ini`
- `/vendor/firmware/wlan/qca_cld/WCNSS_qcom_cfg.ini`

对于不支持 MAC 随机化的老款高通设备，建议直接修改 `wlan_mac.bin` 或 `/sys/wifi/mac_addr`，而不是使用本模块。

## 致谢

本项目是 [MAC Editor](https://github.com/jqssun/android-mac-editor) 原项目（作者 [jqssun](https://github.com/jqssun)）的修改版。  
最初的系统服务钩子实现由 [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz) 提供。  
感谢原作者的出色工作。

## AI 辅助开发

本项目使用 AI 辅助开发。

## 许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。所有原始版权声明均已保留。