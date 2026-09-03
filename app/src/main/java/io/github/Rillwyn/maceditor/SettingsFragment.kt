package io.github.Rillwyn.maceditor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import io.github.Rillwyn.maceditor.databinding.FragmentSettingsBinding
import io.github.Rillwyn.maceditor.utils.PrefManager

/**
 * 模块参数与高级设置页面 Fragment。
 *
 * 核心功能：
 * 1. **多语言即时切换**：提供英语、简体中文、阿拉伯语三语行内单选组，切换时自动持久化配置并平滑重建 [MainActivity]，保持当前页面 Tab 索引不变。
 * 2. **强制 MAC 随机化支持**：控制底层 Hook 开关，使系统服务相信当前硬件与驱动支持全场景 MAC 随机化。
 * 3. **移动热点（AP 模式）MAC 覆写**：独立控制热点接口是否同步自定义 MAC，防止特定 OEM 设备在热点开启时因驱动限制导致崩溃。
 *
 * @sample
 * ```kotlin
 * // 作为 ViewPager2 适配器第 1 页直接挂载
 * val settingsFragment = SettingsFragment()
 * ```
 */
class SettingsFragment : Fragment() {

    /** 视图绑定底层后备属性。 */
    private var _binding: FragmentSettingsBinding? = null

    /** 获取有效的视图绑定对象。 */
    private val binding get() = _binding!!

    /** 标志位：用于在编程式更新控件选中状态时，屏蔽用户交互监听器的二次触发，防止无限回调。 */
    private var updatingUI = false

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
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
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
        _setupLanguageToggle()
        _setupSwitches()
        _refreshAll()
    }

    /**
     * 页面重回前台时的生命周期回调，重新从底层 SharedPreferences 读取最新配置状态。
     */
    override fun onResume() {
        super.onResume()
        _refreshAll()
    }

    /**
     * 释放 View Binding 引用以防内存泄漏。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 配置多语言单选按钮组交互逻辑。
     *
     * 监听语言按钮点击事件，写入所选语言代码（`"en"` / `"zh"` / `"ar"`），记录当前 Tab 索引为 1（设置页），
     * 并立即触发宿主 Activity 的 [android.app.Activity.recreate]，实现零黑屏原地热切换。
     */
    private fun _setupLanguageToggle() {
        binding.languageToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingUI || !isChecked) return@addOnButtonCheckedListener
            val lang = when (checkedId) {
                R.id.language_zh -> "zh"
                R.id.language_ar -> "ar"
                else -> "en"
            }
            // 记住当前 tab 索引（设置页 index = 1），Activity recreate 后由 MainActivity 恢复对应 Tab
            requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("language", lang)
                .putInt("savedTab", 1)
                .apply()
            requireActivity().recreate()
        }
    }

    /**
     * 配置设置项开关的变更监听事件，并在开关切换时通过 [PrefManager] 分发实时 IPC 更新及弹出即时提示。
     */
    private fun _setupSwitches() {
        binding.forceRandomizationSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setForceShowMacRandomization(requireContext(), checked)
            val msgRes = if (checked) R.string.status_activated else R.string.status_not_activated
            Snackbar.make(binding.root, msgRes, Snackbar.LENGTH_SHORT).show()
        }
        binding.apMacOverrideSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUI) return@setOnCheckedChangeListener
            PrefManager.setApMacOverride(requireContext(), checked)
            val msgRes = if (checked) R.string.ap_override_enabled_instant else R.string.ap_override_disabled_instant
            Snackbar.make(binding.root, msgRes, Snackbar.LENGTH_SHORT).show()
        }
    }

    /**
     * 从跨进程配置中心刷新本页面所有 UI 控件的显示状态。
     */
    private fun _refreshAll() {
        updatingUI = true
        _refreshLanguageToggle()
        binding.forceRandomizationSwitch.isChecked = PrefManager.isForceShowMacRandomization(requireContext())
        binding.apMacOverrideSwitch.isChecked = PrefManager.isApMacOverride(requireContext())
        updatingUI = false
    }

    /**
     * 读取当前应用或系统语言配置，并在语言切换组上正确高亮对应的选中按钮。
     */
    private fun _refreshLanguageToggle() {
        val prefs = requireContext().getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "") ?: ""
        val checkedId = when (currentLang) {
            "en" -> R.id.language_en
            "zh" -> R.id.language_zh
            "ar" -> R.id.language_ar
            else -> {
                val sysLang = resources.configuration.locales[0].language
                when {
                    sysLang.startsWith("zh") -> R.id.language_zh
                    sysLang.startsWith("ar") -> R.id.language_ar
                    else -> R.id.language_en
                }
            }
        }
        binding.languageToggle.check(checkedId)
    }
}
