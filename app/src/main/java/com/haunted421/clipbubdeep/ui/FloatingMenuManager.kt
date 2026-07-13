package com.haunted421.clipbubdeep.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import com.haunted421.clipbubdeep.R
import com.haunted421.clipbubdeep.action.ActionHandler
import com.haunted421.clipbubdeep.action.ActionRepository
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class FloatingMenuManager(
    private val context: Context,
    private val actionHandler: ActionHandler,
    private val actionRepository: ActionRepository,
    private val prefs: SharedPreferences
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = false
    private var currentText = ""
    private var currentPkg = ""
    private val actionButtons = mutableListOf<ImageButton>()
    private lateinit var btnMain: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val autoHideRunnable = Runnable { hideMenu() }

    // Publicly readable so SelectionDetector can consult the same suppression
    // window this class writes to during drag/tap handling. This is the single
    // source of truth for "ignore incoming selection events right now" — do
    // not reintroduce a duplicate field anywhere else.
    var ignoreDeselectionUntil = 0L
        private set

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    companion object {
        private const val TAG                = "ClipBub.Menu"
        private const val RADIUS_DP          = 105f
        private const val CONTAINER_DP       = 330f
        private const val AUTO_HIDE_MS       = 15_000L
        private const val SWIPE_THRESHOLD_DP = 30f
        private const val TAP_DURATION_MS    = 200L
        private const val DRAG_DURATION_MS   = 200L
        private const val HAPTIC_TAP         = 45L
        private const val HAPTIC_EXPAND      = 35L
        private const val HAPTIC_DRAG        = 28L
        private const val PREF_X             = "menu_x"
        private const val PREF_Y             = "menu_y"
        private const val PREF_HAS_POS       = "menu_has_pos"
    }

    private val buttonIds = listOf(
        R.id.btnAction0, R.id.btnAction1, R.id.btnAction2,
        R.id.btnAction3, R.id.btnAction4, R.id.btnAction5
    )

    fun showMenu(selectionRect: Rect, text: String, pkg: String) {
        Log.d(TAG, "showMenu called, text=\"${text.take(40)}\" pkg=$pkg existingContainer=${container != null}")
        currentText = text
        currentPkg  = pkg
        if (container != null) { updatePosition(selectionRect); return }

        container = LayoutInflater.from(context).inflate(R.layout.floating_radial_menu, null)
        btnMain = container!!.findViewById(R.id.btnMain)

        val enabledActions = actionRepository.getEnabledActions().take(6)
        actionButtons.clear()
        buttonIds.forEachIndexed { i, id ->
            val btn = container!!.findViewById<ImageButton>(id)
            if (i < enabledActions.size) {
                val action = enabledActions[i]
                btn.setImageResource(action.iconResId)
                btn.visibility = View.GONE
                if (action.id == "clip") {
                    setupClipButton(btn)
                } else {
                    btn.setOnClickListener {
                        vibrate(HAPTIC_TAP)
                        actionHandler.execute(action.id, currentText, currentPkg, null)
                        collapseAndHide()
                    }
                }
                actionButtons.add(btn)
            } else {
                btn.visibility = View.GONE
            }
        }

        makeDraggable()

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        calculatePosition(selectionRect)
        try {
            windowManager.addView(container, params)
            Log.d(TAG, "container added to WindowManager at x=${params?.x} y=${params?.y}")
        } catch (e: Exception) {
            Log.e(TAG, "FAILED to add overlay view to WindowManager", e)
        }
        handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
    }

    private fun setupClipButton(btn: ImageButton) {
        val threshold = SWIPE_THRESHOLD_DP * context.resources.displayMetrics.density
        var downY = 0f
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downY = event.rawY; btn.isPressed = true; true }
                MotionEvent.ACTION_CANCEL -> { btn.isPressed = false; true }
                MotionEvent.ACTION_UP -> {
                    btn.isPressed = false
                    val delta = event.rawY - downY
                    when {
                        delta < -threshold -> {
                            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 55, 35), -1))
                            actionHandler.prependClip(currentText)
                        }
                        delta > threshold -> {
                            vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 55, 35), -1))
                            actionHandler.appendClip(currentText)
                        }
                        else -> {
                            vibrate(HAPTIC_TAP)
                            actionHandler.overwriteClip(currentText)
                        }
                    }
                    collapseAndHide()
                    true
                }
                else -> true
            }
        }
    }

    private fun calculatePosition(rect: Rect) {
        if (prefs.getBoolean(PREF_HAS_POS, false)) {
            params?.x = prefs.getInt(PREF_X, 0)
            params?.y = prefs.getInt(PREF_Y, 0)
            return
        }
        val density     = context.resources.displayMetrics.density
        val containerPx = (CONTAINER_DP * density).toInt()
        val half        = containerPx / 2
        val screenW     = context.resources.displayMetrics.widthPixels
        val screenH     = context.resources.displayMetrics.heightPixels
        var x = (rect.left + rect.right) / 2 - half
        var y = rect.top - dpToPx(8) - containerPx
        if (y < dpToPx(16)) y = rect.bottom + dpToPx(8)
        params?.x = x.coerceIn(0, maxOf(0, screenW - containerPx))
        params?.y = y.coerceIn(0, maxOf(0, screenH - containerPx))
    }

    private fun updatePosition(rect: Rect) {
        if (container == null || params == null) return
        calculatePosition(rect)
        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
    }

    fun hideMenu() {
        if (container != null) Log.d(TAG, "hideMenu called")
        handler.removeCallbacks(autoHideRunnable)
        isExpanded = false
        container?.let { v -> try { windowManager.removeView(v) } catch (_: Exception) {} }
        container = null
        actionButtons.clear()
        ignoreDeselectionUntil = 0
    }

    private fun makeDraggable() {
        var initX = 0; var initY = 0
        var initTouchX = 0f; var initTouchY = 0f
        var downAt = 0L
        var didDrag = false

        btnMain.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    didDrag = false
                    downAt = SystemClock.uptimeMillis()
                    initX = params?.x ?: 0; initY = params?.y ?: 0
                    initTouchX = event.rawX; initTouchY = event.rawY
                    ignoreDeselectionUntil = System.currentTimeMillis() + 3500L
                    handler.removeCallbacks(autoHideRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val elapsed = SystemClock.uptimeMillis() - downAt
                    val dx = event.rawX - initTouchX
                    val dy = event.rawY - initTouchY
                    if (!didDrag && elapsed > DRAG_DURATION_MS &&
                        (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        didDrag = true
                    }
                    if (didDrag) {
                        params?.x = initX + dx.toInt()
                        params?.y = initY + dy.toInt()
                        container?.let { try { windowManager.updateViewLayout(it, params) } catch (_: Exception) {} }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val elapsed = SystemClock.uptimeMillis() - downAt
                    ignoreDeselectionUntil = System.currentTimeMillis() + 1500L
                    handler.postDelayed(autoHideRunnable, AUTO_HIDE_MS)
                    if (!didDrag || elapsed < TAP_DURATION_MS) {
                        toggleExpand()
                    } else {
                        prefs.edit()
                            .putInt(PREF_X, params?.x ?: 0)
                            .putInt(PREF_Y, params?.y ?: 0)
                            .putBoolean(PREF_HAS_POS, true)
                            .apply()
                        vibrate(HAPTIC_DRAG)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExpand() { if (isExpanded) collapseMenu() else expandMenu() }

    private fun expandMenu() {
        if (isExpanded) return
        isExpanded = true
        vibrate(HAPTIC_EXPAND)
        val radius = RADIUS_DP * context.resources.displayMetrics.density
        val interp  = OvershootInterpolator(1.25f)
        val count   = actionButtons.size
        val step    = if (count > 0) 360f / count else 60f
        actionButtons.forEachIndexed { i, btn ->
            val rad = Math.toRadians((270f + i * step).toDouble())
            btn.visibility = View.VISIBLE
            btn.translationX = 0f; btn.translationY = 0f
            btn.scaleX = 0f; btn.scaleY = 0f; btn.alpha = 0f
            btn.animate()
                .translationX((radius * cos(rad)).toFloat())
                .translationY((radius * sin(rad)).toFloat())
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(300).setStartDelay(i * 35L)
                .setInterpolator(interp).start()
        }
    }

    private fun collapseMenu() {
        if (!isExpanded) return
        isExpanded = false
        actionButtons.forEach { btn ->
            btn.animate()
                .translationX(0f).translationY(0f)
                .scaleX(0f).scaleY(0f).alpha(0f)
                .setDuration(200)
                .withEndAction { btn.visibility = View.GONE }
                .start()
        }
    }

    private fun collapseAndHide() {
        collapseMenu()
        handler.postDelayed({ hideMenu() }, 240)
    }

    private fun dpToPx(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()
    private fun vibrate(ms: Long) = vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    private fun vibrate(effect: VibrationEffect) = vibrator.vibrate(effect)
}
