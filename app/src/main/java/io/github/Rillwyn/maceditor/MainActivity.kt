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
import io.github.Rillwyn.maceditor.databinding.ActivityMainBinding
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker
import io.github.Rillwyn.maceditor.utils.MacTextWatcher
import io.github.Rillwyn.maceditor.utils.MacUtils
import io.github.Rillwyn.maceditor.utils.PrefManager
import io.github.Rillwyn.maceditor.utils.XposedChecker
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var updatingUI = false

    private val macReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _refreshDeviceMac()
            _updateStatusCard()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // 从本地 SharedPreferences 读取语言
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

        PrefManager.loadPrefs { runOnUiThread { _refreshAll() } }
        _refreshAll()

        // AP 覆写开关监听
        binding.apMacOverrideSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setApMacOverride(checked)
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
        binding.hookSwitch.isChecked = PrefManager.isHookOn()
        binding.forceRandomizationSwitch.isChecked = PrefManager.isForceShowMacRandomization()
        binding.apMacOverrideSwitch.isChecked = PrefManager.isApMacOverride()
        val saved = PrefManager.getCustomMac()
        if (saved.isNotEmpty() && binding.edittextNewMac.text.isNullOrEmpty()) {
            binding.edittextNewMac.setText(saved)
        }
        updatingUI = false
    }

    private fun _isSystemServerHooked(): Boolean {
        return getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
            .getString("deviceMac", null) != null
    }

    private fun _updateStatusCard() {
        val enabled = XposedChecker.isEnabled()
        val hooked = enabled && _isSystemServerHooked()
        val hookOn = hooked && PrefManager.isHookOn()

        when {
            !enabled -> {
                binding.moduleStatusIcon.setImageResource(R.drawable.ic_disabled_24)
                binding.moduleStatus.text = getString(R.string.status_not_activated)
                binding.serviceStatus.text = getString(R.string.status_detail_not_activated)
            }
            !hooked -> {
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
                binding.serviceStatus.text = getString(R.string.status_detail_hook_on)
            }
        }
    }

    private fun _setupToggles() {
        binding.hookSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setHookState(checked)
            _updateStatusCard()
        }
        binding.forceRandomizationSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setForceShowMacRandomization(checked)
        }
    }

    private fun _refreshDeviceMac() {
        val localPrefs = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
        val mac = localPrefs.getString("deviceMac", null)
        binding.textviewDeviceMac.text = mac ?: getString(R.string.mac_not_set)
    }

    private fun _refreshActiveMac() {
        val saved = PrefManager.getCustomMac()
        binding.textviewCurrentMac.text = saved.ifEmpty { getString(R.string.mac_not_set) }
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
                    PrefManager.setCustomMac(mac)
                    binding.textviewCurrentMac.text = mac
                    _applyMac()
                }
            }
        }
    }

    private fun _applyMac() {
        sendBroadcast(Intent(WifiServiceHooker.ACTION_APPLY_MAC))
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