package io.github.Rillwyn.maceditor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.dataChannel
import io.github.Rillwyn.maceditor.databinding.FragmentHomeBinding
import io.github.Rillwyn.maceditor.hookers.WifiServiceHooker
import io.github.Rillwyn.maceditor.utils.MacTextWatcher
import io.github.Rillwyn.maceditor.utils.MacUtils
import io.github.Rillwyn.maceditor.utils.PrefManager

/**
 * 模块主控制台交互页面 Fragment。
 *
 * 核心交互职责：
 * 1. **模块状态与卡片监控**：实时展示 LSPosed 模块激活状态、Hooker 运行状况、物理出厂 MAC 与当前生效的活跃 MAC。
 * 2. **MAC 地址编辑与校验**：集成 [MacTextWatcher] 与 [MacUtils]，提供实时输入格式化、大写转换、单播规范校验与随机生成功能。
 * 3. **实时生效分发**：支持一键应用以及主开关切换时的零点击（Zero-Click）实时应用，通过系统级 IPC 广播即时同步 Wi-Fi HAL。
 * 4. **跨进程主动探测**：通过 [dataChannel] 主动向 `system_server` 请求物理真实 MAC，避免依赖被动网络广播导致的显示延迟。
 *
 * @sample
 * ```kotlin
 * // 作为 ViewPager2 适配器第 0 页直接挂载
 * val homeFragment = HomeFragment()
 * ```
 */
class HomeFragment : Fragment() {

    /** 视图绑定底层后备属性。 */
    private var _binding: FragmentHomeBinding? = null

    /** 获取有效的视图绑定对象。 */
    private val binding get() = _binding!!

    /** 标志位：在编程式刷新 UI 控件时阻断监听器循环触发。 */
    private var updatingUI = false

