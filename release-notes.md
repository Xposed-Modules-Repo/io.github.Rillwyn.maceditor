# 🇨🇳 中文

**v0.1.0 —— 三页面 UI 重构（主页 / 设置 / 关于）+ 底部导航与滑动切换**

## ✨ 本次更新内容

### 🎨 全新三页面界面
- 单页滚动布局重构为 **主页 / 设置 / 关于** 三个页面，通过**底部导航栏 + 左右滑动**切换
- **主页**：模块状态卡片、「覆写随机 MAC」开关、MAC 地址卡片（系统 MAC / 当前 MAC / 待应用 MAC / 生成随机 / 应用按钮）、底部说明——核心功能全部保留在主页
- **设置**：语言行内单选（English / 中文，点击即切换并回到原页面）、「强制启用 MAC 随机化」开关、「覆写 AP MAC 地址」开关
- **关于**：应用图标、版本号（自动读取）、本项目与原项目链接（整行可点击打开浏览器）、来源说明、当前维护者
- 顶部标题随页面切换，界面更清晰易用

### 🛠 修复
- **修复 release APK 丢失 `META-INF/xposed/`（module.prop、scope.list）**：LSPosed 依赖这些文件识别模块与作用域，已恢复并纳入版本管理，确保发布包可被正常识别

### 📦 其他
- 版本号升至 **0.1.0**（versionCode 11）
- 新增 `androidx.viewpager2` 依赖（滑动切换支持）

## 📦 安装说明
1. 在 LSPosed 中启用模块，勾选作用域 **系统框架（System Framework）**
2. **重启手机**（必须）
3. 打开模块 → 主页输入或选择 MAC 地址 → 点击"应用"

> ⚠️ 注意：修改 MAC 需要 root + LSPosed 环境；部分厂商系统可能限制修改。

## 🔗 相关链接
- 使用文档：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README.md
- 更新日志：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG.md

---

# 🇬🇧 English

**v0.1.0 — Three-Page UI Refactor (Home / Settings / About) + Bottom Navigation & Swipe**

## ✨ What's New

### 🎨 Brand-New Three-Page UI
- The single scrolling page was restructured into **Home / Settings / About**, switched via **bottom navigation tabs + left/right swipe**
- **Home**: module status card, "Override randomized MAC" switch, MAC address card (system MAC / active MAC / standby MAC / generate / apply), footer note — all core MAC features stay on the home page
- **Settings**: inline language selection (English / 中文 — switches instantly and returns to your previous page), "Force enable MAC randomization" switch, "Override AP MAC address" switch
- **About**: app icon, version (read automatically), this project & original project links (whole row clickable, opens in browser), source note, maintainer
- Toolbar title follows the current page for a clearer experience

### 🛠 Fixes
- **Fixed release APK missing `META-INF/xposed/` (module.prop, scope.list)**: LSPosed relies on these files to recognize the module and its scope — restored and now tracked in version control so release packages are recognized correctly

### 📦 Other
- Version bumped to **0.1.0** (versionCode 11)
- Added `androidx.viewpager2` dependency (swipe support)

## 📦 Installation
1. Enable the module in LSPosed and tick the scope **System Framework**
2. **Reboot your device** (required)
3. Open the module → enter or pick a MAC address on Home → tap **Apply**

> ⚠️ Note: Changing MAC requires root + LSPosed. Some vendor systems may restrict modification.

## 🔗 Links
- Documentation: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README.md
- Changelog: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG.md
