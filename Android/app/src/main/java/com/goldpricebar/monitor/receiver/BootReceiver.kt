package com.goldpricebar.monitor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goldpricebar.monitor.service.PriceMonitorService
import com.goldpricebar.monitor.settings.SettingsStore

/** 开机 / 应用更新后按设置自动恢复状态栏监控。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isPackageReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED || action == ACTION_QUICKBOOT_POWERON
        if (!isBoot && !isPackageReplaced) return

        val settings = SettingsStore.get(context)
        if (!settings.monitorEnabled) return
        // 应用更新后总是恢复；正常开机则遵循「开机自动启动」开关。
        if (!isPackageReplaced && !settings.autoStartOnBoot) return

        PriceMonitorService.start(context)
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
