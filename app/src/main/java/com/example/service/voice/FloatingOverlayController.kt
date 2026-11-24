package com.example.service.voice

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.domain.model.AssistantState
import kotlin.math.abs

/**
 * Floating draggable microphone button visible over any app via SYSTEM_ALERT_WINDOW.
 * - Tap: talk / interrupt TTS
 * - Drag: smooth reposition with screen edge awareness
 * - Long-press: opens compact quick-action menu (Home, Back, Screenshot, Stop AI, Open App, Hide)
 * - Live state styling: Idle (Cyan), Listening (Electric Pulse), Thinking (Amber), Speaking (Emerald), Confirming (Coral)
 */
class FloatingOverlayController(
    private val context: Context,
    private val onTapMic: () -> Unit,
    private val onQuickAction: (QuickOverlayAction) -> Unit
) {

    enum class QuickOverlayAction {
        HOME, BACK, SCREENSHOT, STOP_TASK, OPEN_APP, HIDE_OVERLAY
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootContainer: LinearLayout? = null
    private var orbButton: TextView? = null
    private var quickMenuContainer: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false
    private var isMenuOpen = false

    fun show() {
        mainHandler.post {
            if (isShowing || !Settings.canDrawOverlays(context)) return@post
            createAndAttachOverlay()
        }
    }

    fun hide() {
        mainHandler.post {
            if (!isShowing) return@post
            runCatching { rootContainer?.let { windowManager.removeView(it) } }
            rootContainer = null
            orbButton = null
            quickMenuContainer = null
            isShowing = false
            isMenuOpen = false
        }
    }

    fun updateState(state: AssistantState) {
        mainHandler.post {
            val orb = orbButton ?: return@post
            val (bgColor, strokeColor, glyph) = when (state) {
                AssistantState.IDLE -> Triple(0xFF0E2A3A.toInt(), 0xFF22D3EE.toInt(), "🎙")
                AssistantState.LISTENING -> Triple(0xFF0891B2.toInt(), 0xFF67E8F9.toInt(), "◉")
                AssistantState.THINKING -> Triple(0xFFB45309.toInt(), 0xFFFBBF24.toInt(), "✦")
                AssistantState.SPEAKING -> Triple(0xFF047857.toInt(), 0xFF34D399.toInt(), "🔊")
                AssistantState.CONFIRMING -> Triple(0xFFB91C1C.toInt(), 0xFFFCA5A5.toInt(), "!")
                AssistantState.ERROR -> Triple(0xFF7F1D1D.toInt(), 0xFFF87171.toInt(), "✕")
            }
            orb.text = glyph
            orb.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(dp(3), strokeColor)
            }
            val scale = if (state == AssistantState.LISTENING || state == AssistantState.THINKING) 1.08f else 1.0f
            orb.animate().scaleX(scale).scaleY(scale).setDuration(180).start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(240)
        }
        layoutParams = params

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val orbSize = dp(60)
        val orb = TextView(context).apply {
            text = "🎙"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Voice Control AI Floating Mic"
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF0E2A3A.toInt())
                setStroke(dp(3), 0xFF22D3EE.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(orbSize, orbSize)
        }

        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xEE0F172A.toInt())
                setStroke(dp(1), 0xFF334155.toInt())
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            layoutParams = lp
        }

        val menuEntries = listOf(
            "Home" to QuickOverlayAction.HOME,
            "Back" to QuickOverlayAction.BACK,
            "Screenshot" to QuickOverlayAction.SCREENSHOT,
            "Stop AI" to QuickOverlayAction.STOP_TASK,
            "Open App" to QuickOverlayAction.OPEN_APP,
            "Hide Mic" to QuickOverlayAction.HIDE_OVERLAY
        )
        menuEntries.forEach { (label, action) ->
            val itemView = TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                minHeight = dp(44)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    toggleQuickMenu(false)
                    onQuickAction(action)
                }
            }
            menu.addView(itemView)
        }

        // Touch handling: Tap vs Drag vs Long-Press
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            if (!moved) {
                longPressTriggered = true
                toggleQuickMenu(!isMenuOpen)
            }
        }

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    longPressTriggered = false
                    mainHandler.postDelayed(longPressRunnable, 520L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        moved = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(wrapper, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!moved && !longPressTriggered && event.action == MotionEvent.ACTION_UP) {
                        if (isMenuOpen) {
                            toggleQuickMenu(false)
                        } else {
                            onTapMic()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        wrapper.addView(orb)
        wrapper.addView(menu)

        runCatching {
            windowManager.addView(wrapper, params)
            rootContainer = wrapper
            orbButton = orb
            quickMenuContainer = menu
            isShowing = true
        }
    }

    private fun toggleQuickMenu(open: Boolean) {
        isMenuOpen = open
        quickMenuContainer?.visibility = if (open) View.VISIBLE else View.GONE
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
