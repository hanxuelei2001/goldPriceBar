package com.goldpricebar.monitor.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.data.GoldProvider
import com.goldpricebar.monitor.data.PriceRepository
import com.goldpricebar.monitor.ui.MainActivity
import com.goldpricebar.monitor.ui.StatusBarPresentation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 状态栏（通知栏）展示。
 *
 * 收起时只有一行：`↑ 1049.59` + 「浙商 / 民生 / 工银」三个切换按钮，像网速指示器一样紧凑；
 * 下拉展开后显示完整价格、涨跌额与更新时间，并可直接切换数据源。
 */
object NotificationFactory {

    // 通知渠道的重要级别一旦创建就无法再改，所以从 LOW 提升到 DEFAULT 时换了新 ID，
    // 保证老版本升级上来的用户也能真正生效（旧 ID 在 ensureChannels 里删除）。
    const val CHANNEL_STATUS = "status_bar_price_v2"
    const val CHANNEL_ALERT = "price_alert"

    private const val LEGACY_CHANNEL_STATUS = "status_bar_price"

    const val ID_STATUS = 1001
    const val ID_ALERT = 1002

    private const val REQUEST_OPEN_APP = 10
    private const val REQUEST_PROVIDER_BASE = 100

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // IMPORTANCE_DEFAULT 而不是 LOW：LOW 会被系统（尤其 MIUI/HyperOS）归到
        // 「静默通知 / 更多通知」，折叠起来的同时状态栏连小图标都不显示。
        // 渠道本身不带声音与震动，所以提升到 DEFAULT 也不会打扰。
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alert_desc)
            setShowBadge(true)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(statusChannel, alertChannel))

        // 渠道创建后 importance 就改不了了，所以 1.0.3 之前用过 LOW 的旧渠道必须删掉，
        // 否则升级上来的用户仍然停留在静默分组里。
        manager.deleteNotificationChannel(LEGACY_CHANNEL_STATUS)
    }

    /** 常驻状态栏通知。 */
    fun buildStatusNotification(
        context: Context,
        state: PriceRepository.State,
        showProviderName: Boolean,
        refreshSeconds: Int,
    ): Notification {
        val trend = state.trend
        val priceInfo = state.priceInfo
        val providerName = if (showProviderName) state.provider.shortName else null

        val statusText = StatusBarPresentation.statusText(priceInfo.price, trend, providerName)
        val changeText = StatusBarPresentation.changeText(priceInfo)
        val accentColor = ContextCompat.getColor(context, StatusBarPresentation.colorRes(trend))

        val compact = RemoteViews(context.packageName, R.layout.notification_price_compact).apply {
            setTextViewText(R.id.status_arrow_price, statusText)
            setTextViewTextSize(
                R.id.status_arrow_price,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                if (showProviderName) 16f else 18f,
            )
            setTextColor(R.id.status_arrow_price, accentColor)
            applyProviderChips(context, this, state.provider, compact = true)
        }

        val expanded = RemoteViews(context.packageName, R.layout.notification_price_expanded).apply {
            setTextViewText(
                R.id.expanded_provider,
                StatusBarPresentation.providerLabel(state.provider.displayName, state.provider.shortName),
            )
            setTextViewText(R.id.expanded_arrow_price, statusText)
            setTextColor(R.id.expanded_arrow_price, accentColor)
            setTextViewText(R.id.expanded_change, changeText)
            setTextColor(R.id.expanded_change, accentColor)
            setTextViewText(R.id.expanded_meta, metaText(context, state, refreshSeconds))
            applyProviderChips(context, this, state.provider, compact = false)
        }

        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_gold)
            .setContentTitle(statusText)
            .setContentText(changeText)
            .setColor(accentColor)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            // 不再用 setSilent(true)：那会把通知打成「静默」，MIUI/HyperOS 会折叠它
            // 并且不在状态栏显示小图标。静音由渠道（无声音、无震动）保证。
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setWhen(if (state.lastUpdateAt > 0L) state.lastUpdateAt else System.currentTimeMillis())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .setContentIntent(openAppPendingIntent(context))
            // 自定义布局在少数 ROM 上会被系统模板替换，这里再挂一组标准动作按钮保底，
            // 保证「下拉通知栏就能切换数据源」在任何设备上都成立。
            .apply {
                for (provider in GoldProvider.entries) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            0,
                            provider.shortName,
                            providerPendingIntent(context, provider),
                        ).build(),
                    )
                }
            }
            .build()
    }

    /** 高低价提醒。 */
    fun buildAlertNotification(context: Context, title: String, body: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_gold)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(context))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // 用户在系统设置里撤回了通知权限，静默忽略即可。
        }
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    // MARK: - Helpers

    private fun applyProviderChips(
        context: Context,
        views: RemoteViews,
        selected: GoldProvider,
        compact: Boolean,
    ) {
        val chipIds = if (compact) {
            intArrayOf(
                R.id.chip_compact_zhe_shang,
                R.id.chip_compact_min_sheng,
                R.id.chip_compact_gong_shang,
            )
        } else {
            intArrayOf(
                R.id.chip_expanded_zhe_shang,
                R.id.chip_expanded_min_sheng,
                R.id.chip_expanded_gong_shang,
            )
        }

        val providers = GoldProvider.entries
        for (index in providers.indices) {
            val provider = providers[index]
            val isSelected = provider == selected
            views.setInt(
                chipIds[index],
                "setBackgroundResource",
                if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_normal,
            )
            views.setTextColor(
                chipIds[index],
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.surface_dark else R.color.text_secondary,
                ),
            )
            views.setOnClickPendingIntent(chipIds[index], providerPendingIntent(context, provider))
        }
    }

    private fun metaText(
        context: Context,
        state: PriceRepository.State,
        refreshSeconds: Int,
    ): String {
        val time = if (state.lastUpdateAt > 0L) {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastUpdateAt))
        } else {
            "--:--:--"
        }
        return if (state.lastRequestFailed && state.lastUpdateAt > 0L) {
            context.getString(R.string.notification_meta_failed, time)
        } else {
            context.getString(R.string.notification_meta_format, time, refreshSeconds)
        }
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 通知栏里点击数据源胶囊。
     *
     * 用 `getForegroundService` 而不是 `getService`：即使服务已经被系统回收，
     * 点击后也能重新拉起监控而不会抛异常。requestCode 必须按数据源区分，
     * 否则 PendingIntent 会因为 Intent extras 不参与比较而被复用。
     */
    private fun providerPendingIntent(context: Context, provider: GoldProvider): PendingIntent {
        val intent = Intent(context, PriceMonitorService::class.java).apply {
            action = PriceMonitorService.ACTION_SET_PROVIDER
            putExtra(PriceMonitorService.EXTRA_PROVIDER, provider.storageKey)
        }
        return PendingIntent.getForegroundService(
            context,
            REQUEST_PROVIDER_BASE + provider.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
