# 🇸🇦 العربية

**v0.2.0 —— توافق شامل لجميع الشركات المصنعة (أندرويد 10–16) + تطبيق فوري بدون نقرات + دعم كامل للغة العربية والاتجاه من اليمين لليسار (RTL)**

## ✨ ما الجديد في هذا الإصدار

### 🚀 توافق شامل مع كافة الهواتف والشركات المصنعة (أندرويد 10 إلى أندرويد 16)
- دعم كامل وموسع لجميع الشركات المصنعة وأنظمتها: Google Pixel و Samsung (One UI) و Xiaomi / Redmi (MIUI / HyperOS) و Oppo / OnePlus / Realme (ColorOS / OxygenOS) و Vivo / iQOO (OriginOS / Funtouch OS) و Honor / Huawei (MagicOS / EMUI) و Motorola و Sony و Asus و Nothing و Transsion (Infinix / Tecno) و HTC و ZTE.
- نظام متقدم لاكتشاف فئات نظام الواي فاي (Wi-Fi ClassLoader) عبر APEX `service-wifi.jar`، و `SystemServiceManager.loadClassFromLoader`، و `ServiceManager.addService("wifi")`، مع دعم الفئات الأصلية وخطافات HAL الخاصة بكل مصنّع (`WifiNative`، `WifiVendorHal`، `SemWifiNative`، `SemWifiVendorHal`، `MiuiWifiNative`، `MtkWifiNative`، `HwWifiNative`).
- فحص واكتشاف ديناميكي لواجهات نقطة الاتصال المحمولة النشطة (`ap0`، `softap0`، `swlan0`، `wlan1` إلخ) مع ضمان مطابقة مواصفات IEEE 802.11 (Unicast + Locally Administered).

### ⚡ تطبيق فوري وتلقائي (Zero-Click Instant Apply)
- بمجرد تبديل مفتاح "تجاوز عشوائية MAC" أو "تجاوز MAC لنقطة الاتصال"، يتم تطبيق التغييرات فورياً على مستوى عتاد الواي فاي وواجهات الشبكة النشطة عبر بث IPC في أجزاء من الثانية، دون الحاجة للضغط يدوياً على زر "تطبيق عنوان MAC" أو إعادة تشغيل الواي فاي ونقطة الاتصال.
- رسائل تأكيد تفاعلية سريعة (Snackbars) وتحديث فوري لكرت الحالة.

### 🌐 تعريب كامل ودعم أصلي لاتجاه اليمين لليسار (RTL)
- ترجمة عربية كاملة لجميع شاشات وإعدادات ورسائل التطبيق (`values-ar/strings.xml`).
- دعم اتجاه الشاشة من اليمين إلى اليسار (RTL) تلقائياً مع لغة النظام.
- إجبار حقول وعناوين MAC على الاتجاه من اليسار إلى اليمين (LTR) للحفاظ على تسلسل العناوين الست عشرية ومنع تشوهها.
- محول لغات ثلاثي سريع في صفحة الإعدادات (English / 中文 / العربية).

### 🛠 تحسينات تقنية وأمان
- ترقية الإصدار المستهدف إلى أندرويد 16 (`targetSdk = 37`، `compileSdk = 37`).
- استخدام `androidx.core.content.ContextCompat.registerReceiver` مع علم `RECEIVER_EXPORTED` الصريح لتلبية اشتراطات أندرويد 14+ و 16 وتجاوز فحوصات Lint بنجاح.
- تحديث نطاق Xposed ليشمل `com.android.settings` بجانب `system`.
- إدارة وتطوير المشروع المشترك بواسطة: **Rillwyn** و **م. عمرو الديب (Eng. Amr Eldeeb)**.

---

## 📦 تعليمات التثبيت
1. قم بتفعيل الموديل في تطبيق LSPosed وحدد النطاق: **إطار عمل النظام (System Framework)** و **الإعدادات (Settings)**.
2. **أعد تشغيل هاتفك** (مطلوب عند أول تثبيت).
3. افتح تطبيق MAC Editor واستمتع بالتحكم الكامل والتطبيق الفوري لعناوين MAC!

> ⚠️ ملاحظة: يتطلب تعديل عنوان MAC صلاحيات Root وتثبيت بيئة LSPosed.

## 🔗 الروابط
- دليل الاستخدام: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README_AR.md
- سجل التغييرات: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG_AR.md

---

# 🇬🇧 English

**v0.2.0 — Universal Multi-Vendor Compatibility (Android 10–16) + Zero-Click Instant Apply + Full Arabic & RTL Support**

## ✨ What's New

### 🚀 Universal Multi-Vendor Compatibility (Android 10 to Android 16)
- Comprehensive support across all major OEM devices: Google Pixel, Samsung (One UI), Xiaomi / Redmi (MIUI / HyperOS), Oppo / OnePlus / Realme (ColorOS / OxygenOS), Vivo / iQOO (OriginOS / Funtouch OS), Honor / Huawei (MagicOS / EMUI), Motorola, Sony, Asus, Nothing, Transsion (Infinix / Tecno), HTC, and ZTE.
- Multi-tier Wi-Fi ClassLoader discovery spanning APEX `service-wifi.jar`, `SystemServiceManager.loadClassFromLoader`, `ServiceManager.addService("wifi")`, dynamic binder reflection, and OEM-specific native/HAL classes (`WifiNative`, `WifiVendorHal`, `SemWifiNative`, `SemWifiVendorHal`, `MiuiWifiNative`, `MtkWifiNative`, `HwWifiNative`).
- Dynamic detection of active mobile hotspot interfaces (`ap0`, `softap0`, `swlan0`, `wlan1`, etc.) with IEEE 802.11 unicast and locally-administered bit compliance.

