package io.github.Rillwyn.maceditor.utils

import kotlin.random.Random

/**
 * IEEE 802 媒体访问控制（MAC）地址工具套件。
 *
 * 提供符合 IEEE 802 规范的 MAC 地址有效性校验、加密级伪随机单播地址生成以及自非结构化文本中智能提取地址的核心算法。
 *
 * @sample
 * ```kotlin
 * val result = MacUtils.validate("02:00:00:00:00:01")
 * if (result == MacUtils.ValidationResult.VALID) {
 *     // 应用合规的单播 MAC 地址
 * }
 * ```
 */
object MacUtils {

    /**
     * MAC 地址句法与语义校验结果枚举。
     */
    enum class ValidationResult {
        /** 地址格式正确，首字节最低位为 0（单播），且非全零保留地址。 */
        VALID,

        /** 字符串长度不等于 17 或不满足冒号分隔的十六进制格式 `XX:XX:XX:XX:XX:XX`。 */
        BAD_LENGTH,

        /** 地址为 `00:00:00:00:00:00`，属于不可用于物理/虚拟接口的保留全零地址。 */
        ALL_ZEROS,

        /** 首字节最低有效位（I/G 位）为 1（即奇数），属于组播/广播地址，不可作为网络接口单播 MAC。 */
        ODD_FIRST_OCTET
    }

    /**
     * 校验指定的 MAC 地址字符串是否符合 IEEE 802 单播地址规范。
     *
     * 校验规则包含：
     * 1. 严格长度与正则表达式匹配（17 字符大写十六进制冒号分隔）。
     * 2. 排除 `00:00:00:00:00:00` 保留地址。
     * 3. 校验首字节 I/G（Individual/Group）位，必须为偶数（单播地址）。
     *
     * @param mac 待校验的 MAC 地址字符串。必须为大写字母构成的候选地址。
     * @return [ValidationResult] 枚举值，表示校验通过（[ValidationResult.VALID]）或具体的失败原因。
     *
     * @sample
     * ```kotlin
     * val status = MacUtils.validate("02:1A:2B:3C:4D:5E")
     * check(status == MacUtils.ValidationResult.VALID)
     * ```
     */
    fun validate(mac: String): ValidationResult {
        if (mac.length != 17) return ValidationResult.BAD_LENGTH
        if (!mac.matches(Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$"))) return ValidationResult.BAD_LENGTH
        if (mac == "00:00:00:00:00:00") return ValidationResult.ALL_ZEROS
        val firstOctet = mac.substring(0, 2).toInt(16)
        if (firstOctet % 2 != 0) return ValidationResult.ODD_FIRST_OCTET
        return ValidationResult.VALID
    }

    /**
     * 生成符合 IEEE 802 规范的 48 位伪随机单播 MAC 地址字符串。
     *
     * 生成算法保障：
     * - 清除首字节第 0 位（I/G 位置 0，确保单播属性）。
     * - 若首字节为 0 则强制修正为 2（本地管理单播地址，避免全零前缀）。
     * - 末字节为 0 时修正为 1，彻底消除全零地址碰撞概率。
     *
     * @return 标准 17 字符大写冒号分隔的 MAC 地址字符串（例如 `"02:3F:8A:12:BC:90"`）。
     *
     * @sample
     * ```kotlin
     * val randomMac = MacUtils.generateRandom()
     * println("Generated MAC: $randomMac")
     * ```
     */
    fun generateRandom(): String {
        val octets = ByteArray(6).also { Random.nextBytes(it) }
        // 抹除首字节 I/G 位保证单播属性；若清零后为 0x00 则置为 0x02 以符合 IEEE LAA 惯例
        var first = octets[0].toInt() and 0xFE
        if (first == 0) first = 2
        octets[0] = first.toByte()
        // 保证末尾字节非零以防极端全零碰撞
        if (octets[5].toInt() and 0xFF == 0) octets[5] = 1
        return octets.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    /**
     * 从任意文本流（如 `/efs/wifi/.mac.info`、`wlan_mac.bin` 或系统属性转储）中解析并提取首个合法的单播 MAC 地址。
     *
     * 支持匹配格式：
     * - 冒号或连字符分隔格式：`XX:XX:XX:XX:XX:XX` 或 `XX-XX-XX-XX-XX-XX`
     * - 连续 12 位十六进制无分隔格式：`XXXXXXXXXXXX`
     *
     * @param text 包含潜在 MAC 地址特征的原始文本内容。
     * @return 规范化的大写冒号分隔 MAC 字符串；若文本中未发现合法单播地址则返回 `null`。
     *
     * @sample
     * ```kotlin
     * val extracted = MacUtils.extractMac("Factory STA MAC: 4a-5b-6c-7d-8e-9f")
     * println(extracted) // 输出: "4A:5B:6C:7D:8E:9F"
     * ```
     */
    fun extractMac(text: String): String? {
        val colonMatch = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})").find(text)
        if (colonMatch != null) {
            val candidate = colonMatch.value.replace('-', ':').uppercase()
            if (validate(candidate) == ValidationResult.VALID) return candidate
        }
        val hexMatch = Regex("([0-9A-Fa-f]{12})").find(text)
        if (hexMatch != null) {
            val candidate = hexMatch.value.chunked(2).joinToString(":").uppercase()
            if (validate(candidate) == ValidationResult.VALID) return candidate
        }
        return null
    }
}
