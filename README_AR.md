# محرر عنوان الماك لأندرويد (MAC Editor for Android)

[English](README.md) | [中文](README_CN.md) | [العربية](README_AR.md)

[![Stars](https://img.shields.io/github/stars/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/stargazers)
[![LSPosed](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=LSPosed&logo=Android&style=flat&labelColor=F48FB1&logoColor=ffffff)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![GitHub](https://img.shields.io/github/downloads/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/total?label=GitHub&logo=GitHub)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![release](https://img.shields.io/github/v/release/Xposed-Modules-Repo/io.github.Rillwyn.maceditor)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/releases)
[![build](https://img.shields.io/github/actions/workflow/status/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/apk.yml)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/actions/workflows/apk.yml)
[![license](https://img.shields.io/github/license/Xposed-Modules-Repo/io.github.Rillwyn.maceditor?color=green)](https://github.com/Xposed-Modules-Repo/io.github.Rillwyn.maceditor/blob/master/LICENSE)

> **ملاحظة**: هذا المشروع عبارة عن نسخة معدّلة ومطوّرة بشكل شامل من المشروع الأصلي [MAC Editor](https://github.com/jqssun/android-mac-editor) للمطور [jqssun](https://github.com/jqssun)، وتتم صيانته وتطويره بواسطة **Rillwyn** و **م. عمرو الديب (Eng. Amr Eldeeb)**. جميع التعديلات تخضع لرخصة GPL-3.0 مع الحفاظ التام على حقوق النشر الأصلية.

**MAC Editor** هو موديل Xposed متطور مفتوح المصدر تم بناؤه باستخدام **YukiHookAPI 1.3.2** ليمنحك تحكماً دقيقاً وفورياً وشاملاً في عنوان MAC الخاص بشبكات الواي فاي (Wi-Fi) ونقطة الاتصال المحمولة (Hotspot/AP) عبر أجهزة أندرويد ومن جميع الشركات المصنعة.

يمكنك استخدامه في:

- تخصيص سلوك عنوان MAC لحماية الخصوصية وتجنب التتبع.
- تجاوز عنوان MAC العشوائي واستبداله بعنوان أحادي مخصص (Unicast) تختاره بنفسك.
- فرض تفعيل خاصية عشوائية MAC على مستوى العتاد في الأجهزة التي قامت الشركات المصنعة بتعطيلها برمجياً.
- **التطبيق الفوري التلقائي (Zero-Click Apply)**: بمجرد تبديل المفاتيح في الواجهة، يتم تطبيق التغييرات فوراً في أجزاء من الثانية.
- التحكم المستقل في تجاوز عنوان MAC لشبكات الواي فاي العادية ونقطة الاتصال المحمولة (AP).

---

## أبرز المميزات

- **⚡ تطبيق فوري بدون نقرات إضافية (Zero-Click Instant Apply)**:
  بمجرد تشغيل أو إيقاف مفتاح **"تجاوز عشوائية MAC"** أو **"تجاوز MAC لنقطة الاتصال"** في الواجهة، يتم إرسال التحديثات فورياً عبر بث IPC إلى طبقة عتاد الواي فاي (HAL) وواجهات الشبكة النشطة — لا حاجة للضغط يدوياً على زر "تطبيق" أو إعادة تشغيل الواي فاي ونقطة الاتصال!
- **🌐 توافق شامل لجميع الشركات المصنعة (أندرويد 10 حتى أندرويد 16)**:
  تمت هندسة الموديل ليعمل بكفاءة فائقة وموثوقية مع كافة واجهات وأجهزة الشركات:
  - **Google Pixel** (نظام أندرويد الخام AOSP / واجهة Pixel)
  - **Samsung** (واجهة One UI — دعم `SemWifiNative` و `SemWifiVendorHal`)
  - **Xiaomi / Redmi / POCO** (واجهات MIUI و HyperOS — دعم `MiuiWifiNative`)
  - **Oppo / OnePlus / Realme** (واجهات ColorOS و OxygenOS و Realme UI)
  - **Vivo / iQOO** (واجهات OriginOS و Funtouch OS)
  - **Honor / Huawei** (واجهات MagicOS و EMUI — دعم `HwWifiNative`)
  - **Motorola، Sony، Asus، Nothing، Transsion (Infinix / Tecno)، HTC، ZTE**
- **🔍 استكشاف ديناميكي لواجهات نقطة الاتصال المحمولة (AP)**:
  فحص واستكشاف تلقائي لأسماء واجهات نقطة الاتصال المحمولة النشطة (`ap0`، `softap0`، `swlan0`، `wlan1` إلخ)، مما يضمن تغيير عنوان MAC لنقطة الاتصال مهما اختلف تعريف الشريحة أو الشركة المصنعة.
- **🌍 تعريب كامل للغة العربية ودعم اتجاه الواجهة من اليمين لليسار (RTL)**:
  - تعريب كامل ودقيق لجميع النصوص والرسائل وقوائم التطبيق (`values-ar/strings.xml`).
  - دعم أصلي لاتجاه الشاشة من اليمين إلى اليسار (RTL).
  - حماية حقول إدخال وعرض عناوين MAC وإجبارها على الاتجاه من اليسار إلى اليمين (LTR) للحفاظ على تسلسل العناوين الست عشرية (Hexadecimal BiDi) ومنع تشوهها.
  - محول لغات ثلاثي سريع ومباشر في صفحة الإعدادات: **English / 中文 / العربية**.
- **🎨 واجهة مستخدم حديثة من ثلاث صفحات (Material 3)**:
  - **الرئيسية**: كرت حالة تفعيل الموديل، مفتاح التجاوز الفوري، كرت عناوين MAC (عنوان المصنع الأصلي / العنوان النشط الفعلي / حقل العنوان المخصص / زر التوليد العشوائي / زر التطبيق اليدوي).
  - **الإعدادات**: اختيار اللغة، مفتاح فرض تفعيل عشوائية MAC، مفتاح تجاوز MAC لنقطة الاتصال.
  - **حول**: معلومات التطبيق، قراءة الإصدار تلقائياً، روابط المستودعات والمصادر، ومعلومات المطور.
  - دعم كامل للسحب يميناً ويساراً والتنقل عبر شريط التبويبات السفلي باستخدام `ViewPager2`.
- **🏭 قراءة عنوان المصنع الحقيقي (Factory MAC)**:
  قراءة عنوان MAC الحقيقي والفيزيائي للعتاد مباشرة من الشريحة عبر استدعاءات الانعكاس (`WifiVendorHal.getStaFactoryMacAddress` / `getFactoryMacAddress`) وعرضه في الواجهة فورياً عبر قناة `YukiHookDataChannel`.

---

## التوافقية والمتطلبات

- **إصدارات أندرويد**: أندرويد 10، 11، 12، 12L، 13، 14، 15، وأندرويد 16 (`minSdk = 29`، `targetSdk = 37`).
- **البيئة المطلوبة**: جهاز به صلاحيات Root ومثبت عليه إطار عمل **LSPosed** (Zygisk أو Riru).
- **نطاق التفعيل المقترح**: إطار عمل النظام (**System Framework**) و الإعدادات (**Settings**).

---

## طريقة التثبيت والاستخدام

1. قم بتثبيت ملف الـ APK وافتح تطبيق إطار العمل (**LSPosed**).
2. قم بتفعيل الموديل وتأكد من تحديد النطاق: **إطار عمل النظام (System Framework)** و **الإعدادات (Settings)**.
3. **أعد تشغيل هاتفك** (خطوة إلزامية عند أول تثبيت فقط لتحميل الخطافات داخل سيرفر النظام).
4. افتح تطبيق **MAC Editor**:
   - أدخل عنوان MAC مخصص صالح (مثل `02:00:00:00:00:01`) أو اضغط على **"توليد MAC عشوائي"**.
   - اضغط على **"تطبيق عنوان MAC"** أو ببساطة قم بتفعيل مفتاح **"تجاوز عشوائية MAC"** ليتم التطبيق فورياً بدون نقرات إضافية!
   - في إعدادات شبكة الواي فاي على هاتفك، تأكد من ضبط الخصوصية على **"استخدام MAC عشوائي"**.
   - لتطبيق عنوان مخصص على نقطة الاتصال المحمولة، فعّل خيار **"تجاوز عنوان MAC لنقطة الاتصال (AP)"** من صفحة الإعدادات.

---

## البنية البرمجية والتنفيذ التقني

### هيكلية YukiHookAPI 1.3.2 الحديثة

يعتمد الموديل على التعليق التوضيحي `@InjectYukiHookWithXposed` مع معالج KSP الحديث (`ksp-xposed`) لتوليد أصول البداية وفئات فحص حالة الموديل تلقائياً بدون أخطاء.

### خطافات خادم النظام المتقدمة

- **نظام كشف ClassLoader متعدد المراحل**: يكتشف فئات الواي فاي عبر APEX `service-wifi.jar`، و `SystemServiceManager.loadClassFromLoader`، وخطافات تسجيل `ServiceManager`، وفئات عتاد المصنعين الخاصة.
- **خطف الموارد (Resources.getBoolean)**: خطف استدعاء `Resources.getBoolean(int)` لفرض تفعيل خواص العشوائية:
  - `config_wifi_connected_mac_randomization_supported`
  - `config_wifi_p2p_mac_randomization_supported`
  - `config_wifi_ap_mac_randomization_supported`
- **اتصال فوري بين العمليات (IPC)**: مزامنة الإعدادات في أجزاء من الثانية بين واجهة التطبيق وخادم النظام (`system_server`) باستخدام `YukiHookPrefsBridge` والبثوث الديناميكية المسجلة عبر `ContextCompat.RECEIVER_EXPORTED`.

---

## مطورو ومحافظو المشروع

- **المطورون الحاليون والتطويرات الشاملة**: [Rillwyn](https://github.com/Rillwyn) و [م. عمرو الديب (Eng. Amr Eldeeb)](https://github.com/engamreldeeb)
- **المشروع الأصلي**: [jqssun/android-mac-editor](https://github.com/jqssun/android-mac-editor)
- **الفكرة الأولى**: [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz)

---

## الترخيص

هذا المشروع مرخص بموجب [رخصة جنو العمومية الإصدار 3.0](LICENSE) (GPL-3.0). تم الاحتفاظ بجميع إشعارات حقوق النشر الأصلية.
