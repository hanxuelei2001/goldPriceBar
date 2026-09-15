package com.goldpricebar.monitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PriceRepositoryTest {

    @Before
    fun reset() {
        PriceRepository.reset()
        PriceRepository.setProvider(GoldProvider.ZHE_SHANG)
    }

    @Test
    fun `update quote stores price trend and timestamp`() {
        val info = PriceInfo(1049.59, "+4.22", "+0.40%", false)

        PriceRepository.updateQuote(
            provider = GoldProvider.ZHE_SHANG,
            priceInfo = info,
            trend = PriceTrend.RISE,
            timestamp = 1_700_000_000_000L,
            failed = false,
        )

        val state = PriceRepository.current
        assertEquals(1049.59, state.priceInfo.price, 0.0001)
        assertEquals(PriceTrend.RISE, state.trend)
        assertEquals(1_700_000_000_000L, state.lastUpdateAt)
        assertFalse(state.lastRequestFailed)
    }

    @Test
    fun `failed quote keeps the previous price and timestamp`() {
        PriceRepository.updateQuote(
            provider = GoldProvider.ZHE_SHANG,
            priceInfo = PriceInfo(1049.59, "+4.22", "+0.40%", false),
            trend = PriceTrend.RISE,
            timestamp = 1_700_000_000_000L,
            failed = false,
        )

        PriceRepository.markRequestFailed()

        val state = PriceRepository.current
        assertEquals(1049.59, state.priceInfo.price, 0.0001)
        assertEquals(1_700_000_000_000L, state.lastUpdateAt)
        assertTrue(state.lastRequestFailed)
    }

    @Test
    fun `recompute trend applies the new cost price immediately`() {
        PriceRepository.updateQuote(
            provider = GoldProvider.ZHE_SHANG,
            priceInfo = PriceInfo(1049.59, "+4.22", "+0.40%", false),
            trend = PriceTrend.RISE,
            timestamp = 1L,
            failed = false,
        )

        PriceRepository.recomputeTrend(costPrice = 1100.0)

        assertEquals(PriceTrend.FALL, PriceRepository.current.trend)
    }

    @Test
    fun `reset clears quote but keeps provider and monitoring flag`() {
        PriceRepository.setProvider(GoldProvider.GONG_SHANG)
        PriceRepository.setMonitoring(true)
        PriceRepository.updateQuote(
            provider = GoldProvider.GONG_SHANG,
            priceInfo = PriceInfo(1049.59, "+4.22", "+0.40%", false),
            trend = PriceTrend.RISE,
            timestamp = 1L,
            failed = false,
        )

        PriceRepository.reset()

        val state = PriceRepository.current
        assertEquals(GoldProvider.GONG_SHANG, state.provider)
        assertTrue(state.isMonitoring)
        assertEquals(0.0, state.priceInfo.price, 0.0001)
        assertEquals(0L, state.lastUpdateAt)
    }
}
