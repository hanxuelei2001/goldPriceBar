package com.goldpricebar.monitor

import android.app.Application
import com.goldpricebar.monitor.service.NotificationFactory
import com.goldpricebar.monitor.settings.SettingsStore

class GoldPriceApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 提前建好通知渠道，避免第一次 startForeground 时渠道尚未就绪。
        SettingsStore.get(this)
        NotificationFactory.ensureChannels(this)
    }
}
