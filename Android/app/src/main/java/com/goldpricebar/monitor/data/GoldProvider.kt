package com.goldpricebar.monitor.data

/**
 * 积存金数据源。与 macOS / Windows 版保持一致：浙商、民生、工商（工银）。
 *
 * `storageKey` 会写入 SharedPreferences，重命名会影响已保存的设置。
 */
enum class GoldProvider(
    val storageKey: String,
    val displayName: String,
    val shortName: String,
) {
    ZHE_SHANG(
        storageKey = "zheShang",
        displayName = "浙商积存金",
        shortName = "浙商",
    ),
    MIN_SHENG(
        storageKey = "minSheng",
        displayName = "民生积存金",
        shortName = "民生",
    ),
    GONG_SHANG(
        storageKey = "gongShang",
        displayName = "工商积存金",
        shortName = "工银",
    ),
    ;

    /** 行情接口地址。 */
    val url: String
        get() = when (this) {
            ZHE_SHANG -> "https://api.jdjygold.com/gw2/generic/produTools/h5/m/getGoldPrice?goldCode=CZB-JCJ"
            MIN_SHENG -> "https://ms.jr.jd.com/gw2/generic/CreatorSer/newh5/m/getFirstRelatedProductInfo" +
                "?reqData=%7B%22circleId%22%3A%2213245%22%2C%22invokeSource%22%3A5%2C%22productId%22%3A%2221001001000001%22%7D"
            GONG_SHANG -> "https://api.jdjygold.com/gw2/generic/produTools/h5/m/getGoldPrice?goldCode=ICBC-JCJ"
        }

    /** 浙商与工商同为京东金价行情接口，仅 `goldCode` 不同，响应结构一致。 */
    val isJdGoldQuote: Boolean
        get() = this == ZHE_SHANG || this == GONG_SHANG

    companion object {
        val default: GoldProvider = ZHE_SHANG

        fun fromStorageKey(key: String?): GoldProvider =
            entries.firstOrNull { it.storageKey == key } ?: default

        /** 轮换到下一个数据源（浙商 → 民生 → 工银 → 浙商）。 */
        fun next(after: GoldProvider): GoldProvider {
            val all = entries
            val index = all.indexOf(after)
            return if (index < 0) default else all[(index + 1) % all.size]
        }
    }
}