    /** 动态注册的广播接收器：用于接收由系统服务在检测到网络状态变化时广播的物理出厂 MAC 更新。 */
    private val macReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            _refreshDeviceMac()
            _updateStatusCard()
        }
    }

    /**
     * 构建 Fragment 根视图。
     *
     * @param inflater 布局填充器。
     * @param container 父容器。
     * @param savedInstanceState 状态恢复数据包。
     * @return 初始化的根视图。
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图构建完成后绑定用户交互监听器并执行初始状态同步。
     *
     * @param view 已构建完成的根视图。
     * @param savedInstanceState 状态恢复数据包。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _setupToggles()
        _setupMacCard()
        binding.footerNote.text = getString(
            R.string.footer_note,
            getString(R.string.force_mac_randomization_label)
        )

        _refreshAll()
    }

    /**
     * 页面可见生命周期回调：动态挂载出厂 MAC 接收器并主动向宿主请求出厂 MAC。
     */
    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            requireContext(),
            macReceiver,
            IntentFilter(MacBroadcastReceiver.ACTION_MAC_DETECTED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        _refreshAll()
        _requestSystemMac()
    }

    /**
     * 页面暂停生命周期回调：注销动态广播接收器以防内存泄露。
     */
    override fun onPause() {
        super.onPause()
        _binding?.let { requireContext().unregisterReceiver(macReceiver) }
    }

    /**
     * 释放 View Binding 引用。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 通过 [dataChannel] 向 `system_server` 宿主发送主动拉取真实出厂 MAC 的异步请求。
     */
    private fun _requestSystemMac() {
        val ctx = requireContext()
        runCatching {
            ctx.dataChannel("android").with {
                wait<String>("mac_result") { mac ->
                    activity?.runOnUiThread {
                        ctx.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putString("deviceMac", mac.uppercase()).apply()
                        _refreshDeviceMac()
                        _updateStatusCard()
                    }
                }
                put("mac_request", "true")
            }
        }.onFailure {
            // 模块未激活或数据通道不可用时静默容错
        }
    }

    /**
     * 刷新本页面所有卡片、开关与 MAC 输入框的展示数据。
     */
    private fun _refreshAll() {
        updatingUI = true
        _updateStatusCard()
        _refreshDeviceMac()
        _refreshActiveMac()
        binding.hookSwitch.isChecked = PrefManager.isHookOn(requireContext())
        val saved = PrefManager.getCustomMac(requireContext())
        if (saved.isNotEmpty() && binding.edittextNewMac.text.isNullOrEmpty()) {
            binding.edittextNewMac.setText(saved)
        }
        updatingUI = false
    }

    /**
     * 判断当前模块是否已在 LSPosed / Xposed 框架中成功激活。
     *
     * 采用双重检测策略：
     * 1. 优先调用 [YukiHookAPI.Status.isModuleActive]（由框架向模块进程直接注入）。
     * 2. 备用策略：检查本地是否已收到来自 `system_server` 的出厂 MAC 回包。
     *
     * @return 若模块处于激活运行状态则返回 `true`。
     */
    private fun _isModuleActive(): Boolean {
        if (YukiHookAPI.Status.isModuleActive) return true
        val localPrefs = context?.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val deviceMac = localPrefs?.getString("deviceMac", null)
        return !deviceMac.isNullOrEmpty()
    }

    /**
     * 根据模块激活状态与覆写主开关状态，动态更新状态卡片的图标、主标题与详细状态说明。
     */
    private fun _updateStatusCard() {
        val moduleActive = _isModuleActive()
        val hookOn = moduleActive && PrefManager.isHookOn(requireContext())

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
                val customMac = PrefManager.getCustomMac(requireContext())
                val systemMac = _getSystemMacDisplay()
                binding.serviceStatus.text = if (customMac.isNotEmpty()) {
                    getString(R.string.status_detail_hook_on_custom, customMac, systemMac)
                } else {
                    getString(R.string.status_detail_hook_on_system, systemMac)
                }
            }
        }
    }

    /**
     * 获取用于显示的物理出厂/系统 MAC 地址。
     *
     * @return 格式化的出厂 MAC 字符串；若未读取到则返回默认的占位文本。
     */
    private fun _getSystemMacDisplay(): String {
        val localPrefs = requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        return localPrefs.getString("deviceMac", null) ?: getString(R.string.mac_not_set)
    }

    /**
     * 绑定主开关交互逻辑，并在切换时触发实时应用广播与提示。
     */
    private fun _setupToggles() {
        binding.hookSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setHookState(requireContext(), checked)
            _updateStatusCard()
            _refreshActiveMac()
            val msgRes = if (checked) R.string.hook_enabled_instant else R.string.hook_disabled_instant
            Snackbar.make(binding.root, msgRes, Snackbar.LENGTH_SHORT)
                .setAction(R.string.reconnect_wifi_hint) {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
                .show()
        }
    }

    /**
     * 刷新“系统 MAC”卡片字段。
     */
    private fun _refreshDeviceMac() {
        val localPrefs = requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val mac = localPrefs.getString("deviceMac", null)
        binding.textviewDeviceMac.text = mac ?: getString(R.string.mac_not_set)
    }

    /**
     * 刷新“当前生效 MAC”卡片字段。
     */
    private fun _refreshActiveMac() {
        if (!PrefManager.isHookOn(requireContext())) {
            val localPrefs = requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val deviceMac = localPrefs.getString("deviceMac", null)
            binding.textviewCurrentMac.text = deviceMac ?: getString(R.string.mac_not_set)
            return
        }
        val saved = PrefManager.getCustomMac(requireContext())
        if (saved.isNotEmpty()) {
            binding.textviewCurrentMac.text = saved
        } else {
            val localPrefs = requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
            val deviceMac = localPrefs.getString("deviceMac", null)
            binding.textviewCurrentMac.text = deviceMac ?: getString(R.string.mac_not_set)
        }
    }

    /**
     * 初始化 MAC 地址输入框过滤器、文本监听器与随机生成/应用按钮点击事件。
     */
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
                    PrefManager.setCustomMac(requireContext(), mac)
                    binding.textviewCurrentMac.text = mac
                    _applyMac()
                }
            }
        }
    }

    /**
     * 向系统框架发送应用 MAC 广播，并弹出跳转 Wi-Fi 设置的提示。
     */
    private fun _applyMac() {
        val mac = PrefManager.getCustomMac(requireContext())
        requireContext().sendBroadcast(
            Intent(WifiServiceHooker.ACTION_APPLY_MAC)
                .putExtra(WifiServiceHooker.EXTRA_MAC, mac)
        )
        Snackbar.make(binding.root, R.string.mac_set_success, Snackbar.LENGTH_LONG)
            .setAction(R.string.open_wifi_settings) {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            .show()
    }

    /**
     * 显示标准 Material 风格错误对话框。
     *
     * @param msg 错误提示内容字符串。
     */
    private fun _showError(msg: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
