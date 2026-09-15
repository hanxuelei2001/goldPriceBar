package com.goldpricebar.monitor.ui

import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.data.PriceInfo
import com.goldpricebar.monitor.data.PriceTrend
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 状态栏文案：「红色上升表示赚了，绿色下降表示亏损」的具体落地。
 */
class StatusBarPresentationTest {

    @Test
    fun `rise shows red up arrow`() {
        assertEquals("\u2191", StatusBarPresentation.arrow(PriceTrend.RISE))
        assertEquals(R.color.trend_rise, StatusBarPresentation.colorRes(PriceTrend.RISE))
        assertEquals("\u2191 1049.59", StatusBarPresentation.statusText(1049.59, PriceTrend.RISE))
    }

    @Test
    fun `fall shows green down arrow`() {
        assertEquals("\u2193", StatusBarPresentation.arrow(PriceTrend.FALL))
        assertEquals(R.color.trend_fall, StatusBarPresentation.colorRes(PriceTrend.FALL))
        assertEquals("\u2193 1045.37", StatusBarPresentation.statusText(1045.37, PriceTrend.FALL))
    }

    @Test
    fun `neutral shows neutral arrow`() {
        assertEquals("\u2192", StatusBarPresentation.arrow(PriceTrend.NEUTRAL))
        assertEquals(R.color.trend_neutral, StatusBarPresentation.colorRes(PriceTrend.NEUTRAL))
    }

    @Test
    fun `provider short name can be prefixed`() {
        assertEquals(
            "浙商 \u2191 1049.59",
            StatusBarPresentation.statusText(1049.59, PriceTrend.RISE, "浙商"),
        )
        assertEquals(
            "\u2191 1049.59",
            StatusBarPresentation.statusText(1049.59, PriceTrend.RISE, ""),
        )
    }

    @Test
    fun `price is always formatted with two decimals`() {
        assertEquals("\u2191 0.00", StatusBarPresentation.statusText(0.0, PriceTrend.RISE))
        assertEquals("\u2191 1050.00", StatusBarPresentation.statusText(1050.0, PriceTrend.RISE))
        assertEquals("\u2191 1049.60", StatusBarPresentation.statusText(1049.6, PriceTrend.RISE))
    }

    @Test
    fun `change text joins amount and percent`() {
        val info = PriceInfo(
            price = 1049.59,
            changeAmount = "+4.22",
            changePercent = "+0.40%",
            isNegative = false,
        )
        assertEquals("+4.22  +0.40%", StatusBarPresentation.changeText(info))
    }

    @Test
    fun `provider label combines short and display names`() {
        assertEquals("工银 · 工商积存金", StatusBarPresentation.providerLabel("工商积存金", "工银"))
    }
}
