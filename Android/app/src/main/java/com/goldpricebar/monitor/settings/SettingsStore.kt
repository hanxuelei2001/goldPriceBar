package com.goldpricebar.monitor.settings

import android.content.Context
import android.content.SharedPreferences
import com.goldpricebar.monitor.data.GoldProvider

/**
 * 设置持久化（SharedPreferences），与桌面端的 UserDefaults / settings.json 一一对应。
 */
class SettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前数据源。 */
    var provider: GoldProvider
        get() = GoldProvider.fromStorageKey(prefs.getString(KEY_PROVIDER, null))
        set(value) = prefs.edit().putString(KEY_PROVIDER, value.storageKey).apply()

    /** 刷新间隔（秒），仅允许 [REFRESH_OPTIONS] 中的值。 */
    var refreshIntervalSeconds: Int
        get() = prefs.getInt(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_SECONDS)
            .takeIf { it in REFRESH_OPTIONS } ?: DEFAULT_REFRESH_SECONDS
        set(value) {
            val safe = if (value in REFRESH_OPTIONS) value else DEFAULT_REFRESH_SECONDS
            prefs.edit().putInt(KEY_REFRESH_INTERVAL, safe).apply()
        }

    /** 高价提醒：金价 ≥ 该值时提醒；`null` 表示未设置。 */
    var highPriceThreshold: Double?
        get() = readPrice(KEY_HIGH_THRESHOLD)
        set(value) = writePrice(KEY_HIGH_THRESHOLD, value)

    /** 低价提醒：金价 ≤ 该值时提醒；`null` 表示未设置。 */
    var lowPriceThreshold: Double?
        get() = readPrice(KEY_LOW_THRESHOLD)
        set(value) = writePrice(KEY_LOW_THRESHOLD, value)

    /** 状态栏常驻监控开关（前台服务）。 */
    var monitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITOR_ENABLED, value).apply()

    /** 开机自动启动监控。 */
    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    /** 启动时是否弹出成本价设置。 */
    var promptCostPriceOnStartup: Boolean
        get() = prefs.getBoolean(KEY_PROMPT_COST_PRICE, true)
        set(value) = prefs.edit().putBoolean(KEY_PROMPT_COST_PRICE, value).apply()

    /** 状态栏显示当前数据源简称（关闭后只显示箭头 + 价格，更省空间）。 */
    var showProviderInStatusBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PROVIDER, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_PROVIDER, value).apply()

    fun costPrice(provider: GoldProvider): Double? = readPrice(costKey(provider))

    fun setCostPrice(provider: GoldProvider, value: Double?) = writePrice(costKey(provider), value)

    fun costPrices(): Map<GoldProvider, Double> =
        GoldProvider.entries.mapNotNull { provider ->
            costPrice(provider)?.let { provider to it }
        }.toMap()

    fun setCostPrices(prices: Map<GoldProvider, Double?>) {
        val editor = prefs.edit()
        for (provider in GoldProvider.entries) {
            val key = costKey(provider)
            val value = prices[provider]
            if (value == null || !value.isFinite() || value <= 0.0) {
                editor.remove(key)
            } else {
                editor.putString(key, value.toString())
            }
        }
        editor.apply()
    }

    private fun readPrice(key: String): Double? =
        PriceInput.parsePrice(prefs.getString(key, null))

    private fun writePrice(key: String, value: Double?) {
        val editor = prefs.edit()
        if (value == null || !value.isFinite() || value <= 0.0) {
            editor.remove(key)
        } else {
            editor.putString(key, value.toString())
        }
        editor.apply()
    }

    private fun costKey(provider: GoldProvider) = "cost_price_${provider.storageKey}"

    companion object {
        private const val PREFS_NAME = "gold_price_bar_settings"

        private const val KEY_PROVIDER = "selected_provider"
        private const val KEY_REFRESH_INTERVAL = "refresh_interval_seconds"
        private const val KEY_HIGH_THRESHOLD = "high_price_threshold"
        private const val KEY_LOW_THRESHOLD = "low_price_threshold"
        private const val KEY_MONITOR_ENABLED = "monitor_enabled"
        private const val KEY_AUTO_START = "auto_start_on_boot"
        private const val KEY_PROMPT_COST_PRICE = "prompt_cost_price_on_startup"
        private const val KEY_SHOW_PROVIDER = "show_provider_in_status_bar"

        /** 与桌面端一致的刷新频率档位。 */
        val REFRESH_OPTIONS = intArrayOf(1, 2, 5, 10)

        /** 移动端默认 5 秒：1 秒轮询在手机上耗电明显，用户可在界面里改回 1 秒。 */
        const val DEFAULT_REFRESH_SECONDS = 5

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }
    }
}
