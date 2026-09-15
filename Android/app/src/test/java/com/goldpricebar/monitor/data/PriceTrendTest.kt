package com.goldpricebar.monitor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceTrendTest {

    @Test
    fun `cost price takes precedence over daily direction`() {
        // 当日下跌，但价格仍在成本价之上 → 仍然是「赚了」的红色
        assertEquals(
            PriceTrend.RISE,
            PriceTrend.from(price = 1050.0, costPrice = 1000.0, isNegative = true),
        )
        // 当日上涨，但价格已跌破成本价 → 「亏了」的绿色
        assertEquals(
            PriceTrend.FALL,
            PriceTrend.from(price = 950.0, costPrice = 1000.0, isNegative = false),
        )
    }

    @Test
    fun `equal to cost price is neutral`() {
        assertEquals(
            PriceTrend.NEUTRAL,
            PriceTrend.from(price = 1000.0, costPrice = 1000.0, isNegative = false),
        )
    }

    @Test
    fun `falls back to daily direction when cost price is missing or invalid`() {
        assertEquals(PriceTrend.RISE, PriceTrend.from(1000.0, null, false))
        assertEquals(PriceTrend.FALL, PriceTrend.from(1000.0, null, true))
        assertEquals(PriceTrend.NEUTRAL, PriceTrend.from(1000.0, null, null))

        assertEquals(PriceTrend.RISE, PriceTrend.from(1000.0, 0.0, false))
        assertEquals(PriceTrend.FALL, PriceTrend.from(1000.0, -5.0, true))
        assertEquals(PriceTrend.RISE, PriceTrend.from(1000.0, Double.NaN, false))
    }

    @Test
    fun `falls back when the current price is not usable yet`() {
        assertEquals(PriceTrend.FALL, PriceTrend.from(0.0, 1000.0, true))
        assertEquals(PriceTrend.RISE, PriceTrend.from(Double.NaN, 1000.0, false))
    }

    @Test
    fun `direction from flag`() {
        assertEquals(PriceTrend.FALL, PriceTrend.from(true))
        assertEquals(PriceTrend.RISE, PriceTrend.from(false))
        assertEquals(PriceTrend.NEUTRAL, PriceTrend.from(null))
    }
}
