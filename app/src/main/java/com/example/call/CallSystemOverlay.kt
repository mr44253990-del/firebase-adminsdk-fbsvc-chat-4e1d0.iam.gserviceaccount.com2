package com.example.call

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import com.example.service.ActiveChatState
import com.example.service.CallForegroundService

/**
 * System-wide floating call bar ("small call screen") that stays visible over OTHER apps and
 * even after the user leaves Convo Chat entirely, as long as a 1:1 or group call is running.
 *
 * Requires the user's "Display over other apps" (SYSTEM_ALERT_WINDOW) permission, which the
 * app already requests while a call is active. The bar shows the peer/group name, a live
 * timer, a speaking indicator, and End/Open actions; it is draggable anywhere on screen.
 */
object CallSystemOverlay {
    private var overlay: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var timerRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var openIntent: Intent? = null
    private var callId: String = ""
    private var titleText = "Convo Chat call"
    private var titleView: TextView? = null

    /** Shows or hides the overlay based on call state, app visibility and permission. */
    fun update(context: Context, openCallIntent: Intent? = null, callTitle: String? = null, id: String? = null) {
        openIntent = openCallIntent ?: openIntent
        callId = id ?: callId
        titleText = callTitle ?: titleText
        val app = context.applicationContext
        val privateLive = CallEngine.state.value.status !in listOf("idle", "ended", "declined", "missed", "failed")
        val groupLive = GroupCallEngine.state.value.status !in listOf("idle", "ended", "failed")
        val active = privateLive || groupLive
        val permitted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(app)
        if (active && permitted && !ActiveChatState.appVisible) show(app) else hide(app)
    }

    fun hide(context: Context? = null) {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        overlay?.let { view ->
            runCatching {
                (view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.removeView(view)
            }
        }
        overlay = null
        params = null
        titleView = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun show(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        if (overlay != null) {
            // Already visible: just refresh the title.
            titleView?.text = titleText
            return
        }
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.parseColor("#F0101826"))
                setStroke(dp(1), Color.parseColor("#667C5CFF"))
            }
            background = bg
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }

        val icon = TextView(context).apply {
            text = "📞"
            textSize = 16f
        }
        chip.addView(icon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })

        val title = TextView(context).apply {
            text = titleText
            setTextColor(Color.WHITE)
            textSize = 13f
            maxLines = 1
        }
        titleView = title
        val status = TextView(context).apply {
            text = "00:00"
            setTextColor(0xFFB9C4D6.toInt())
            textSize = 11f
            maxLines = 1
        }
        val textColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(title)
        textColumn.addView(status)
        chip.addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        val end = TextView(context).apply {
            text = "End"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            background = bg
            setOnClickListener {
                if (callId.startsWith("group:")) GroupCallEngine.end() else CallEngine.end()
                CallForegroundService.stop(it.context.applicationContext)
                hide()
            }
        }
        chip.addView(end, LinearLayout.LayoutParams(dp(52), dp(40)))

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(90)
        }

        // Drag anywhere; a small tap (no drag) reopens the full call screen.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        chip.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = layoutParams.x; startY = layoutParams.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    layoutParams.x = startX + dx.toInt()
                    layoutParams.y = startY + dy.toInt()
                    runCatching { windowManager.updateViewLayout(view, layoutParams) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                        openIntent?.let { intent ->
                            runCatching {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                context.startActivity(intent)
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(chip, layoutParams)
            overlay = chip
            params = layoutParams
            startTicker(status)
        }
    }

    /** Live timer + "speaking" hint so the user always knows the call is alive. */
    private fun startTicker(status: TextView) {
        val tick = object : Runnable {
            override fun run() {
                if (overlay == null) return
                val private = CallEngine.state.value
                val group = GroupCallEngine.state.value
                val connectedAt = if (private.status !in listOf("idle", "ended", "declined", "missed", "failed"))
                    private.connectedAt else group.connectedAt
                val seconds = if (connectedAt > 0) ((System.currentTimeMillis() - connectedAt) / 1000L).coerceAtLeast(0) else 0L
                val speaking = CallEngine.remoteSpeaking.value
                status.text = when {
                    speaking -> "Speaking... ${"%02d:%02d".format(seconds / 60, seconds % 60)}"
                    else -> "%02d:%02d".format(seconds / 60, seconds % 60)
                }
                handler.postDelayed(this, 1000)
            }
        }
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = tick
        handler.post(tick)
    }
}
