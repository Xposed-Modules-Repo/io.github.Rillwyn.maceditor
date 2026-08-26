package io.github.Rillwyn.maceditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.dataChannel
import io.github.Rillwyn.maceditor.databinding.ActivityMainBinding
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker
import io.github.Rillwyn.maceditor.utils.MacTextWatcher
import io.github.Rillwyn.maceditor.utils.MacUtils
import io.github.Rillwyn.maceditor.utils.PrefManager
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var updatingUI = false

    // 广播接收器，用于接收系统 MAC 更新（仅用于 UI 展示，不作为激活状态依据）
    private val macReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _refreshDeviceMac()
            _updateStatusCard()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 语言处理（保持原逻辑，使用模块自身本地缓存）
        val prefs = newBase.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "") ?: ""
        val locale = if (lang.isNotEmpty()) Locale(lang) else Locale.getDefault()
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        _setupToggles()
        _setupMacCard()
        binding.footerNote.text = getString(R.string.footer_note, getString(R.string.force_mac_randomization_label))

        // 偏好立即可用，无需异步绑定
        _refreshAll()

        // AP 覆写开关监听
        binding.apMacOverrideSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setApMacOverride(this, checked)
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            macReceiver,
            IntentFilter(MacBroadcastReceiver.ACTION_MAC_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        _refreshAll()
        // 通过 YukiHookDataChannel 主动拉取系统 MAC（重启后无需等待 WiFi 广播即可显示）
        _requestSystemMac()
    }

    /**
     * 通过 YukiHookDataChannel 向 system_server 主动请求当前系统 MAC：
     * 发送 mac_request，等待 mac_result 回包后更新“系统 MAC”显示。
     * 模块未激活/通道不可用时静默忽略（不影响其它功能）。
     */
    private fun _requestSystemMac() {
        runCatching {
            dataChannel("android").with {
                wait<String>("mac_result") { mac ->
                    runOnUiThread {
                        getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
                            .edit().putString("deviceMac", mac.uppercase()).apply()
                        _refreshDeviceMac()
                        _updateStatusCard()
                    }
                }
                put("mac_request", "true")
            }
        }.onFailure { /* 数据通道不可用（如模块未激活），忽略 */ }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(macReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.language_select)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            showLanguageDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showLanguageDialog() {
        val prefs = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "") ?: ""
        val items = arrayOf(getString(R.string.language_english), getString(R.string.language_chinese))
        val checkedItem = when (currentLang) {
            "en" -> 0
            "zh" -> 1
            else -> {
                val sysLang = resources.configuration.locales[0].language
                if (sysLang.startsWith("zh")) 1 else 0
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language_select)
            .setSingleChoiceItems(items, checkedItem) { _, which ->
                val lang = when (which) {
                    0 -> "en"
                    1 -> "zh"
                    else -> ""
                }
                prefs.edit().putString("language", lang).apply()
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun _refreshAll() {
        updatingUI = true
        _updateStatusCard()
        _refreshDeviceMac()
        _refreshActiveMac()
        binding.hookSwitch.isChecked = PrefManager.isHookOn(this)
        binding.forceRandomizationSwitch.isChecked = PrefManager.isForceShowMacRandomization(this)
        binding.apMacOverrideSwitch.isChecked = PrefManager.isApMacOverride(this)
        val saved = PrefManager.getCustomMac(this)
        if (saved.isNotEmpty() && binding.edittextNewMac.text.isNullOrEmpty()) {
            binding.edittextNewMac.setText(saved)
        }
        updatingUI = false
    }

    // 判断模块是否已在 Xposed/LSPosed 中激活：
    // 使用 YukiHookAPI 的状态检测（LSPosed 自动注入模块自身进程），
    // 不再依赖“是否收到系统广播”这种瞬态信号，重启后立即显示正确状态。
    private fun _isModuleActive(): Boolean {
        return YukiHookAPI.Status.isModuleActive
    }

    private fun _updateStatusCard() {
        val moduleActive = _isModuleActive()
        val hookOn = moduleActive && PrefManager.isHookOn(this)

        when {
            !moduleActive -> {
                binding.moduleStatusIcon.setImageResource(R.drawable.ic_error_24)
                binding.moduleStatus.text = getString(R.string.status_inactive)
                binding.serviceStatus.text = getString(R.string.status_detail_inactive)
            }
            !hookOn -> {
                binding.moduleStatusIcon.setImageResource(R.drawable.ic_warning_24)
                binding.moduleStatus.text = getString(R.string.status_activated)
                binding.serviceStatus.text = getString(R.string.status_detail_hook_off)
            }
            else -> {
                binding.moduleStatusIcon.setImageResource(R.drawable.ic_baseline_router_24)
                binding.moduleStatus.text = getString(R.string.status_activated)
                // 副标题：反映当前实际使用的 MAC（自定义优先，否则显示系统 MAC）
                val customMac = PrefManager.getCustomMac(this)
                val systemMac = _getSystemMacDisplay()
                binding.serviceStatus.text = if (customMac.isNotEmpty()) {
                    getString(R.string.status_detail_hook_on_custom, customMac, systemMac)
                } else {
                    getString(R.string.status_detail_hook_on_system, systemMac)
                }
            }
        }
    }

    /** 读取本地缓存的系统 MAC（出厂 MAC，由主动拉取/广播更新），不可用返回“未知” */
    private fun _getSystemMacDisplay(): String {
        val localPrefs = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
        return localPrefs.getString("deviceMac", null) ?: getString(R.string.mac_not_set)
    }

    private fun _setupToggles() {
        binding.hookSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setHookState(this, checked)
            _updateStatusCard()
        }
        binding.forceRandomizationSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setForceShowMacRandomization(this, checked)
        }
    }

    private fun _refreshDeviceMac() {
        val localPrefs = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
        val mac = localPrefs.getString("deviceMac", null)
        binding.textviewDeviceMac.text = mac ?: getString(R.string.mac_not_set)
    }

    private fun _refreshActiveMac() {
        val saved = PrefManager.getCustomMac(this)
        if (saved.isNotEmpty()) {
            binding.textviewCurrentMac.text = saved
        } else {
            // 未设置自定义 MAC 时，显示系统 MAC（本地缓存），避免显示“未知”
            val localPrefs = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
            val deviceMac = localPrefs.getString("deviceMac", null)
            binding.textviewCurrentMac.text = deviceMac ?: getString(R.string.mac_not_set)
        }
    }

    private fun _setupMacCard() {
        val editText = binding.edittextNewMac
        editText.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(17))
        editText.addTextChangedListener(MacTextWatcher())

        binding.btnGenerateMac.setOnClickListener {
            editText.setText(MacUtils.generateRandom())
        }

        binding.btnSetMac.setOnClickListener {
            val mac = editText.text.toString().uppercase()
            when (MacUtils.validate(mac)) {
                MacUtils.ValidationResult.BAD_LENGTH ->
                    _showError(getString(R.string.error_bad_length))
                MacUtils.ValidationResult.ALL_ZEROS ->
                    _showError(getString(R.string.error_all_zeros))
                MacUtils.ValidationResult.ODD_FIRST_OCTET ->
                    _showError(getString(R.string.error_odd_first_octet))
                MacUtils.ValidationResult.VALID -> {
                    PrefManager.setCustomMac(this, mac)
                    binding.textviewCurrentMac.text = mac
                    _applyMac()
                }
            }
        }
    }

    private fun _applyMac() {
        // 广播携带目标 MAC（由 system_server 接收并立即应用，
        // 不依赖跨进程 prefs 读取，确保首次安装后第一次点击即生效）
        val mac = PrefManager.getCustomMac(this)
        sendBroadcast(
            Intent(WifiServiceHooker.ACTION_APPLY_MAC)
                .putExtra(WifiServiceHooker.EXTRA_MAC, mac)
        )
        Snackbar.make(binding.root, R.string.mac_set_success, Snackbar.LENGTH_LONG)
            .setAction(R.string.open_wifi_settings) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .show()
    }

    private fun _showError(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
