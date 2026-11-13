package com.example.call

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import kotlin.math.abs

/**
 * System-wide floating call bubble. While an AUDIO call (private or group) is running and the
 * user leaves Convo Chat completely, the same small draggable call card stays on top of every
 * other app (needs "Display over other apps"). It shows who you are talking to and a live timer;
 * tap it to come back, or hang up straight from the bubble. Video calls keep using
 * Picture-in-Picture.
 */
object CallFloatingBubble {
    private val main = Handler(Looper.getMainLooper())
    private var app: Application? = null
    private var startedActivities = 0
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var shownKey = ""
    private var muteView: TextView? = null
    private var lastX = -1
    private var lastY = -1

    private data class Live(val key: String, val name: String, val image: String, val connectedAt: Long, val group: Boolean, val muted: Boolean, val status: String)

    fun install(application: Application) {
        if (app != null) return
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++; main.post { refresh() } }
            override fun onActivityStopped(activity: Activity) { startedActivities = (startedActivities - 1).coerceAtLeast(0); main.postDelayed({ refresh() }, 350) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        main.post(ticker)
    }

    /** Re-evaluates once a second so the bubble follows call state without extra plumbing. */
    private val ticker = object : Runnable {
        override fun run() {
            runCatching { refresh() }
            main.postDelayed(this, if (bubble != null || liveCall() != null) 1000L else 2500L)
        }
    }

    private fun liveCall(): Live? {
        val p = CallEngine.state.value
        if (p.status in listOf("connected", "reconnecting", "calling", "ringing")) {
            if (p.video || p.screenSharing) return null
            return Live("p:" + p.callId, p.remoteName.ifBlank { "Convo Chat call" }, p.remoteImage, p.connectedAt, false, p.muted, p.status)
        }
        val g = GroupCallEngine.state.value
        if (g.status in listOf("connected", "reconnecting", "active")) {
            if (g.video || g.screenSharing) return null
            return Live("g:" + g.roomId, g.groupName.ifBlank { "Group call" }, "", g.connectedAt, true, g.muted, g.status)
        }
        return null
    }

    private fun refresh() {
        val context = app ?: return
        val live = liveCall()
        val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        val shouldShow = live != null && startedActivities == 0 && canDraw && !CallPipState.inPip.value
        if (!shouldShow) { hide(); return }
        if (bubble == null || shownKey != live!!.key) { hide(); show(context, live!!) }
        muteView?.text = if (live!!.muted) "🔇" else "🎙️"
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun show(context: Context, live: Live) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 8f), dp(context, 8f), dp(context, 8f), dp(context, 8f))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF21B1440.toInt(), 0xF2132B4A.toInt())).apply {
                cornerRadius = dp(context, 30f).toFloat()
                setStroke(dp(context, 1.5f), 0xFF2EE8A0.toInt())
            }
            elevation = dp(context, 10f).toFloat()
        }
        val avatarSize = dp(context, 44f)
        val avatarBox = FrameLayout(context)
        val initial = TextView(context).apply {
            text = live.name.trim().take(1).uppercase().ifBlank { "C" }
            setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7C5CFC.toInt(), 0xFF19C3B1.toInt())).apply { shape = GradientDrawable.OVAL }
        }
        val photo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        avatarBox.addView(initial, FrameLayout.LayoutParams(avatarSize, avatarSize))
        avatarBox.addView(photo, FrameLayout.LayoutParams(avatarSize, avatarSize))
        if (live.image.startsWith("http")) runCatching {
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(live.image).transformations(CircleCropTransformation()).target(photo).build())
        }
        card.addView(avatarBox, LinearLayout.LayoutParams(avatarSize, avatarSize))

        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(context, 10f), 0, dp(context, 8f), 0) }
        texts.addView(TextView(context).apply {
            text = live.name; setTextColor(Color.WHITE); textSize = 13.5f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(context, 120f)
        })
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF2EE8A0.toInt()) }
        }, LinearLayout.LayoutParams(dp(context, 7f), dp(context, 7f)).apply { rightMargin = dp(context, 5f) })
        if (live.connectedAt > 0L) {
            row.addView(Chronometer(context).apply {
                base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - live.connectedAt).coerceAtLeast(0L)
                setTextColor(0xFFB9F5DC.toInt()); textSize = 12f
                start()
            })
        } else row.addView(TextView(context).apply { text = if (live.status == "ringing") "Ringing…" else "Calling…"; setTextColor(0xFFB9F5DC.toInt()); textSize = 12f })
        row.addView(TextView(context).apply { text = if (live.group) "  · Group" else "  · On call"; setTextColor(0x99FFFFFF.toInt()); textSize = 11f })
        texts.addView(row)
        card.addView(texts)

        val mute = TextView(context).apply {
            text = if (live.muted) "🔇" else "🎙️"; textSize = 15f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
            setOnClickListener { if (live.group) GroupCallEngine.toggleMute() else CallEngine.toggleMute() }
        }
        muteView = mute
        card.addView(mute, LinearLayout.LayoutParams(dp(context, 38f), dp(context, 38f)).apply { rightMargin = dp(context, 6f) })
        val end = TextView(context).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            contentDescription = "End call"
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE5394B.toInt()) }
            setOnClickListener {
                if (live.group) GroupCallEngine.end() else CallEngine.end()
                hide()
            }
        }
        card.addView(end, LinearLayout.LayoutParams(dp(context, 38f), dp(context, 38f)))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (lastX >= 0) lastX else dp(context, 12f)
            y = if (lastY >= 0) lastY else dp(context, 96f)
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        card.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0); lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        lastX = lp.x; lastY = lp.y
                        runCatching { wm.updateViewLayout(card, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) openCall(context, live); true }
                else -> false
            }
        }
        runCatching {
            wm.addView(card, lp)
            bubble = card; params = lp; shownKey = live.key
        }.onFailure { bubble = null; muteView = null }
    }

    private fun openCall(context: Context, live: Live) {
        val intent = Intent(context, com.example.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (live.group) {
                val g = GroupCallEngine.state.value
                action = "com.ebchat.OPEN_ACTIVE_GROUP_CALL"
                putExtra("groupId", g.groupId); putExtra("roomId", g.roomId); putExtra("groupName", g.groupName)
                putStringArrayListExtra("memberIds", ArrayList(GroupCallEngine.memberIds())); putExtra("videoCall", g.video)
            } else action = "com.ebchat.OPEN_ACTIVE_CALL"
        }
        runCatching { context.startActivity(intent) }
    }

    private fun hide() {
        val view = bubble ?: return
        val context = app ?: return
        runCatching { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
        bubble = null; params = null; muteView = null; shownKey = ""
    }
}
