# 修改记录（中文版）

本文件记录了基于 [MAC Editor](https://github.com/jqssun/android-mac-editor) 原项目（作者 [jqssun](https://github.com/jqssun)）所做的所有修改。

所有修改均遵循原项目许可证（GPL-3.0），并保留了原始版权声明。

---

## [0.0.10] - 2026-08-25

### 核心重构
- **全面迁移至 YukiHookAPI 1.3.2**：以 YukiHookAPI 官方机制重建模块入口与 Hook 实现，修复旧版代码引用了不存在的 API（`YukiModule`、`Preferences.default`、`module.encounter`、`module.injectResource` 等）导致源码无法编译的问题。
  - 新入口 `HookEntry` 使用 `@InjectYukiHookWithXposed` + `IYukiHookXposedInit`，由 KSP 处理器自动生成 `assets/xposed_init` 与模块状态检测类，不再手工维护 `META-INF/xposed` 资产。

### Fixed
- **重启后误显示“未激活”**：激活状态改用 `YukiHookAPI.Status.isModuleActive`（LSPosed 自动向模块自身进程注入激活状态），不再依赖“是否收到系统 MAC 广播”这种瞬态信号。重启后进入应用立即显示正确状态，无需清后台。
- **重启后第一次点击“应用 MAC”无反应**：新增 Hook `WifiNative` 全部构造器，系统一创建 WifiNative 即缓存实例；同时兼容 `WifiVendorHal` 上的同名方法。点击“应用 MAC”时实例必定可用，首次点击即可生效。
- **跨进程偏好设置断裂**：模块应用与 system_server 之间改用 YukiHookAPI 的 `YukiHookPrefsBridge`（模块内可读写、宿主内 XSharedPreferences 只读同一份数据），彻底替代旧的“自定义 prefs 桥 + 广播”方案，`customMac`、`hookActive`、`apMacOverride` 等设置实时共享。
- **偏好文件可读性**：启用 `isEnableHookSharedPreferences`，强制模块偏好文件权限为 0664（world-readable），确保 system_server 在任何框架模式下均可读取。

### Changed
- 构建配置：`compileSdk` 升至 37（YukiHookAPI 1.3.2 依赖要求）；引入 KSP（`ksp-xposed`）；以本地 `libs/api-82.jar` 提供 XposedBridge 编译期 API；移除已失效的 `maven.highcapable.me` 仓库。
- **资源钩子改为 Hook `Resources.getBoolean`**：在 system_server 中拦截 `getBoolean(int)` 并按资源名返回 true（普通方法 Hook，兼容 LSPosed 等不支持 XResources 资源替换的框架；开关切换即时生效，无需重启）。
- **Hook 改用 `Member.hook` 新写法**：直接反射目标成员再 Hook，避免旧 finder 写法（`method { }.hook { }`）在部分环境的重载解析异常。
- **Hook 安装双保险**：`loadSystem` 时直接 Hook + 监听 `SystemServiceManager.loadClassFromLoader`，在 WifiService 类加载时二次安装，确保任何加载时序下都能 hook 上。
- **Xposed 资产与模块声明**：`module.prop`、`scope.list` 恢复在 `src/main/resources/META-INF/xposed/`（打包到 **APK 根目录 `META-INF/xposed/`**）；Manifest 补充传统 Xposed 模块声明（`MODULE_SETTINGS`、`xposedmodule`/`xposeddescription`/`xposedminversion`/`xposedscope`），确保 LSPosed 正确识别。入口由 KSP 生成的 `assets/xposed_init` 提供，**不创建 `java_init.list`**（该文件存在会让 LSPosed 改走 libxposed 加载路径导致模块失效）。
- **“系统 MAC”显示出厂 MAC**：通过反射 `WifiVendorHal.getStaFactoryMacAddress(iface)`（ColorOS/OPPO 方法名；AOSP 标准为 `getFactoryMacAddress`，已做多候选兼容）获取**硬件出厂 MAC**，不再显示随机化后的 MAC；应用进程启动时经 `YukiHookDataChannel` 主动拉取并缓存，打开界面立即显示。
- **一次点击“应用 MAC 地址”立即生效**：广播直接携带目标 MAC（不再依赖跨进程 prefs 读取时序），`WifiNative` 实例未就绪时自动延迟重试。
- **状态展示优化**：状态卡片副标题动态显示当前实际使用的 MAC（自定义 MAC 与系统 MAC 分行展示）；未设置自定义 MAC 时“当前 MAC”显示系统 MAC。

### 文档
- 更新 `README.md`、`README_CN.md`。
- 新增 `CODE_SUMMARY.md` 代码总结文档。
- 版本号升级至 `0.0.10`（`app/build.gradle.kts`）。

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