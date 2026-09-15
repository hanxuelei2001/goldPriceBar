package com.goldpricebar.monitor.data

/** 单个数据源的行情快照。 */
data class PriceInfo(
    val price: Double,
    /** 例如 `-4.22` 或 `+4.22`。 */
    val changeAmount: String,
    /** 例如 `-0.44%` 或 `+0.44%`。 */
    val changePercent: String,
    /** `true` 下跌、`false` 上涨、`null` 持平或数据缺失。 */
    val isNegative: Boolean?,
) {
    companion object {
        val EMPTY = PriceInfo(
            price = 0.0,
            changeAmount = "0.00",
            changePercent = "0.00%",
            isNegative = null,
        )
    }
}

/** 关联行情中的一行（伦敦金 / 黄金 T+D / 离岸人民币 / 美元指数）。 */
data class QuoteRow(
    val name: String,
    val price: String,
    val raise: Double,
    val raisePercent: Double,
) {
    companion object {
        val EMPTY = QuoteRow(name = "--", price = "--", raise = 0.0, raisePercent = 0.0)
    }
}

/** 详情面板展示的关联行情。 */
data class MarketData(
    val londonGold: QuoteRow,
    val goldTD: QuoteRow,
    val usdCnh: QuoteRow,
    val dollarIndex: QuoteRow,
    /** 伦敦金换算价 = XAUUSD / 31.1035 * USDCNH，单位 ¥/g。 */
    val convertedPrice: Double,
    /** 溢价 = 黄金 T+D − 换算价，单位 ¥/g。 */
    val premium: Double,
) {
    companion object {
        val EMPTY = MarketData(
            londonGold = QuoteRow.EMPTY,
            goldTD = QuoteRow.EMPTY,
            usdCnh = QuoteRow.EMPTY,
            dollarIndex = QuoteRow.EMPTY,
            convertedPrice = 0.0,
            premium = 0.0,
        )
    }
}
