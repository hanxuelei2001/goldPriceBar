package com.goldpricebar.monitor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 前台服务与界面之间的单一数据源。
 *
 * 服务负责写入，Activity 通过 [state] 订阅，因此通知栏里切换数据源后界面会立刻同步。
 */
object PriceRepository {

    data class State(
        val provider: GoldProvider = GoldProvider.default,
        val priceInfo: PriceInfo = PriceInfo.EMPTY,
        val trend: PriceTrend = PriceTrend.NEUTRAL,
        val market: MarketData = MarketData.EMPTY,
        /** 最近一次成功刷新的时间戳（毫秒），0 表示还没拿到数据。 */
        val lastUpdateAt: Long = 0L,
        /** 正在请求中。 */
        val isFetching: Boolean = false,
        /** 常驻状态栏监控是否运行中。 */
        val isMonitoring: Boolean = false,
        /** 最近一次请求是否失败（用于在界面上提示）。 */
        val lastRequestFailed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())

    val state: StateFlow<State> = _state.asStateFlow()

    val current: State get() = _state.value

    fun setProvider(provider: GoldProvider) = _state.update { it.copy(provider = provider) }

    fun setMonitoring(monitoring: Boolean) = _state.update { it.copy(isMonitoring = monitoring) }

    fun setFetching(fetching: Boolean) = _state.update { it.copy(isFetching = fetching) }

    fun updateQuote(
        provider: GoldProvider,
        priceInfo: PriceInfo,
        trend: PriceTrend,
        timestamp: Long,
        failed: Boolean,
    ) = _state.update {
        it.copy(
            provider = provider,
            priceInfo = priceInfo,
            trend = trend,
            lastUpdateAt = if (failed) it.lastUpdateAt else timestamp,
            lastRequestFailed = failed,
        )
    }

    fun updateMarket(market: MarketData) = _state.update { it.copy(market = market) }

    /** 本次请求失败但已有历史数据：保留上一次的价格，只把失败标记打开。 */
    fun markRequestFailed() = _state.update { it.copy(lastRequestFailed = true) }

    /** 请求重新开始。 */
    fun clearRequestFailure() = _state.update { it.copy(lastRequestFailed = false) }

    /** 重新按当前设置计算红绿基准（成本价改动后立即生效）。 */
    fun recomputeTrend(costPrice: Double?) = _state.update {
        it.copy(
            trend = PriceTrend.from(
                price = it.priceInfo.price,
                costPrice = costPrice,
                isNegative = it.priceInfo.isNegative,
            ),
        )
    }

    fun reset() = _state.update {
        State(provider = it.provider, isMonitoring = it.isMonitoring)
    }
}
