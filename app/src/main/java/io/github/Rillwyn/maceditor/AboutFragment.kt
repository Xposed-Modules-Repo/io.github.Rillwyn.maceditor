package io.github.Rillwyn.maceditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.github.Rillwyn.maceditor.databinding.FragmentAboutBinding

/**
 * 关于信息呈现页面 Fragment。
 *
 * 负责展示模块的应用名称、版本号、版权说明、当前项目与原项目 GitHub 仓库链接以及维护者信息。
 * 遵循 View Binding 规范管理视图生命周期，防止 Fragment 视图解绑后的内存泄漏。
 *
 * @sample
 * ```kotlin
 * // 作为 ViewPager2 适配器的页面之一直接装载
 * val fragment = AboutFragment()
 * ```
 */
class AboutFragment : Fragment() {

    /** 视图绑定底层后备属性。 */
    private var _binding: FragmentAboutBinding? = null

    /** 获取当前有效的视图绑定对象；在 [onCreateView] 与 [onDestroyView] 之间安全访问。 */
    private val binding get() = _binding!!

    /**
     * 实例化并构建 Fragment 视图层次结构。
     *
     * @param inflater 布局填充器。
     * @param container 父容器视图组。
     * @param savedInstanceState 状态恢复数据包。
     * @return 初始化的根视图。
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * 视图构建完成后的初始化配置与交互事件绑定。
     *
     * 从 [BuildConfig.VERSION_NAME] 动态注入版本号，并绑定项目链接的外部浏览器跳转行为。
     *
     * @param view 已经构建完成的根视图。
     * @param savedInstanceState 状态恢复数据包。
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.aboutVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)

        binding.thisProjectUrl.text = getString(R.string.about_this_project_url)
        binding.originalProjectUrl.text = getString(R.string.about_original_project_url)

        binding.linkThisProject.setOnClickListener {
            openUrl(getString(R.string.about_this_project_url))
        }
        binding.linkOriginalProject.setOnClickListener {
            openUrl(getString(R.string.about_original_project_url))
        }
    }

    /**
     * 视图销毁生命周期回调，在此释放 View Binding 引用以防内存泄露。
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 使用隐式 Intent 尝试通过系统默认浏览器打开指定 URL。
     *
     * 内部包装异常捕获，在没有可用浏览器 Activity 的异常场景下静默处理，避免产生崩溃。
     *
     * @param url 待打开的 HTTP/HTTPS 链接地址。
     */
    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
