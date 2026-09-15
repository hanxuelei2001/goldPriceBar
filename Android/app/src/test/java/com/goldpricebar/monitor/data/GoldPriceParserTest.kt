package com.goldpricebar.monitor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldPriceParserTest {

    // MARK: - 京东金价行情（浙商 / 工商共用）

    @Test
    fun `parses jd gold quote for zhe shang and gong shang`() {
        val json = """
            {"resultData":{"data":{"lastPrice":1049.59,"raise":4.22,"raisePercent":0.004}}}
        """.trimIndent()

        for (provider in listOf(GoldProvider.ZHE_SHANG, GoldProvider.GONG_SHANG)) {
            val info = GoldPriceParser.parse(provider, json)
            assertEquals(1049.59, info.price, 0.0001)
            assertEquals("4.22", info.changeAmount)
            assertEquals("0.40%", info.changePercent)
            assertEquals(false, info.isNegative)
        }
    }

    @Test
    fun `negative raise marks quote as falling`() {
        val json = """{"resultData":{"data":{"lastPrice":1045.37,"raise":-4.22,"raisePercent":-0.004}}}"""

        val info = GoldPriceParser.parse(GoldProvider.ZHE_SHANG, json)

        assertEquals(1045.37, info.price, 0.0001)
        assertEquals("-4.22", info.changeAmount)
        assertEquals("-0.40%", info.changePercent)
        assertEquals(true, info.isNegative)
    }

    @Test
    fun `flat raise has no direction`() {
        val json = """{"resultData":{"data":{"lastPrice":1049.59,"raise":0,"raisePercent":0}}}"""

        val info = GoldPriceParser.parse(GoldProvider.ZHE_SHANG, json)

        assertEquals(1049.59, info.price, 0.0001)
        assertEquals("0.00", info.changeAmount)
        assertEquals("0.00%", info.changePercent)
        assertNull(info.isNegative)
    }

    @Test
    fun `percent is truncated instead of rounded`() {
        // 0.409% 截断后应为 0.40%，四舍五入会得到 0.41%
        val json = """{"resultData":{"data":{"lastPrice":1049.59,"raise":4.29,"raisePercent":0.00409}}}"""

        val info = GoldPriceParser.parse(GoldProvider.ZHE_SHANG, json)

        assertEquals("0.40%", info.changePercent)
    }

    @Test
    fun `missing fields fall back to empty quote`() {
        val info = GoldPriceParser.parse(GoldProvider.ZHE_SHANG, """{"resultData":{"data":{}}}""")

        assertEquals(0.0, info.price, 0.0001)
        assertEquals("0.00", info.changeAmount)
        assertEquals("0.00%", info.changePercent)
        assertNull(info.isNegative)
    }

    @Test
    fun `malformed json never throws`() {
        assertEquals(PriceInfo.EMPTY, GoldPriceParser.parse(GoldProvider.ZHE_SHANG, "not json at all"))
        assertEquals(PriceInfo.EMPTY, GoldPriceParser.parse(GoldProvider.ZHE_SHANG, "{}"))
    }

    // MARK: - 民生接口

    @Test
    fun `parses min sheng string payload`() {
        val json = """
            {"resultData":{"data":{
              "minimumPriceValue":"1040.50",
              "rateValue":"-0.52%",
              "dayFluctuateNum":"-5.44"
            }}}
        """.trimIndent()

        val info = GoldPriceParser.parse(GoldProvider.MIN_SHENG, json)

        assertEquals(1040.50, info.price, 0.0001)
        assertEquals("-5.44", info.changeAmount)
        assertEquals("-0.52%", info.changePercent)
        assertEquals(true, info.isNegative)
    }

    @Test
    fun `min sheng positive fluctuation is marked as rising`() {
        val json = """{"resultData":{"data":{"minimumPriceValue":"1040.50","rateValue":"0.52%","dayFluctuateNum":"5.44"}}}"""

        val info = GoldPriceParser.parse(GoldProvider.MIN_SHENG, json)

        assertEquals(false, info.isNegative)
    }

    @Test
    fun `min sheng blank strings fall back to zero values`() {
        val json = """{"resultData":{"data":{"minimumPriceValue":"","rateValue":"","dayFluctuateNum":""}}}"""

        val info = GoldPriceParser.parse(GoldProvider.MIN_SHENG, json)

        assertEquals(0.0, info.price, 0.0001)
        assertEquals("0.00", info.changeAmount)
        assertEquals("0.00%", info.changePercent)
    }

    // MARK: - 关联行情

    @Test
    fun `parses market quotes and computes conversion and premium`() {
        val json = """
            {"resultData":{"data":[
              {"uniqueCode":"WG-XAUUSD","name":"伦敦金","lastPrice":2350.00,"raise":5.0,"raisePercent":0.002},
              {"uniqueCode":"SGE-Au(T+D)","name":"黄金T+D","lastPrice":548.60,"raise":-1.2,"raisePercent":-0.002},
              {"uniqueCode":"FX-USDCNH","name":"离岸人民币","lastPrice":7.2500,"raise":0.01,"raisePercent":0.001},
              {"uniqueCode":"FX-DXY","name":"美元指数","lastPrice":104.123,"raise":-0.2,"raisePercent":-0.001}
            ]}}
        """.trimIndent()

        val market = GoldPriceParser.parseMarketQuotes(json)

        // 2350.00 / 31.1035 * 7.25 = 547.72...
        val expectedConverted = 2350.00 / 31.1035 * 7.25
        assertEquals(expectedConverted, market.convertedPrice, 0.0001)
        assertEquals(548.60 - expectedConverted, market.premium, 0.0001)

        assertEquals("2350.00", market.londonGold.price)
        assertEquals("548.60", market.goldTD.price)
        assertEquals("7.2500", market.usdCnh.price)
        assertEquals("104.123", market.dollarIndex.price)
    }

    @Test
    fun `market quotes missing entries stay empty`() {
        val market = GoldPriceParser.parseMarketQuotes("""{"resultData":{"data":[]}}""")

        assertEquals(MarketData.EMPTY, market)
    }

    @Test
    fun `market quote percent is truncated to two decimals`() {
        val json = """
            {"resultData":{"data":[
              {"uniqueCode":"WG-XAUUSD","name":"伦敦金","lastPrice":2350.00,"raise":5.0,"raisePercent":0.00409}
            ]}}
        """.trimIndent()

        val market = GoldPriceParser.parseMarketQuotes(json)

        assertEquals("2350.00  +0.40%", GoldPriceParser.formatQuoteWithPercent(market.londonGold.price, market.londonGold.raisePercent))
    }

    @Test
    fun `quote formatting adds plus sign for gains and keeps minus for losses`() {
        assertEquals("--", GoldPriceParser.formatQuoteWithPercent("--", 0.001))
        assertEquals("1049.59  +0.40%", GoldPriceParser.formatQuoteWithPercent("1049.59", 0.004))
        assertEquals("1049.59  -0.40%", GoldPriceParser.formatQuoteWithPercent("1049.59", -0.004))
    }

    @Test
    fun `truncated percent helper handles zero and negatives`() {
        assertEquals("0.00%", GoldPriceParser.formatTruncatedPercent(0.0))
        assertEquals("1.00%", GoldPriceParser.formatTruncatedPercent(1.0))
        assertEquals("-1.00%", GoldPriceParser.formatTruncatedPercent(-1.0))
        assertEquals("0.40%", GoldPriceParser.formatTruncatedPercent(0.409))
    }

    @Test
    fun `provider endpoint selection matches desktop app`() {
        assertTrue(GoldProvider.ZHE_SHANG.isJdGoldQuote)
        assertTrue(GoldProvider.GONG_SHANG.isJdGoldQuote)
        assertFalse(GoldProvider.MIN_SHENG.isJdGoldQuote)

        assertTrue(GoldProvider.ZHE_SHANG.url.contains("goldCode=CZB-JCJ"))
        assertTrue(GoldProvider.GONG_SHANG.url.contains("goldCode=ICBC-JCJ"))
        assertTrue(GoldProvider.MIN_SHENG.url.contains("getFirstRelatedProductInfo"))
    }

    @Test
    fun `provider cycles zhe shang min sheng gong shang`() {
        assertEquals(GoldProvider.MIN_SHENG, GoldProvider.next(GoldProvider.ZHE_SHANG))
        assertEquals(GoldProvider.GONG_SHANG, GoldProvider.next(GoldProvider.MIN_SHENG))
        assertEquals(GoldProvider.ZHE_SHANG, GoldProvider.next(GoldProvider.GONG_SHANG))
    }

    @Test
    fun `provider storage key round trip`() {
        for (provider in GoldProvider.entries) {
            assertEquals(provider, GoldProvider.fromStorageKey(provider.storageKey))
        }
        assertEquals(GoldProvider.default, GoldProvider.fromStorageKey(null))
        assertEquals(GoldProvider.default, GoldProvider.fromStorageKey("nope"))
    }
}
