package com.goldpricebar.monitor.data

import org.json.JSONObject
import java.util.Locale

/**
 * 行情 JSON 解析。全部为纯函数，便于在 JVM 单元测试中直接覆盖。
 *
 * 解析规则与 macOS / Windows 版保持一致：
 * - 浙商（CZB-JCJ）与工商（ICBC-JCJ）共用京东金价行情结构；
 * - 民生接口为独立结构，字段都是字符串；
 * - 涨跌幅按**截断**（非四舍五入）保留两位小数；
 * - 任何解析失败都回退到 [PriceInfo.EMPTY] / [MarketData.EMPTY]，不抛异常。
 */
object GoldPriceParser {

    private const val TROY_OUNCE_IN_GRAMS = 31.1035

    private const val CODE_XAUUSD = "WG-XAUUSD"
    private const val CODE_AU_TD = "SGE-Au(T+D)"
    private const val CODE_USDCNH = "FX-USDCNH"
    private const val CODE_DXY = "FX-DXY"

    fun parse(provider: GoldProvider, json: String): PriceInfo =
        if (provider.isJdGoldQuote) parseJdGoldQuote(json) else parseMinSheng(json)

    /** 京东金价行情：`resultData.data.{lastPrice, raise, raisePercent}`。 */
    fun parseJdGoldQuote(json: String): PriceInfo = runCatching {
        val node = JSONObject(json)
            .optJSONObject("resultData")
            ?.optJSONObject("data")
            ?: return PriceInfo.EMPTY

        val price = node.optDouble("lastPrice", 0.0).orZero()
        val raise = node.optDouble("raise", 0.0).orZero()
        val raisePercent = node.optDouble("raisePercent", 0.0).orZero()

        PriceInfo(
            price = price,
            changeAmount = String.format(Locale.US, "%.2f", raise),
            changePercent = formatTruncatedPercent(raisePercent * 100.0),
            isNegative = when {
                raise < 0.0 -> true
                raise > 0.0 -> false
                else -> null
            },
        )
    }.getOrDefault(PriceInfo.EMPTY)

    /**
     * 民生接口：`resultData.data.{minimumPriceValue, rateValue, dayFluctuateNum}`，
     * 三个字段都是字符串，涨跌幅由接口直接给出。
     */
    fun parseMinSheng(json: String): PriceInfo = runCatching {
        val node = JSONObject(json)
            .optJSONObject("resultData")
            ?.optJSONObject("data")
            ?: return PriceInfo.EMPTY

        val price = node.optString("minimumPriceValue").toDoubleOrNull() ?: 0.0
        val amountStr = node.optString("dayFluctuateNum").ifBlank { "0.00" }
        val percentStr = node.optString("rateValue").ifBlank { "0.00%" }

        val isNegative = when {
            amountStr.startsWith("-") -> true
            (amountStr.toDoubleOrNull() ?: 0.0) > 0.0 -> false
            else -> null
        }

        PriceInfo(
            price = price,
            changeAmount = amountStr,
            changePercent = percentStr,
            isNegative = isNegative,
        )
    }.getOrDefault(PriceInfo.EMPTY)

    /** 关联行情：伦敦金、黄金 T+D、离岸人民币、美元指数。 */
    fun parseMarketQuotes(json: String): MarketData = runCatching {
        val items = JSONObject(json)
            .optJSONObject("resultData")
            ?.optJSONArray("data")
            ?: return MarketData.EMPTY

        var xauusd: JSONObject? = null
        var auTd: JSONObject? = null
        var usdCnh: JSONObject? = null
        var dxy: JSONObject? = null

        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            when (item.optString("uniqueCode")) {
                CODE_XAUUSD -> xauusd = item
                CODE_AU_TD -> auTd = item
                CODE_USDCNH -> usdCnh = item
                CODE_DXY -> dxy = item
            }
        }

        val londonPrice = xauusd.optLastPrice()
        val exchangeRate = usdCnh.optLastPrice()
        val auTdPrice = auTd.optLastPrice()

        val converted = if (londonPrice > 0.0 && exchangeRate > 0.0) {
            londonPrice / TROY_OUNCE_IN_GRAMS * exchangeRate
        } else {
            0.0
        }
        val premium = if (converted > 0.0 && auTdPrice > 0.0) auTdPrice - converted else 0.0

        MarketData(
            londonGold = xauusd.toQuoteRow(decimals = 2),
            goldTD = auTd.toQuoteRow(decimals = 2),
            usdCnh = usdCnh.toQuoteRow(decimals = 4),
            dollarIndex = dxy.toQuoteRow(decimals = 3),
            convertedPrice = converted,
            premium = premium,
        )
    }.getOrDefault(MarketData.EMPTY)

    /** 截断（而非四舍五入）到两位小数后追加 `%`，例如 `0.406` → `0.40%`。 */
    fun formatTruncatedPercent(percent: Double): String {
        if (!percent.isFinite()) return "0.00%"
        val truncated = (percent * 100.0).toLong().toDouble() / 100.0
        return String.format(Locale.US, "%.2f%%", truncated)
    }

    private fun Double.orZero(): Double = if (isFinite()) this else 0.0

    private fun JSONObject?.optLastPrice(): Double =
        (this?.optDouble("lastPrice", 0.0) ?: 0.0).orZero()

    private fun JSONObject?.toQuoteRow(decimals: Int): QuoteRow {
        val node = this ?: return QuoteRow.EMPTY
        val price = node.optDouble("lastPrice", 0.0).orZero()
        return QuoteRow(
            name = node.optString("name").ifBlank { "--" },
            price = String.format(Locale.US, "%.${decimals}f", price),
            raise = node.optDouble("raise", 0.0).orZero(),
            raisePercent = node.optDouble("raisePercent", 0.0).orZero(),
        )
    }

    /** 供 UI 展示：`1049.59  +0.40%` 形式，缺数据时原样返回 `--`。 */
    fun formatQuoteWithPercent(price: String, raisePercent: Double): String {
        if (price == "--") return price
        val truncated = (raisePercent * 100.0 * 100.0).toLong().toDouble() / 100.0
        val sign = if (truncated > 0.0) "+" else ""
        return String.format(Locale.US, "%s  %s%.2f%%", price, sign, truncated)
    }
}
