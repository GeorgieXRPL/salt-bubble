package com.easton.saltbubble.overlay

import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.easton.saltbubble.ui.overlay.OverlayRoot
import com.easton.saltbubble.ui.theme.SaltBubbleTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayController(
    private val service: BubbleService,
    private val chat: ChatController,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    fun attach() {
        if (view != null) return
        params = collapsedParams()
        val compose = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(service)
            setViewTreeSavedStateRegistryOwner(service)
            setViewTreeViewModelStoreOwner(service)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && _expanded.value) {
                    collapse()
                    true
                } else {
                    false
                }
            }
            setContent {
                SaltBubbleTheme {
                    OverlayRoot(
                        chat = chat,
                        expanded = _expanded,
                        onToggle = { toggle() },
                        onCollapse = { collapse() },
                        onClose = { service.requestStop() },
                        onDrag = { dx, dy -> drag(dx, dy) },
                        onIme = { enabled -> setImeEnabled(enabled) },
                    )
                }
            }
        }
        view = compose
        windowManager.addView(compose, params)
    }

    fun detach() {
        view?.let { current ->
            current.disposeComposition()
            runCatching { windowManager.removeView(current) }
        }
        view = null
    }

    fun toggle() {
        if (_expanded.value) collapse() else expand()
    }

    fun expand() {
        if (_expanded.value) return
        _expanded.value = true
        applySize(expanded = true)
    }

    fun collapse() {
        if (!_expanded.value) return
        _expanded.value = false
        setImeEnabled(false)
        applySize(expanded = false)
    }

    private fun drag(dx: Float, dy: Float) {
        val screen = screenSize()
        // Allow dragging into the lower dismiss zone (half height past bottom).
        val maxY = (screen.second - params.height / 2).coerceAtLeast(0)
        params.x = (params.x + dx.toInt()).coerceIn(0, (screen.first - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.toInt()).coerceIn(0, maxY)
        view?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun applySize(expanded: Boolean) {
        val screen = screenSize()
        val density = service.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        if (expanded) {
            params.width = minOf(dp(360), screen.first - dp(12))
            params.height = minOf(dp(560), (screen.second * 0.74f).toInt())
            params.flags = expandedFlags()
            if (params.x + params.width > screen.first) {
                params.x = (screen.first - params.width - dp(8)).coerceAtLeast(0)
            }
            if (params.y + params.height > screen.second) {
                params.y = (screen.second - params.height - dp(8)).coerceAtLeast(0)
            }
        } else {
            params.width = dp(64)
            params.height = dp(64)
            params.flags = collapsedFlags()
        }
        view?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun setImeEnabled(enabled: Boolean) {
        if (!::params.isInitialized || view == null) return
        params.flags = if (enabled) {
            expandedFlags() and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else if (_expanded.value) {
            expandedFlags()
        } else {
            collapsedFlags()
        }
        params.softInputMode = if (enabled) {
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        } else {
            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }
        view?.let { windowManager.updateViewLayout(it, params) }
    }

    private fun collapsedParams(): WindowManager.LayoutParams {
        val screen = screenSize()
        val density = service.resources.displayMetrics.density
        val size = (64 * density).toInt()
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            collapsedFlags(),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screen.first - size - (12 * density).toInt()).coerceAtLeast(0)
            y = (screen.second * 0.28f).toInt()
        }
    }

    private fun collapsedFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }

    private fun expandedFlags(): Int {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = service.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        }
    }
}
