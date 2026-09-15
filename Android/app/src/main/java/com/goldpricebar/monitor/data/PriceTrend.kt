package com.goldpricebar.monitor.data

/**
 * 金价红绿基准，与 macOS / Windows 版语义完全一致。
 *
 * - 设定了成本价时以成本价为基准：高于成本 = [RISE]（红），低于成本 = [FALL]（绿）。
 * - 未设定成本价时回退到接口返回的当日涨跌方向。
 */
enum class PriceTrend {
    /** 赚了：显示红色 + 上升箭头。 */
    RISE,

    /** 亏了：显示绿色 + 下降箭头。 */
    FALL,

    /** 持平或数据缺失。 */
    NEUTRAL,
    ;

    companion object {
        fun from(isNegative: Boolean?): PriceTrend = when (isNegative) {
            true -> FALL
            false -> RISE
            null -> NEUTRAL
        }

        /**
         * 成本价优先，无效或缺失时回退到当日涨跌方向。
         *
         * @param price 当前金价
         * @param costPrice 该数据源的买入成本价，`null` 表示未设置
         * @param isNegative 接口返回的涨跌方向
         */
        fun from(price: Double, costPrice: Double?, isNegative: Boolean?): PriceTrend {
            if (costPrice == null || !costPrice.isFinite() || costPrice <= 0.0 ||
                !price.isFinite() || price <= 0.0
            ) {
                return from(isNegative)
            }
            return when {
                price > costPrice -> RISE
                price < costPrice -> FALL
                else -> NEUTRAL
            }
        }
    }
}
