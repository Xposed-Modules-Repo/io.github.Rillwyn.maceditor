package io.github.Rillwyn.maceditor.utils

import android.text.Editable
import android.text.TextWatcher

/**
 * MAC 地址实时输入格式化监听器。
 *
 * 继承自 [TextWatcher]，用于在用户输入 MAC 地址字符时自动剔除非法格式、转换为大写并动态插入冒号分隔符，
 * 将输入序列即时格式化为标准的 `XX:XX:XX:XX:XX:XX` 形式。内置重入锁标志位以杜绝递归触发 [afterTextChanged]。
 *
 * @sample
 * ```kotlin
 * val editText = findViewById<EditText>(R.id.mac_input)
 * editText.addTextChangedListener(MacTextWatcher())
 * ```
 */
class MacTextWatcher : TextWatcher {

    /** 防重入标志位，用于在修改 [Editable] 缓冲区时防止二次递归调用。 */
    private var editing = false

    /**
     * 文本即将发生改变前的回调，此处无需特殊处理。
     *
     * @param s 改变前的字符序列。
     * @param start 发生改变的起始索引。
     * @param count 即将被替换的字符数量。
     * @param after 即将替换为的新字符数量。
     */
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    /**
     * 文本正在发生改变时的回调，此处无需特殊处理。
     *
     * @param s 当前文本序列。
     * @param start 改变发生的起始索引。
     * @param before 被替换的原字符数量。
     * @param count 新插入的字符数量。
     */
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    /**
     * 文本改变完成后的回调处理。
     *
     * 提取当前输入流中的有效十六进制字符，并每隔 2 个字符插入一个冒号 `:` 分隔符，最后写回 [Editable]。
     *
     * @param s 可编辑文本缓冲区。如果为 `null` 或处于重入状态则直接返回。
     */
    override fun afterTextChanged(s: Editable?) {
        if (editing || s == null) return
        editing = true

        // 剥离现有冒号并统一大写化，重新按双字符分组构筑标准 MAC 表达形式
        val raw = s.toString().replace(":", "").uppercase()
        val sb = StringBuilder()
        for (i in raw.indices) {
            if (i > 0 && i % 2 == 0) sb.append(':')
            sb.append(raw[i])
        }
        val formatted = sb.toString()
        if (formatted != s.toString()) {
            s.replace(0, s.length, formatted)
        }

        editing = false
    }
}
