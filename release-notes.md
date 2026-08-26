# 🇨🇳 中文

**v0.0.10 —— YukiHookAPI 全面重构 + 出厂 MAC 获取 + 一键应用**

## ✨ 本次更新内容

### 🚀 核心重构
- 使用 **YukiHookAPI 1.3.2** 完全重写项目，替代原 Xposed 传统写法，代码更简洁、更易维护
- 修复模块首次安装激活后需重启一次才能正常显示激活状态的问题
- 修复首次点击"应用"按钮 MAC 不生效、需点击第二次的问题（改为广播携带 MAC + 自动重试机制，一次点击即生效）

### 📶 系统 MAC 获取（重要）
- 新增从系统底层直接读取**硬件出厂 MAC 地址**的能力：
  - 优先调用 ColorOS/OPPO 系统 API `WifiVendorHal.getStaFactoryMacAddress()`
  - 回退到 AOSP 标准 `getFactoryMacAddress()` 等系统接口
  - 不再显示系统随机化后的虚拟 MAC
- 模块启动时主动通过数据通道（DataChannel）拉取系统 MAC，状态卡片实时显示

### 🎨 界面优化
- 状态卡片副标题动态显示：自定义 MAC 与系统 MAC 分行展示，修改状态一目了然
- 应用界面文字排版优化，信息更清晰

### 🔧 其他修复
- 恢复并修复 APK 内 `META-INF/xposed/`（module.prop、scope.list），确保 LSPosed 正确识别模块与作用域
- 资源钩子强制开启系统 MAC 随机化能力支持（`config_wifi_*_mac_randomization_supported`）
- 已实测：OnePlus 8T (KB2000) / Android 14 / Zygisk-LSPosed 环境全部功能正常

## 📦 安装说明
1. 在 LSPosed 中启用模块，勾选作用域 **系统框架（System Framework）**
2. **重启手机**（必须）
3. 打开模块 → 输入或选择 MAC 地址 → 点击"应用"

> ⚠️ 注意：修改 MAC 需要 root + LSPosed 环境；部分厂商系统可能限制修改。

## 🔗 相关链接
- 使用文档：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README.md
- 更新日志：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG.md
- 代码总结：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CODE_SUMMARY.md

---

# 🇬🇧 English

**v0.0.10 — YukiHookAPI Full Refactor + Factory MAC Retrieval + One-Tap Apply**

## ✨ What's New

### 🚀 Core Refactor
- Completely rewritten with **YukiHookAPI 1.3.2**, replacing the legacy Xposed style — cleaner and more maintainable code
- Fixed the issue where the module showed "not activated" on first launch after install/activation until a reboot
- Fixed the issue where the MAC was only applied on the *second* tap of "Apply" — now uses a broadcast carrying the MAC plus automatic retry, so one tap just works

### 📶 System MAC Retrieval (Important)
- Added direct retrieval of the **hardware factory MAC address** from the system:
  - Prefers ColorOS/OPPO system API `WifiVendorHal.getStaFactoryMacAddress()`
  - Falls back to AOSP standard `getFactoryMacAddress()` and other system interfaces
  - No longer shows the system's randomized virtual MAC
- On module startup, the system MAC is proactively pulled via the DataChannel and shown live on the status card

### 🎨 UI Improvements
- Status card subtitle is now dynamic: custom MAC and system MAC displayed on separate lines for clarity
- Improved text layout for better readability

### 🔧 Other Fixes
- Restored and fixed `META-INF/xposed/` (module.prop, scope.list) inside the APK so LSPosed correctly recognizes the module and its scope
- Resource hooks force-enable system MAC randomization support (`config_wifi_*_mac_randomization_supported`)
- Tested working on: OnePlus 8T (KB2000) / Android 14 / Zygisk-LSPosed

## 📦 Installation
1. Enable the module in LSPosed and tick the scope **System Framework**
2. **Reboot your device** (required)
3. Open the module → enter or pick a MAC address → tap **Apply**

> ⚠️ Note: Changing MAC requires root + LSPosed. Some vendor systems may restrict modification.

## 🔗 Links
- Documentation: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README.md
- Changelog: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG.md
- Code summary: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CODE_SUMMARY.md
