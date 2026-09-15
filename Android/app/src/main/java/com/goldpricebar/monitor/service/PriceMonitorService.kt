package com.goldpricebar.monitor.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.data.GoldPriceClient
import com.goldpricebar.monitor.data.GoldProvider
import com.goldpricebar.monitor.data.PriceRepository
import com.goldpricebar.monitor.data.PriceTrend
import com.goldpricebar.monitor.settings.PriceInput
import com.goldpricebar.monitor.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 状态栏常驻监控。
 *
 * 前台服务按设定间隔轮询行情，并把结果写到
 * [PriceRepository]（供界面订阅）和常驻通知（供系统状态栏显示）。
 * 通知栏里的数据源切换、以及界面上的设置改动都通过 `onStartCommand` 的 action 进入。
 */
class PriceMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client = GoldPriceClient()

    /** 立即刷新信号：轮询循环在等待间隔时会被它提前唤醒。 */
    private val refreshSignal = Channel<Unit>(Channel.CONFLATED)

    private lateinit var settings: SettingsStore

    private var pollJob: Job? = null

    // 每次穿越阈值只提醒一次，价格回到正常范围后自动复位（与桌面端一致）。
    private var highAlertTriggered = false
    private var lowAlertTriggered = false

    private var lastMarketFetchAt = 0L

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.get(this)
        NotificationFactory.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_SET_PROVIDER) {
            val provider = GoldProvider.fromStorageKey(intent.getStringExtra(EXTRA_PROVIDER))
            settings.provider = provider
            PriceRepository.setProvider(provider)
            // 切换数据源后清空提醒状态，避免用旧数据源的阈值状态继续抑制提醒。
            highAlertTriggered = false
            lowAlertTriggered = false
        }

        // 必须先 startForeground：服务可能由 startForegroundService 拉起，5 秒内不调用会崩溃。
        startForegroundCompat()

        if (!settings.monitorEnabled) {
            shutdown()
            return START_NOT_STICKY
        }

        PriceRepository.setMonitoring(true)
        ensurePolling()

        when (action) {
            ACTION_SET_PROVIDER, ACTION_RELOAD, ACTION_REFRESH_NOW -> refreshSignal.trySend(Unit)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        PriceRepository.setMonitoring(false)
        scope.cancel()
        super.onDestroy()
    }

    // MARK: - Polling

    private fun ensurePolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch { pollingLoop() }
    }

    private suspend fun pollingLoop() {
        while (currentCoroutineContext().isActive) {
            // 单次请求的意外异常不应该让整个监控停摆。
            try {
                refreshOnce()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                PriceRepository.setFetching(false)
                PriceRepository.markRequestFailed()
            }

            val intervalMs = settings.refreshIntervalSeconds * 1_000L
            // 正常等待一个刷新间隔；期间若收到「立即刷新」信号则提前唤醒。
            withTimeoutOrNull(intervalMs) { refreshSignal.receive() }
        }
    }

    private suspend fun refreshOnce() {
        PriceRepository.setFetching(true)
        val provider = settings.provider
        val priceInfo = client.fetchPriceInfo(provider)
        PriceRepository.setFetching(false)

        val failed = !priceInfo.price.isFinite() || priceInfo.price <= 0.0
        val hasPreviousData = PriceRepository.current.lastUpdateAt > 0L

        if (failed && hasPreviousData) {
            // 移动网络容易抖动：保留上一次成功的价格，只标记本次请求失败，避免状态栏闪成 0.00。
            PriceRepository.markRequestFailed()
        } else {
            val trend = PriceTrend.from(
                price = priceInfo.price,
                costPrice = settings.costPrice(provider),
                isNegative = priceInfo.isNegative,
            )
            PriceRepository.updateQuote(provider, priceInfo, trend, System.currentTimeMillis(), failed)
        }

        updateStatusNotification()

        if (!failed) {
            checkPriceAlerts(priceInfo.price)
        }

        maybeFetchMarketData()
    }

    private suspend fun maybeFetchMarketData() {
        val now = System.currentTimeMillis()
        val overdue = now - lastMarketFetchAt >= MARKET_FETCH_IDLE_INTERVAL_MS
        if (!isUiVisible && !overdue) return

        lastMarketFetchAt = now
        PriceRepository.updateMarket(client.fetchMarketData())
    }

    // MARK: - Notification

    private fun startForegroundCompat() {
        val notification = NotificationFactory.buildStatusNotification(
            context = this,
            state = PriceRepository.current,
            showProviderName = settings.showProviderInStatusBar,
            refreshSeconds = settings.refreshIntervalSeconds,
        )
        ServiceCompat.startForeground(
            this,
            NotificationFactory.ID_STATUS,
            notification,
            foregroundServiceTypeCompat,
        )
    }

    private fun updateStatusNotification() {
        val notification = NotificationFactory.buildStatusNotification(
            context = this,
            state = PriceRepository.current,
            showProviderName = settings.showProviderInStatusBar,
            refreshSeconds = settings.refreshIntervalSeconds,
        )
        NotificationFactory.post(this, NotificationFactory.ID_STATUS, notification)
    }

    /** 名字不能叫 foregroundServiceType：会与 API 29+ 的 Service.getForegroundServiceType() 撞签名。 */
    private val foregroundServiceTypeCompat: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    // MARK: - Price alerts

    private fun checkPriceAlerts(price: Double) {
        if (price <= 0.0) return
        val provider = settings.provider

        settings.highPriceThreshold?.let { high ->
            if (price >= high && !highAlertTriggered) {
                highAlertTriggered = true
                postAlert(
                    title = getString(R.string.alert_triggered_high_title),
                    body = getString(
                        R.string.alert_triggered_body,
                        provider.displayName,
                        PriceInput.formatPrice(price),
                        getString(R.string.alert_condition_high, PriceInput.formatPrice(high)),
                    ),
                )
            } else if (price < high) {
                highAlertTriggered = false
            }
        }

        settings.lowPriceThreshold?.let { low ->
            if (price <= low && !lowAlertTriggered) {
                lowAlertTriggered = true
                postAlert(
                    title = getString(R.string.alert_triggered_low_title),
                    body = getString(
                        R.string.alert_triggered_body,
                        provider.displayName,
                        PriceInput.formatPrice(price),
                        getString(R.string.alert_condition_low, PriceInput.formatPrice(low)),
                    ),
                )
            } else if (price > low) {
                lowAlertTriggered = false
            }
        }
    }

    private fun postAlert(title: String, body: String) {
        NotificationFactory.post(
            this,
            NotificationFactory.ID_ALERT,
            NotificationFactory.buildAlertNotification(this, title, body),
        )
    }

    private fun shutdown() {
        PriceRepository.setMonitoring(false)
        pollJob?.cancel()
        pollJob = null
        NotificationFactory.cancel(this, NotificationFactory.ID_STATUS)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.goldpricebar.monitor.action.START"
        const val ACTION_RELOAD = "com.goldpricebar.monitor.action.RELOAD"
        const val ACTION_SET_PROVIDER = "com.goldpricebar.monitor.action.SET_PROVIDER"
        const val ACTION_REFRESH_NOW = "com.goldpricebar.monitor.action.REFRESH_NOW"

        const val EXTRA_PROVIDER = "extra_provider"

        /** 界面可见时每个刷新周期都取关联行情；不可见时降频到 60 秒。 */
        private const val MARKET_FETCH_IDLE_INTERVAL_MS = 60_000L

        /** 界面是否在前台，决定关联行情的刷新频率。 */
        @Volatile
        var isUiVisible: Boolean = false

        fun start(context: Context) = send(context, ACTION_START)

        /** 设置变化后调用：立即用新设置刷一次。 */
        fun reload(context: Context) = send(context, ACTION_RELOAD)

        fun requestRefresh(context: Context) = send(context, ACTION_REFRESH_NOW)

        fun switchProvider(context: Context, provider: GoldProvider) {
            val intent = Intent(context, PriceMonitorService::class.java).apply {
                action = ACTION_SET_PROVIDER
                putExtra(EXTRA_PROVIDER, provider.storageKey)
            }
            startForegroundServiceSafely(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PriceMonitorService::class.java))
        }

        private fun send(context: Context, action: String) {
            val intent = Intent(context, PriceMonitorService::class.java).setAction(action)
            startForegroundServiceSafely(context, intent)
        }

        /**
         * Android 12+ 禁止 App 在后台启动前台服务（开机自启时可能命中该限制），
         * 这里捕获异常静默降级：用户下次打开界面时会重新拉起监控。
         */
        private fun startForegroundServiceSafely(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: IllegalStateException) {
                // 后台启动被限制，忽略。
            }
        }
    }
}
