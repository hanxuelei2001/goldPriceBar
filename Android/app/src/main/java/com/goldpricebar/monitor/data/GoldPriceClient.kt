package com.goldpricebar.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 HTTP 客户端：只发两个 GET 请求，不引入 OkHttp 之类的额外依赖。
 * 任何网络错误都退化为 [PriceInfo.EMPTY] / [MarketData.EMPTY]，与桌面端行为一致。
 */
class GoldPriceClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 5_000,
) {

    suspend fun fetchPriceInfo(provider: GoldProvider): PriceInfo = withContext(Dispatchers.IO) {
        val body = get(provider.url) ?: return@withContext PriceInfo.EMPTY
        GoldPriceParser.parse(provider, body)
    }

    suspend fun fetchMarketData(): MarketData = withContext(Dispatchers.IO) {
        val body = get(MARKET_QUOTE_URL) ?: return@withContext MarketData.EMPTY
        GoldPriceParser.parseMarketQuotes(body)
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val USER_AGENT = "GoldPriceBarAndroid/1.0"

        /** 伦敦金 / 黄金 T+D / 离岸人民币 / 美元指数。 */
        const val MARKET_QUOTE_URL: String =
            "https://ms.jr.jd.com/gw2/generic/jdtwt/h5/m/getSimpleQuoteUseUniqueCodes" +
                "?reqData=%7B%22ticket%22%3A%22gold-price-h5%22%2C%22uniqueCodes%22%3A" +
                "%5B%22WG-XAUUSD%22%2C%22SGE-Au(T%2BD)%22%2C%22FX-USDCNH%22%2C%22FX-DXY%22%5D%7D"
    }
}
