package com.example.ui

import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.graphicsLayer

private const val TIKTOK_HOME = "https://www.tiktok.com/"

/**
 * TikTok stays in Android WebView: app/deep-link schemes are blocked, while web URLs
 * continue inside this view. The small DOM helper only tries the exact “Not now” label
 * when TikTok presents its app-open prompt; it never auto-accepts login or permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TikTokBrowserScreen(onClose: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(TIKTOK_HOME) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf("") }
    var fullScreenVideoView by remember { mutableStateOf<View?>(null) }
    var fullScreenVideoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitFullScreenVideo() {
        val activeView = fullScreenVideoView
        val callback = fullScreenVideoCallback
        fullScreenVideoView = null
        fullScreenVideoCallback = null
        (activeView?.parent as? ViewGroup)?.removeView(activeView)
        callback?.onCustomViewHidden()
    }

    fun syncNavigation(view: WebView?) {
        if (view == null) return
        currentUrl = view.url ?: TIKTOK_HOME
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    BackHandler(enabled = true) {
        if (fullScreenVideoView != null) {
            exitFullScreenVideo()
        } else {
            val view = webView
            if (view?.canGoBack() == true) {
                view.goBack()
                syncNavigation(view)
            } else {
                onClose()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFullScreenVideo()
            webView?.let { view ->
                runCatching { view.stopLoading() }
                runCatching { view.webChromeClient = null }
                runCatching { view.webViewClient = WebViewClient() }
                runCatching { (view.parent as? android.view.ViewGroup)?.removeView(view) }
                runCatching { view.destroy() }
            }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (fullScreenVideoView == null) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close TikTok and return to Convo Chat")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("TikTok", fontWeight = FontWeight.Bold, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = Uri.parse(currentUrl).host ?: "www.tiktok.com",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { webView?.goBack(); syncNavigation(webView) }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
                    }
                    IconButton(onClick = { webView?.goForward(); syncNavigation(webView) }, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Next page", modifier = Modifier.rotate180())
                    }
                    IconButton(onClick = { pageError = ""; webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                }
                if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.allowFileAccessFromFileURLs = false
                        settings.allowUniversalAccessFromFileURLs = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        }
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        CookieManager.getInstance().setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) syncNavigation(view)
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null || fullScreenVideoView != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }
                                fullScreenVideoView = view
                                fullScreenVideoCallback = callback
                            }

                            override fun onHideCustomView() {
                                exitFullScreenVideo()
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme.orEmpty().lowercase()
                                // Keep regular web navigation in this browser. Refuse app-store,
                                // TikTok-app, intent://, and other external-app launches.
                                return scheme != "https" && scheme != "http"
                            }

                            @Suppress("DEPRECATION")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                                val scheme = Uri.parse(url).scheme.orEmpty().lowercase()
                                return scheme != "https" && scheme != "http"
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                pageError = ""
                                progress = 5
                                syncNavigation(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                progress = 100
                                syncNavigation(view)
                                if (isTikTokHost(view?.url)) view?.evaluateJavascript(NOT_NOW_PROMPT_SCRIPT, null)
                            }

                            @Suppress("DEPRECATION")
                            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                if (failingUrl == view?.url) pageError = description ?: "TikTok could not load in this WebView"
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) pageError = error?.description?.toString() ?: "TikTok could not load in this WebView"
                            }
                        }
                        webView = this
                        loadUrl(TIKTOK_HOME)
                    }
                },
                update = { view -> if (webView !== view) webView = view }
            )

            if (fullScreenVideoView != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            fullScreenVideoView?.let { active ->
                                (active.parent as? ViewGroup)?.removeView(active)
                                addView(active, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                            }
                        }
                    },
                    update = { container ->
                        val active = fullScreenVideoView ?: return@AndroidView
                        if (active.parent !== container) {
                            (active.parent as? ViewGroup)?.removeView(active)
                            container.addView(active, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                        }
                    }
                )
                IconButton(
                    onClick = ::exitFullScreenVideo,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(.56f), CircleShape)
                ) { Icon(Icons.Default.Close, "Exit full-screen video", tint = Color.White) }
            }

            if (pageError.isNotBlank() && fullScreenVideoView == null) {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(22.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
                    tonalElevation = 5.dp
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("TikTok did not load", fontWeight = FontWeight.Bold)
                        Text("$pageError\nThe TikTok website may block some embedded WebViews. Try reload or turn TikTok mode off in Admin Settings.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { pageError = ""; webView?.reload() }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Reload") }
                    }
                }
            }
        }
    }
}

private fun isTikTokHost(url: String?): Boolean {
    val host = runCatching { Uri.parse(url.orEmpty()).host?.lowercase().orEmpty() }.getOrDefault("")
    return host == "tiktok.com" || host.endsWith(".tiktok.com")
}

private val NOT_NOW_PROMPT_SCRIPT = """
(function(){
  function clickNotNow(){
    try {
      var candidates = Array.from(document.querySelectorAll('button,[role="button"],a'));
      var target = candidates.find(function(node){
        var text = (node.innerText || node.textContent || node.getAttribute('aria-label') || '')
          .replace(/\s+/g, ' ').trim().toLowerCase();
        return text === 'not now';
      });
      if (target && target.getClientRects().length) { target.click(); return true; }
    } catch (_) {}
    return false;
  }
  var tries = 0;
  var timer = setInterval(function(){
    tries++;
    if (clickNotNow() || tries >= 24) clearInterval(timer);
  }, 500);
})();
""".trimIndent()

private fun Modifier.rotate180(): Modifier = this.graphicsLayer(rotationZ = 180f)
