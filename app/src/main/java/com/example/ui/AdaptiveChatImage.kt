package com.example.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.concurrent.ConcurrentHashMap

/** Remembers each picture's real shape so bubbles do not jump when they scroll back in. */
private val imageRatioCache = ConcurrentHashMap<String, Float>()

/**
 * Chat picture that keeps the photo's own shape: a landscape photo gets a wide bubble, a
 * portrait photo a tall one — exactly what the full-screen viewer shows, instead of every
 * image being cropped into the same fixed box.
 */
@Composable
fun AdaptiveChatImage(
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp
) {
    var ratio by remember(url) { mutableFloatStateOf(imageRatioCache[url] ?: 0f) }
    // Until the size is known use a gentle 4:3 placeholder; extremes are clamped so a very long
    // screenshot or panorama cannot take over the whole screen.
    val shown = (if (ratio > 0f) ratio else 4f / 3f).coerceIn(0.56f, 1.9f)
    val widthCap = when {
        shown < 0.8f -> 210.dp      // portrait
        shown < 1.15f -> 240.dp     // square-ish
        else -> 300.dp              // landscape
    }
    AsyncImage(
        model = url,
        contentDescription = "Image attachment",
        contentScale = ContentScale.Crop,
        onSuccess = { state ->
            val w = state.painter.intrinsicSize.width
            val h = state.painter.intrinsicSize.height
            if (w > 0f && h > 0f) { val r = w / h; imageRatioCache[url] = r; ratio = r }
        },
        modifier = modifier
            .widthIn(max = widthCap)
            .fillMaxWidth()
            .aspectRatio(shown)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    )
}

/**
 * Screens that position their composer with `imePadding()` need the window in resize mode.
 * Without this the system also pans the window, so the input was pushed up twice and a big
 * empty gap appeared between the keyboard and the text box.
 */
@Composable
fun KeyboardResizeMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val token = Any()
        if (window != null) KeyboardModeStack.push(window, token)
        onDispose { if (window != null) KeyboardModeStack.pop(window, token) }
    }
}

/**
 * Keeps track of which screens currently want "resize" keyboard behaviour.
 *
 * v5.2.1 fix: the first version restored the old mode with `Window.setSoftInputMode(0)`, but
 * Android ignores the value 0 there — so after opening the AI chat once, the whole app stayed in
 * resize mode and the normal chat / group chat composer ended up hidden behind the keyboard.
 * The original mode is now written back directly to the window attributes, and nested screens
 * are counted so the mode is only restored when the last one leaves.
 */
private object KeyboardModeStack {
    private val holders = ArrayList<Any>()
    private var original: Int? = null

    fun push(window: android.view.Window, token: Any) {
        if (holders.isEmpty()) original = window.attributes.softInputMode
        holders += token
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    fun pop(window: android.view.Window, token: Any) {
        holders -= token
        if (holders.isEmpty()) {
            val restore = original ?: WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
            original = null
            // setSoftInputMode() ignores "unspecified", so assign the attribute itself.
            window.attributes = window.attributes.apply {
                softInputMode = (restore and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    (restore and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
            }
        }
    }
}
