package com.goldpricebar.monitor.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.data.GoldPriceParser
import com.goldpricebar.monitor.data.GoldProvider
import com.goldpricebar.monitor.data.MarketData
import com.goldpricebar.monitor.data.PriceRepository
import com.goldpricebar.monitor.databinding.ActivityMainBinding
import com.goldpricebar.monitor.databinding.DialogCostPriceBinding
import com.goldpricebar.monitor.databinding.DialogPriceAlertBinding
import com.goldpricebar.monitor.databinding.ItemCostRowBinding
import com.goldpricebar.monitor.databinding.ItemMarketRowBinding
import com.goldpricebar.monitor.service.NotificationFactory
import com.goldpricebar.monitor.service.PriceMonitorService
import com.goldpricebar.monitor.settings.PriceInput
import com.goldpricebar.monitor.settings.SettingsStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面：状态栏价格的完整版本，外加数据源、刷新频率、成本价与价格提醒设置。
 * 所有设置改动都会立刻同步到前台服务与状态栏通知。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore

    private val marketRows = LinkedHashMap<MarketRowKey, ItemMarketRowBinding>()
    private val costRows = LinkedHashMap<GoldProvider, ItemCostRowBinding>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            renderState(PriceRepository.current)
            if (!granted) {
                Toast.makeText(this, R.string.permission_notification_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = SettingsStore.get(this)
        PriceRepository.setProvider(settings.provider)

        binding.textVersion.text = getString(R.string.about_version, appVersionName())

        buildMarketRows()
        buildCostRows()

        bindProviderGroup()
        bindRefreshGroup()
        bindSwitches()
        bindActions()
        observeState()

        if (settings.monitorEnabled) {
            PriceMonitorService.start(this)
        } else {
            PriceRepository.setMonitoring(false)
        }

        requestNotificationPermissionIfNeeded()
        maybePromptCostPriceOnStartup()
    }

    override fun onStart() {
        super.onStart()
        PriceMonitorService.isUiVisible = true
        // 监控关闭时不要拉起服务，否则会闪现一下常驻通知再被移除。
        if (settings.monitorEnabled) {
            PriceMonitorService.requestRefresh(this)
        }
    }

    override fun onStop() {
        PriceMonitorService.isUiVisible = false
        super.onStop()
    }

    // MARK: - Setup

    private fun buildMarketRows() {
        val inflater = LayoutInflater.from(this)
        for (key in MarketRowKey.entries) {
            val row = ItemMarketRowBinding.inflate(inflater, binding.marketContainer, false)
            row.marketName.setText(key.labelRes)
            binding.marketContainer.addView(row.root)
            marketRows[key] = row
        }
    }

    private fun buildCostRows() {
        val inflater = LayoutInflater.from(this)
        for (provider in GoldProvider.entries) {
            val row = ItemCostRowBinding.inflate(inflater, binding.costContainer, false)
            row.costName.text = provider.displayName
            row.costEdit.setOnClickListener { showCostPriceDialog() }
            binding.costContainer.addView(row.root)
            costRows[provider] = row
        }
    }

    private fun bindProviderGroup() {
        binding.groupProvider.check(providerButtonId(settings.provider))
        binding.groupProvider.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val provider = providerFromButtonId(checkedId) ?: return@addOnButtonCheckedListener
            if (provider == settings.provider) return@addOnButtonCheckedListener

            settings.provider = provider
            // 清空上一次数据源的价格，避免切换瞬间用旧价格配新数据源的红绿。
            PriceRepository.reset()
            PriceRepository.setProvider(provider)
            PriceMonitorService.switchProvider(this, provider)
        }
    }

    private fun bindRefreshGroup() {
        binding.groupRefresh.check(refreshButtonId(settings.refreshIntervalSeconds))
        binding.groupRefresh.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val seconds = refreshSecondsFromButtonId(checkedId) ?: return@addOnButtonCheckedListener
            if (seconds == settings.refreshIntervalSeconds) return@addOnButtonCheckedListener

            settings.refreshIntervalSeconds = seconds
            PriceMonitorService.reload(this)
        }
    }

    private fun bindSwitches() {
        binding.switchMonitor.isChecked = settings.monitorEnabled
        binding.switchAutostart.isChecked = settings.autoStartOnBoot
        binding.switchShowProvider.isChecked = settings.showProviderInStatusBar
        binding.switchPromptCost.isChecked = settings.promptCostPriceOnStartup

        binding.switchMonitor.setOnCheckedChangeListener { _, checked ->
            settings.monitorEnabled = checked
            if (checked) {
                PriceMonitorService.start(this)
                requestNotificationPermissionIfNeeded()
            } else {
                PriceMonitorService.stop(this)
                PriceRepository.setMonitoring(false)
            }
            renderState(PriceRepository.current)
        }

        binding.switchAutostart.setOnCheckedChangeListener { _, checked ->
            settings.autoStartOnBoot = checked
        }

        binding.switchShowProvider.setOnCheckedChangeListener { _, checked ->
            settings.showProviderInStatusBar = checked
            PriceMonitorService.reload(this)
        }

        binding.switchPromptCost.setOnCheckedChangeListener { _, checked ->
            settings.promptCostPriceOnStartup = checked
        }
    }

    private fun bindActions() {
        binding.buttonRefreshNow.setOnClickListener {
            PriceMonitorService.requestRefresh(this)
            if (!settings.monitorEnabled) {
                settings.monitorEnabled = true
                binding.switchMonitor.isChecked = true
                PriceMonitorService.start(this)
            }
        }

        binding.buttonNotificationSettings.setOnClickListener { openNotificationSettings() }
        binding.buttonBatteryWhitelist.setOnClickListener { requestBatteryWhitelist() }

        binding.buttonSetHighAlert.setOnClickListener { showPriceAlertDialog(isHigh = true) }
        binding.buttonSetLowAlert.setOnClickListener { showPriceAlertDialog(isHigh = false) }

        binding.buttonClearHighAlert.setOnClickListener {
            settings.highPriceThreshold = null
            renderAlertRows()
            PriceMonitorService.reload(this)
        }
        binding.buttonClearLowAlert.setOnClickListener {
            settings.lowPriceThreshold = null
            renderAlertRows()
            PriceMonitorService.reload(this)
        }
        binding.buttonClearAllAlerts.setOnClickListener {
            settings.highPriceThreshold = null
            settings.lowPriceThreshold = null
            renderAlertRows()
            PriceMonitorService.reload(this)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PriceRepository.state.collect { renderState(it) }
            }
        }
    }

    // MARK: - Rendering

    private fun renderState(state: PriceRepository.State) {
        val trend = state.trend
        val color = ContextCompat.getColor(this, StatusBarPresentation.colorRes(trend))

        binding.textProvider.text = "${state.provider.shortName} · ${state.provider.displayName}"
        binding.textPrice.text = StatusBarPresentation.statusText(state.priceInfo.price, trend)
        binding.textPrice.setTextColor(color)
        binding.textChange.text = StatusBarPresentation.changeText(state.priceInfo)
        binding.textChange.setTextColor(color)

        binding.textUpdateTime.text = if (state.lastUpdateAt > 0L) {
            getString(R.string.label_update_time, timeFormat.format(Date(state.lastUpdateAt)))
        } else {
            getString(R.string.label_never_updated)
        }

        binding.textStateHint.text = buildStateHint(state)
        renderMarket(state.market)
        renderCostRows()
        renderAlertRows()
    }

    private fun buildStateHint(state: PriceRepository.State): String {
        val parts = mutableListOf(
            getString(
                if (state.isMonitoring) R.string.label_monitoring_on else R.string.label_monitoring_off,
            ),
        )
        if (state.lastRequestFailed) {
            parts += getString(R.string.label_fetch_failed)
        }
        if (!NotificationFactory.areNotificationsEnabled(this)) {
            parts += getString(R.string.permission_notification_denied)
        }
        return parts.joinToString("  ·  ")
    }

    private fun renderMarket(market: MarketData) {
        val hasData = market.convertedPrice > 0.0 || market.londonGold.price != "--"
        binding.textMarketHint.visibility = if (hasData) View.GONE else View.VISIBLE

        setMarketRow(
            MarketRowKey.LONDON_GOLD,
            GoldPriceParser.formatQuoteWithPercent(market.londonGold.price, market.londonGold.raisePercent),
            trendColor(market.londonGold.raise),
        )
        setMarketRow(
            MarketRowKey.GOLD_TD,
            GoldPriceParser.formatQuoteWithPercent(market.goldTD.price, market.goldTD.raisePercent),
            trendColor(market.goldTD.raise),
        )
        setMarketRow(
            MarketRowKey.CONVERTED,
            if (market.convertedPrice > 0.0) PriceInput.formatPrice(market.convertedPrice) else "--",
            ContextCompat.getColor(this, R.color.text_primary),
        )
        setMarketRow(
            MarketRowKey.PREMIUM,
            if (market.convertedPrice > 0.0) {
                String.format(Locale.US, "%+.2f", market.premium)
            } else {
                "--"
            },
            trendColor(market.premium),
        )
        setMarketRow(
            MarketRowKey.USDCNH,
            GoldPriceParser.formatQuoteWithPercent(market.usdCnh.price, market.usdCnh.raisePercent),
            trendColor(market.usdCnh.raise),
        )
        setMarketRow(
            MarketRowKey.DXY,
            GoldPriceParser.formatQuoteWithPercent(market.dollarIndex.price, market.dollarIndex.raisePercent),
            trendColor(market.dollarIndex.raise),
        )
    }

    private fun setMarketRow(key: MarketRowKey, value: String, color: Int) {
        marketRows[key]?.let { row ->
            row.marketValue.text = value
            row.marketValue.setTextColor(color)
        }
    }

    private fun renderCostRows() {
        for (provider in GoldProvider.entries) {
            val cost = settings.costPrice(provider)
            costRows[provider]?.costValue?.text =
                if (cost != null) PriceInput.formatPrice(cost) else getString(R.string.cost_unset)
        }
    }

    private fun renderAlertRows() {
        val high = settings.highPriceThreshold
        val low = settings.lowPriceThreshold

        binding.textHighAlertValue.text = if (high != null) {
            getString(R.string.alert_condition_high, PriceInput.formatPrice(high))
        } else {
            getString(R.string.cost_unset)
        }
        binding.textLowAlertValue.text = if (low != null) {
            getString(R.string.alert_condition_low, PriceInput.formatPrice(low))
        } else {
            getString(R.string.cost_unset)
        }

        binding.buttonClearHighAlert.visibility = if (high != null) View.VISIBLE else View.GONE
        binding.buttonClearLowAlert.visibility = if (low != null) View.VISIBLE else View.GONE
        binding.buttonClearAllAlerts.visibility =
            if (high != null || low != null) View.VISIBLE else View.GONE
    }

    private fun trendColor(raise: Double): Int = ContextCompat.getColor(
        this,
        when {
            raise > 0.0 -> R.color.trend_rise
            raise < 0.0 -> R.color.trend_fall
            else -> R.color.text_primary
        },
    )

    // MARK: - Dialogs

    private fun showCostPriceDialog() {
        val dialogBinding = DialogCostPriceBinding.inflate(layoutInflater)
        dialogBinding.inputCostZheShang.setText(costText(GoldProvider.ZHE_SHANG))
        dialogBinding.inputCostMinSheng.setText(costText(GoldProvider.MIN_SHENG))
        dialogBinding.inputCostGongShang.setText(costText(GoldProvider.GONG_SHANG))

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.cost_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawInputs = mapOf(
                    GoldProvider.ZHE_SHANG to dialogBinding.inputCostZheShang.text?.toString(),
                    GoldProvider.MIN_SHENG to dialogBinding.inputCostMinSheng.text?.toString(),
                    GoldProvider.GONG_SHANG to dialogBinding.inputCostGongShang.text?.toString(),
                )

                val invalid = rawInputs.any { (_, raw) ->
                    !raw.isNullOrBlank() && PriceInput.parsePrice(raw) == null
                }
                if (invalid) {
                    Toast.makeText(this, R.string.alert_invalid_message, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                settings.setCostPrices(rawInputs.mapValues { (_, raw) -> PriceInput.parsePrice(raw) })
                applyCostPriceChange()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun costText(provider: GoldProvider): String =
        settings.costPrice(provider)?.let { PriceInput.formatPrice(it) }.orEmpty()

    private fun applyCostPriceChange() {
        PriceRepository.recomputeTrend(settings.costPrice(PriceRepository.current.provider))
        PriceMonitorService.reload(this)
        renderCostRows()
        renderState(PriceRepository.current)
    }

    private fun showPriceAlertDialog(isHigh: Boolean) {
        val dialogBinding = DialogPriceAlertBinding.inflate(layoutInflater)
        dialogBinding.alertMessage.setText(
            if (isHigh) R.string.alert_high_dialog_message else R.string.alert_low_dialog_message,
        )
        val current = if (isHigh) settings.highPriceThreshold else settings.lowPriceThreshold
        if (current != null) {
            dialogBinding.alertInput.setText(PriceInput.formatPrice(current))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isHigh) R.string.alert_high_dialog_title else R.string.alert_low_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_ok, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = PriceInput.parsePrice(dialogBinding.alertInput.text?.toString())
                if (value == null) {
                    Toast.makeText(this, R.string.alert_invalid_message, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isHigh) {
                    settings.highPriceThreshold = value
                } else {
                    settings.lowPriceThreshold = value
                }
                renderAlertRows()
                PriceMonitorService.reload(this)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // MARK: - Permissions & system settings

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return

        AlertDialog.Builder(this)
            .setTitle(R.string.action_open_notification_settings)
            .setMessage(R.string.permission_notification_rationale)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.battery_whitelist_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestBatteryWhitelist() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(packageName) == true) {
            Toast.makeText(this, R.string.battery_whitelist_done, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.battery_whitelist_title)
            .setMessage(R.string.battery_whitelist_rationale)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.battery_whitelist_unavailable, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun maybePromptCostPriceOnStartup() {
        if (!settings.promptCostPriceOnStartup) return
        binding.root.postDelayed(
            {
                if (!isFinishing && !isDestroyed) showCostPriceDialog()
            },
            STARTUP_COST_DIALOG_DELAY_MS,
        )
    }

    private fun appVersionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull() ?: "1.0.0"

    // MARK: - View id mapping

    private fun providerButtonId(provider: GoldProvider): Int = when (provider) {
        GoldProvider.ZHE_SHANG -> R.id.button_provider_zhe_shang
        GoldProvider.MIN_SHENG -> R.id.button_provider_min_sheng
        GoldProvider.GONG_SHANG -> R.id.button_provider_gong_shang
    }

    private fun providerFromButtonId(id: Int): GoldProvider? = when (id) {
        R.id.button_provider_zhe_shang -> GoldProvider.ZHE_SHANG
        R.id.button_provider_min_sheng -> GoldProvider.MIN_SHENG
        R.id.button_provider_gong_shang -> GoldProvider.GONG_SHANG
        else -> null
    }

    private fun refreshButtonId(seconds: Int): Int = when (seconds) {
        1 -> R.id.button_refresh_1
        2 -> R.id.button_refresh_2
        10 -> R.id.button_refresh_10
        else -> R.id.button_refresh_5
    }

    private fun refreshSecondsFromButtonId(id: Int): Int? = when (id) {
        R.id.button_refresh_1 -> 1
        R.id.button_refresh_2 -> 2
        R.id.button_refresh_5 -> 5
        R.id.button_refresh_10 -> 10
        else -> null
    }

    private enum class MarketRowKey(@StringRes val labelRes: Int) {
        LONDON_GOLD(R.string.market_london_gold),
        GOLD_TD(R.string.market_gold_td),
        CONVERTED(R.string.market_converted),
        PREMIUM(R.string.market_premium),
        USDCNH(R.string.market_usdcnh),
        DXY(R.string.market_dxy),
    }

    private companion object {
        const val STARTUP_COST_DIALOG_DELAY_MS = 700L
    }
}
