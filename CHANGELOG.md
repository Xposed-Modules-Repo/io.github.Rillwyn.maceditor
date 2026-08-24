# Changelog

This file documents all modifications made to the original [MAC Editor](https://github.com/jqssun/android-mac-editor) project by [jqssun](https://github.com/jqssun).

All changes are made under the terms of the original license (GPL-3.0), and all original copyright notices are retained.

---

## [0.0.8] - 2026-08-24

### Added
- **Multi‑language UI**: Support for English and Chinese, switchable via the app menu. The UI refreshes instantly after selection.
- **AP MAC override toggle**: A dedicated switch to control whether the custom MAC address is applied to the mobile hotspot (AP) interface. **Default is OFF**, preventing hotspot startup failures on devices that reject MAC changes on `wlan2`.

### Changed
- **Hotspot compatibility fix**: Replaced method‑based detection with interface‑name‑based detection (`wlan2`) to accurately identify AP calls, ensuring the AP override switch works reliably.
- **Language storage optimization**: Language preference is now stored in local `SharedPreferences` instead of remote Xposed prefs, fixing the previous issue where language switching had no effect.

### Technical Details
- Modified `WifiServiceHooker.kt`: Uses `ifaceName` to determine if the call targets AP mode; checks `apMacOverride` preference before replacing MAC.
- Modified `MainActivity.kt`: Reads language setting in `attachBaseContext` from local prefs, uses `recreate()` to refresh UI.
- Added `values-zh/strings.xml` for Chinese strings.
- Updated `activity_main.xml`: Added AP MAC override card.
- Bumped version to `0.0.8` in `app/build.gradle.kts`.

### Documentation
- Updated `README.md` and added `README_CN.md` to describe new features and usage.
- Created `CHANGELOG.md` and `CHANGELOG_CN.md` to record all modifications.

---

## Original Releases (upstream)

The original project was based on [David Berdik](https://f-droid.org/repo/com.berdik.macsposed_6_src.tar.gz)'s `MacSposed` implementation, later improved and open‑sourced by [jqssun](https://github.com/jqssun).

Original features included:
- Hooking `setStaMacAddress` and `setApMacAddress`.
- Forcing MAC randomization resources.
- Basic UI for setting a custom MAC address.