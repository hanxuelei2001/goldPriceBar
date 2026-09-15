package com.goldpricebar.monitor.settings

import java.util.Locale

/** 价格输入解析与格式化：纯函数，供界面与单元测试共用。 */
object PriceInput {

    /** 解析用户输入的成本价 / 提醒价，非法或非正数返回 `null`。 */
    fun parsePrice(raw: String?): Double? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val value = text.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        return value
    }

    /** 统一保留两位小数，例如 `1049.6` → `1049.60`。 */
    fun formatPrice(value: Double): String =
        if (value.isFinite()) String.format(Locale.US, "%.2f", value) else "0.00"

    /** 涨跌幅同样按截断保留两位小数。 */
    fun formatPercentFromFraction(fraction: Double): String {
        if (!fraction.isFinite()) return "0.00%"
        val truncated = (fraction * 100.0 * 100.0).toLong().toDouble() / 100.0
        return String.format(Locale.US, "%.2f%%", truncated)
    }
}