### ⚡ Zero-Click Instant Apply
- Toggling "Override randomized MAC" or "Override AP MAC address" immediately pushes the configuration to the underlying Wi-Fi/AP HAL and active network interfaces in real time via IPC broadcasts — no manual tap on "Apply MAC Address" or toggling Wi-Fi/hotspot on and off required.
- Instant visual snackbar feedback and dynamic status card updates.

### 🌐 Arabic Localization & Full RTL Support
- Complete Arabic translation across all UI screens, dialogs, and messages (`values-ar/strings.xml`).
- Native Right-to-Left (RTL) mirroring matching the active locale.
- Preserved Left-to-Right (LTR) text direction for MAC inputs, display chips, and hexadecimal addresses.
- 3-way quick language toggle in the Settings screen: English / 中文 / العربية.

### 🛠 Technical & Security Refinements
- Target SDK updated to Android 16 (`targetSdk = 37`, `compileSdk = 37`), maintaining support down to Android 10 (`minSdk = 29`).
- Adopted `androidx.core.content.ContextCompat.registerReceiver` with explicit `RECEIVER_EXPORTED` flags for strict Android 14+ / 16 compliance and 0 lint warnings.
- Expanded Xposed scope to include `com.android.settings`.
- Maintained and developed collaboratively by: **Rillwyn** & **Eng. Amr Eldeeb**.

---

## 📦 Installation
1. Enable the module in LSPosed and check scopes: **System Framework** and **Settings**.
2. **Reboot your device** (required on initial install).
3. Open MAC Editor to configure your custom MAC address with instant zero-click effect.

> ⚠️ Note: Modifying MAC addresses requires root + LSPosed.

## 🔗 Links
- Documentation: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README.md
- Changelog: https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG.md

---

# 🇨🇳 中文

**v0.2.0 —— 全厂商广泛兼容（Android 10–16）+ 零点击即时生效 + 阿拉伯语与 RTL 界面全支持**

## ✨ 本次更新内容

### 🚀 全厂商深度兼容（Android 10 至 Android 16）
- 深度适配主流所有 OEM 厂商：Google Pixel、Samsung（One UI）、Xiaomi / Redmi（MIUI / HyperOS）、Oppo / OnePlus / Realme（ColorOS / OxygenOS）、Vivo / iQOO（OriginOS / Funtouch OS）、Honor / 华为（MagicOS / EMUI）、Motorola、Sony、Asus、Nothing、传音 Transsion（Infinix / Tecno）、HTC 与 ZTE。
- 多重 ClassLoader 发现机制：覆盖 APEX `service-wifi.jar`、`SystemServiceManager.loadClassFromLoader`、`ServiceManager.addService("wifi")`、动态 Binder 反射及多厂商 Native/HAL 类兼容（`WifiNative`、`WifiVendorHal`、`SemWifiNative`、`SemWifiVendorHal`、`MiuiWifiNative`、`MtkWifiNative`、`HwWifiNative`）。
- 动态感知活跃移动热点接口（`ap0`、`softap0`、`swlan*`、`wlan1` 等），遵循 IEEE 802.11 单播与本地管理位规范。

### ⚡ 零点击即时生效（Zero-Click Instant Apply）
- 在界面中切换「覆写随机 MAC」或「覆写 AP MAC 地址」开关时，配置立即经由 IPC 广播同步至底层 Wi-Fi/AP HAL 与网络接口，毫秒级生效，无需再点击“应用 MAC 地址”，无需反复开关 Wi-Fi 或热点。
- 界面提供即时反馈提示（Snackbar）与动态状态卡片联动。

### 🌐 阿拉伯语本地化与 RTL 界面支持
- 完整阿拉伯语支持（`values-ar/strings.xml`），覆盖所有屏幕文本、弹窗与提示。
- 全局 RTL 镜像布局支持（`android:supportsRtl="true"`），根据系统/选择语言动态调整布局方向。
- MAC 地址输入框、展示标签及十六进制文本强制保持 LTR（从左至右），防止双向文本乱序。
- 设置页面新增三语单选切换：English / 中文 / العربية。

### 🛠 架构优化与安全合规
- 目标 SDK 升级至 Android 16（`targetSdk = 37`，`compileSdk = 37`），最低兼容 Android 10（`minSdk = 29`）。
- 广播接收器采用 `androidx.core.content.ContextCompat.registerReceiver` 并显式声明 `RECEIVER_EXPORTED`，满足 Android 14+ / 16 安全规范并通过 Lint 严格检查。
- Xposed 作用域补充 `com.android.settings`。
- 本版本由 **Rillwyn** 与 **Eng. Amr Eldeeb** 共同维护与增强。

---

## 📦 安装说明
1. 在 LSPosed 中启用模块，勾选作用域 **系统框架（System Framework）** 与 **设置（Settings）**。
2. **重启手机**（首次安装必须）。
3. 打开模块，即刻享受全自动即时生效的 MAC 定制体验！

> ⚠️ 注意：修改 MAC 需要 root + LSPosed 环境；部分厂商系统可能限制修改。

## 🔗 相关链接
- 使用文档：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/README_CN.md
- 更新日志：https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/main/CHANGELOG_CN.md
