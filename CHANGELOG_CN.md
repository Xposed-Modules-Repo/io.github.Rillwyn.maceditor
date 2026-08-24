# 修改记录（中文版）

本文件记录了基于 [MAC Editor](https://github.com/jqssun/android-mac-editor) 原项目（作者 [jqssun](https://github.com/jqssun)）所做的所有修改。

所有修改均遵循原项目许可证（GPL-3.0），并保留了原始版权声明。

---

## [0.0.9] - 2026-08-24

### Fixed
- **Release 版本激活问题**：修正 `META-INF/xposed` 目录下 `java_init.list`、`module.prop`、`scope.list` 中的包名和作用域名称，确保 LSPosed 正确加载模块。
---

## [0.0.8] - 2026-08-24

### 新增功能
- **多语言界面**：支持英文和中文，通过应用菜单切换，界面即时刷新。
- **AP MAC 覆写开关**：新增独立开关，控制是否对移动热点（AP）接口应用自定义 MAC 地址。默认**关闭**，避免部分设备因修改热点 MAC 导致热点启动失败。

### 改进
- **热点兼容性修复**：改用接口名称（`wlan2`）而非方法比较来识别 AP 调用，确保 AP 覆写开关准确生效。
- **语言存储优化**：将语言偏好存储在本地 `SharedPreferences`，与模块远程配置分离，解决了之前切换无效的问题。

### 技术细节
- 修改 `WifiServiceHooker.kt`：通过 `ifaceName` 判断是否为 AP 接口，依据 `apMacOverride` 开关决定是否替换 MAC。
- 修改 `MainActivity.kt`：在 `attachBaseContext` 中从本地偏好读取语言，使用 `recreate()` 刷新界面。
- 新增 `values-zh/strings.xml` 中文资源文件。
- 更新 `activity_main.xml`：添加 AP MAC 覆写卡片。
- 版本号升级至 `0.0.8`（`app/build.gradle.kts` 中 `versionName`）。

### 文档
- 更新 `README.md` 和新增 `README_CN.md`，说明新功能和用法。
- 创建 `CHANGELOG.md` 和 `CHANGELOG_CN.md` 记录所有修改。

---

## 原始版本（上游）

初始版本基于 [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz) 的 `MacSposed` 实现，由 [jqssun](https://github.com/jqssun) 完善并开源。

原始功能包括：
- 钩子 `setStaMacAddress` 和 `setApMacAddress`。
- 强制 MAC 随机化资源覆写。
- 基本 UI 设置 MAC 地址。