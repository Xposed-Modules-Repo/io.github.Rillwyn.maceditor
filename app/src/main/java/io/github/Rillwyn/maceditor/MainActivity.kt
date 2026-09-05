package io.github.Rillwyn.maceditor

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import io.github.Rillwyn.maceditor.databinding.ActivityMainBinding
import java.util.Locale

/**
 * 应用程序主 Activity 界面容器。
 *
 * 采用 Material 3 架构设计：
 * 1. **多语言与 RTL 动态注入**：在 [attachBaseContext] 阶段动态覆盖配置上下文，无缝支持英语、简体中文与阿拉伯语（从右至左镜像布局）。
 * 2. **三页面容器体系**：通过 [androidx.viewpager2.widget.ViewPager2] 承载 [HomeFragment]、[SettingsFragment] 与 [AboutFragment]，支持左右平滑滑动手势。
 * 3. **双向导航联动**：实现 [com.google.android.material.bottomnavigation.BottomNavigationView] 与 ViewPager2 的状态同步与顶部工具栏标题自适应联动。
 * 4. **页面状态保全**：在设置页切换语言导致 Activity 重建时，自动持久化并还原 Tab 索引，带来连贯的用户体验。
 *
 * @sample
 * ```xml
 * <!-- AndroidManifest.xml 启动配置 -->
 * <activity
 *     android:name=".MainActivity"
 *     android:exported="true">
 *     <intent-filter>
 *         <action android:name="android.intent.action.MAIN" />
 *         <category android:name="android.intent.category.LAUNCHER" />
 *     </intent-filter>
 * </activity>
 * ```
 */
class MainActivity : AppCompatActivity() {

    /** 视图绑定对象。 */
    private lateinit var binding: ActivityMainBinding

    /**
     * 在基础上下文挂载前注入自定义语言与布局方向配置。
     *
     * @param newBase 原始基础上下文对象。
     */
    override fun attachBaseContext(newBase: Context) {
        // 读取持久化语言配置（支持 en: 英文, zh: 简体中文, ar: 阿拉伯语 RTL 镜像布局）
        val prefs = newBase.getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "") ?: ""
        val locale = if (lang.isNotEmpty()) Locale.forLanguageTag(lang) else Locale.getDefault()
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    /**
     * Activity 创建生命周期回调：初始化视图、绑定 ViewPager2 与导航监听器。
     *
     * @param savedInstanceState 状态恢复数据包。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = MainPagerAdapter(this)
        // 限制离屏缓存页数，兼顾流畅滑动与低内存占用
        binding.viewPager.offscreenPageLimit = 1

        // 底部导航点击与 ViewPager2 页面切换联动
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.viewPager.setCurrentItem(0, false)
                R.id.nav_settings -> binding.viewPager.setCurrentItem(1, false)
                R.id.nav_about -> binding.viewPager.setCurrentItem(2, false)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        // ViewPager2 滑动监听与底部导航高亮及工具栏标题同步
        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                _syncNavSelection(position)
                _updateToolbarTitle(position)
            }
        })

        // 恢复语言切换前的所在页面 Tab（由设置页切换语言写入 savedTab）
        val savedTab = getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
            .getInt("savedTab", 0)
        _restoreTab(savedTab)
    }

    /**
     * 恢复指定的 Tab 页面。
     *
     * @param savedTab 目标页面索引（0: 主页, 1: 设置, 2: 关于）。
     */
    private fun _restoreTab(savedTab: Int) {
        if (savedTab in 0..2) {
            binding.viewPager.setCurrentItem(savedTab, false)
        }
    }

    /**
     * 同步底部导航栏选中的菜单项并记录当前页面索引。
     *
     * @param position 当前活动的 ViewPager2 页面索引。
     */
    private fun _syncNavSelection(position: Int) {
        val menuItem = when (position) {
            0 -> R.id.nav_home
            1 -> R.id.nav_settings
            else -> R.id.nav_about
        }
        binding.bottomNav.menu.findItem(menuItem)?.isChecked = true
        getSharedPreferences(MacBroadcastReceiver.PREFS_NAME, MODE_PRIVATE)
            .edit().putInt("savedTab", position).apply()
    }

    /**
     * 根据当前页面索引动态更新顶部 MaterialToolbar 标题文本。
     *
     * @param position 当前活动的页面索引。
     */
    private fun _updateToolbarTitle(position: Int) {
        val title = when (position) {
            1 -> getString(R.string.toolbar_settings)
            2 -> getString(R.string.toolbar_about)
            else -> getString(R.string.app_name)
        }
        binding.toolbar.title = title
    }

    /**
     * 主界面三页面 Fragment 状态适配器。
     *
     * @param activity 宿主 [AppCompatActivity] 实例。
     */
    inner class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

        /** 返回页面总数量（固定为 3）。 */
        override fun getItemCount(): Int = 3

        /**
         * 根据页面索引实例化对应 Fragment。
         *
         * @param position 目标位置（0: [HomeFragment], 1: [SettingsFragment], 2: [AboutFragment]）。
         * @return 初始化的目标 [Fragment] 实例。
         */
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                1 -> SettingsFragment()
                2 -> AboutFragment()
                else -> HomeFragment()
            }
        }
    }
}
