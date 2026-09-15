package com.goldpricebar.monitor.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.goldpricebar.monitor.R
import com.goldpricebar.monitor.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.max

/**
 * 顶部悬浮价格条。
 *
 * Android 的状态栏属于 SystemUI，第三方 App 无法在上面写文字（系统自带的「实时网速」除外），
 * 所以这里退一步：用 TYPE_APPLICATION_OVERLAY 在状态栏**正下方**常驻一条
 * 「浙商 ↓ 928.47」，效果上等同于网速指示器。
 *
 * 需要「显示在其他应用上层」权限；点击打开主界面，拖动可调整位置并持久化。
 *
 * 所有 View 操作都会被切回主线程：行情轮询跑在 Dispatchers.Default 上，
 * 直接改 View 会抛 CalledFromWrongThreadException。
 */
class PriceOverlay(
    private val context: Context,
    private val settings: SettingsStore,
) {

    private val windowManager: WindowManager? =
        context.getSystemService(WindowManager::class.java)

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 只有 [isShowing] 会从后台线程读，其余字段一律只在主线程访问。 */
    @Volatile
    private var root: View? = null
    private var textView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = root != null

    fun show() = runOnMain { showOnMain() }

    fun hide() = runOnMain { hideOnMain() }

    /** 每次行情刷新时调用（可能来自后台线程）。 */
    fun update(text: String, color: Int) = runOnMain {
        textView?.text = text
        textView?.setTextColor(color)
    }

    /** 把持久化的位置重新套用上去（「重置位置」后无需重建悬浮条）。 */
    fun applyStoredPosition() = runOnMain { applyStoredPositionOnMain() }

    // ── 主线程实现 ──────────────────────────────────────────────────────────

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post { block() }
        }
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showOnMain() {
        if (root != null || windowManager == null) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_price, null)
        textView = view.findViewById(R.id.overlay_text)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX.coerceAtLeast(0)
            y = settings.overlayY.takeIf { it >= 0 } ?: defaultTopOffset()
        }

        attachInteractions(view, params)

        runCatching { windowManager?.addView(view, params) }
            .onFailure { return }

        root = view
        layoutParams = params
    }

    private fun hideOnMain() {
        val view = root ?: return
        root = null
        textView = null
        layoutParams = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun applyStoredPositionOnMain() {
        val view = root ?: return
        val params = layoutParams ?: return
        val storedX = settings.overlayX
        val storedY = settings.overlayY
        params.x = if (storedX >= 0) storedX else 0
        params.y = if (storedY >= 0) storedY else defaultTopOffset()
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    // ── 交互 ────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun attachInteractions(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnClickListener { openApp() }

        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val metrics = context.resources.displayMetrics
                        params.x = (startX + dx).toInt()
                            .coerceIn(0, max(0, metrics.widthPixels - touched.width))
                        params.y = (startY + dy).toInt()
                            .coerceIn(0, max(0, metrics.heightPixels - touched.height))
                        runCatching { windowManager?.updateViewLayout(touched, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        // 拖动结束后记住位置，重启后恢复。
                        settings.overlayX = params.x
                        settings.overlayY = params.y
                    } else {
                        touched.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun openApp() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // 持有 SYSTEM_ALERT_WINDOW 的应用属于后台启动 Activity 的豁免情形。
        runCatching { context.startActivity(intent) }
    }

    /**
     * 默认贴在状态栏正下方。
     *
     * TYPE_APPLICATION_OVERLAY 的窗口本身已经被系统按状态栏 inset 过（dumpsys 里
     * `parent=[0,128]`），坐标原点就在状态栏下沿，所以这里只留一点间距——
     * 再加一次状态栏高度会让悬浮条明显往下掉。
     */
    private fun defaultTopOffset(): Int =
        (4 * context.resources.displayMetrics.density).toInt()

    companion object {
        /** 是否已获得「显示在其他应用上层」权限。 */
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
