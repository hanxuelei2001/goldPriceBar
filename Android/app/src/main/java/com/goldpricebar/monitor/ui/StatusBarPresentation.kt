package com.goldpricebar.monitor.ui

import androidx.annotation.ColorRes
import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.data.PriceInfo
import com.goldpricebar.monitor.data.PriceTrend
import com.goldpricebar.monitor.settings.PriceInput

/**
 * 状态栏文案：像网速指示器那样「箭头 + 数字」。
 *
 * - 赚了（高于成本价 / 当日上涨）：红色 + `↑`
 * - 亏了（低于成本价 / 当日下跌）：绿色 + `↓`
 * - 持平：中性色 + `→`
 */
object StatusBarPresentation {

    const val ARROW_RISE = "\u2191"
    const val ARROW_FALL = "\u2193"
    const val ARROW_NEUTRAL = "\u2192"

    fun arrow(trend: PriceTrend): String = when (trend) {
        PriceTrend.RISE -> ARROW_RISE
        PriceTrend.FALL -> ARROW_FALL
        PriceTrend.NEUTRAL -> ARROW_NEUTRAL
    }

    @ColorRes
    fun colorRes(trend: PriceTrend): Int = when (trend) {
        PriceTrend.RISE -> R.color.trend_rise
        PriceTrend.FALL -> R.color.trend_fall
        PriceTrend.NEUTRAL -> R.color.trend_neutral
    }

    /**
     * 状态栏主文案。
     *
     * @param providerShortName 传入时前缀数据源简称，例如 `浙商 ↑ 1049.59`
     */
    fun statusText(price: Double, trend: PriceTrend, providerShortName: String? = null): String {
        val priceText = PriceInput.formatPrice(price)
        val core = "${arrow(trend)} $priceText"
        return if (providerShortName.isNullOrBlank()) core else "$providerShortName $core"
    }

    /** 涨跌额与涨跌幅，例如 `+4.22  +0.40%`。 */
    fun changeText(info: PriceInfo): String = "${info.changeAmount}  ${info.changePercent}"

    /** 数据源简称 + 全称，用于通知标题与详情页。 */
    fun providerLabel(displayName: String, shortName: String): String = "$shortName · $displayName"
}
