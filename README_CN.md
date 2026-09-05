# MAC Editor for Android（修改增强版）

[English](README.md) | [中文](README_CN.md) | [العربية](README_AR.md)

[![Stars](https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/stargazers)
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=LSPosed&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![GitHub](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=GitHub&logo=GitHub)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![build](https://img.shields.io/github/actions/workflow/status/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/apk.yml)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/Xposed-Modules-Repo/io.github.Rillwyn.maceditor?color=green)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/master/LICENSE)

> **注意**：本项目是基于 [MAC Editor](https://github.com/jqssun/android-mac-editor) 原项目（作者 [jqssun](https://github.com/jqssun)）的深度修改与增强版本，当前由 **Rillwyn** 与 **Eng. Amr Eldeeb** 共同维护开发。所有修改均遵循 GPL-3.0 许可证，并保留原始版权声明。

**MAC Editor** 是一款基于 **YukiHookAPI 1.3.2** 构建的现代化开源 Xposed 模块，为您提供针对 Android 设备 Wi-Fi 与移动热点（AP）MAC 地址的精细化、高可靠性控制。

您可以用它来：

- 自定义 MAC 地址以加强隐私保护或调试测试。
- 将系统的随机化 MAC 地址替换为指定的单播 MAC 地址。
- 在部分厂商禁用了硬件随机化的机型上，强制开启系统级 MAC 随机化支持。
- **零点击即时生效**：界面切换开关即刻毫秒级同步硬件 HAL 与活跃网络接口。
- 独立控制客户端 Wi-Fi 与热点（AP）的 MAC 替换行为。

---

## 核心功能

- **⚡ 零点击即时生效（Zero-Click Instant Apply）**：
  在界面中切换「覆写随机 MAC」或「覆写 AP MAC 地址」开关时，立即通过 IPC 广播更新底层 Wi-Fi/AP HAL 与活跃接口，无需再手动点击“应用 MAC 地址”，无需反复开关 Wi-Fi 或热点！
- **🌐 全厂商深度兼容（Android 10 至 Android 16）**：
  全方位适配主流 Android 厂商及定制系统：
  - **Google Pixel**（AOSP / 原生 Android）
  - **Samsung**（One UI —— `SemWifiNative` / `SemWifiVendorHal`）
  - **Xiaomi / Redmi / POCO**（MIUI / HyperOS —— `MiuiWifiNative`）
  - **Oppo / OnePlus / Realme**（ColorOS / OxygenOS / Realme UI）
  - **Vivo / iQOO**（OriginOS / Funtouch OS）
  - **Honor / 华为**（MagicOS / EMUI —— `HwWifiNative`）
  - **Motorola、Sony、Asus、Nothing、传音 Transsion (Infinix / Tecno)、HTC 与 ZTE**
- **🔍 动态 AP 热点接口感知**：
  动态枚举并 Hook 活跃的热点网络接口（`ap0`、`softap0`、`swlan0`、`wlan1` 等），解决不同芯片驱动命名的兼容性痛点。
- **🌍 阿拉伯语完整本地化与 RTL 镜像适配**：
  - 完整阿拉伯语支持（`values-ar/strings.xml`）。
  - 全局 RTL 镜像布局跟随系统语言自动切换。
  - MAC 地址输入框、展示标签及十六进制文本强制保持 LTR（从左至右），防止双向文本乱序。
  - 设置页面提供三语快速单选切换：**English / 中文 / العربية**。
- **🎨 现代三页面 Material 3 UI 架构**：
  - **主页**：模块状态卡片、即时生效开关、MAC 地址卡片（系统出厂 MAC / 当前实际 MAC / 自定义输入框 / 随机生成 / 应用按钮）与动态状态展示。
  - **设置**：语言行内单选、强制启用 MAC 随机化开关、AP MAC 覆写开关。
  - **关于**：应用信息、版本号自动读取、项目链接、来源致谢与当前维护者信息。
  - 基于 `ViewPager2` 实现的平滑手势滑动与底部导航栏双向联动。
- **🏭 真实出厂 MAC 读取**：
  通过反射底层 `WifiVendorHal.getStaFactoryMacAddress` / `getFactoryMacAddress` 直接获取硬件真实 MAC，并通过 `YukiHookDataChannel` 跨进程即时回传模块界面缓存展示。

---

## 兼容性说明

- **Android 系统版本**：Android 10、11、12、12L、13、14、15 及 Android 16（`minSdk = 29`，`targetSdk = 37`）。
- **运行环境**：已 Root 并安装 **LSPosed**（Zygisk / Riru）或现代兼容器件。
- **推荐作用域**：系统框架（`android` / `system`）及 系统设置（`com.android.settings`）。

---

## 安装与使用说明

1. 在 LSPosed 中启用本模块，勾选作用域 **系统框架（System Framework）** 与 **设置（Settings）**。
2. **重启手机**（首次安装必须重启以加载 System Server Hook）。
3. 打开 **MAC Editor** 应用：
   - 输入您期望的 MAC 地址（例如 `02:00:00:00:00:01`）或点击 **“生成随机 MAC”**。
   - 点击 **“应用 MAC 地址”**（或直接开启 **“覆写随机 MAC”** 开关即可全自动即时应用）。
   - 在系统的 Wi-Fi 连接详情中，确保隐私设置选择了 **“使用随机 MAC”**。
   - 如需在开启移动热点时同样替换 MAC，请进入设置页面开启 **“覆写 AP MAC 地址”**。

---

## 架构与核心实现

### YukiHookAPI 1.3.2 现代化重构

模块入口采用 `@InjectYukiHookWithXposed` 注解，由 KSP 编译器（`ksp-xposed`）自动生成 `assets/xposed_init` 和激活状态检测类，免去繁琐手工配置。

### 弹性的 System Server Hook 体系

- **多层级 ClassLoader 发现机制**：覆盖 APEX `service-wifi.jar`、`SystemServiceManager.loadClassFromLoader`、动态 `ServiceManager` 注入与各大厂商特定的 HAL 类。
- **资源 Hook（Resources.getBoolean）**：通过 Hook `Resources.getBoolean(int)`，动态强制开启以下底层布尔值：
  - `config_wifi_connected_mac_randomization_supported`
  - `config_wifi_p2p_mac_randomization_supported`
  - `config_wifi_ap_mac_randomization_supported`
- **低延迟 IPC 跨进程通信**：基于 `YukiHookPrefsBridge` 与注册了 `RECEIVER_EXPORTED` 的动态广播实现模块界面与 `system_server` 的实时通信。

---

## 项目维护者

- **维护与功能增强**：[Rillwyn](https://github.com/Rillwyn) 与 [Eng. Amr Eldeeb](https://github.com/engamreldeeb)
- **原项目开源作者**：[jqssun/android-mac-editor](https://github.com/jqssun/android-mac-editor)
- **最初思路与原型**：[David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz)

---

## 开源许可证

本项目基于 [GNU General Public License v3.0](LICENSE) 协议开源。所有原始版权声明均予保留。
