package com.torrentmovie.app.ui.util

import android.webkit.WebView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private data class PlayerLoadTag(
    val videoId: String,
    val autoplay: Boolean,
    val muted: Boolean,
)

@Composable
fun InlineYoutubePlayer(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    fixedAspectRatio: Boolean = true,
    startMuted: Boolean = true,
) {
    val videoId = remember(youtubeKey) { normalizeYoutubeVideoId(youtubeKey) }
    if (videoId == null) {
        Text(
            text = "Trailer unavailable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        return
    }
    val muted = autoplay && startMuted
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, webViewRef) {
        val webView = webViewRef
        if (webView == null) {
            return@DisposableEffect onDispose {}
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.resumeTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val playerModifier = if (fixedAspectRatio) {
        modifier.fillMaxWidth().aspectRatio(16f / 9f)
    } else {
        modifier
    }

    fun loadPlayer(webView: WebView) {
        webView.loadYoutubeEmbed(
            videoId = videoId,
            autoplay = autoplay,
            muted = muted,
        )
    }

    AndroidView(
        modifier = playerModifier,
        factory = { context ->
            WebView(context).apply {
                tag = PlayerLoadTag(videoId, autoplay, muted)
                loadPlayer(this)
            }
        },
        update = { webView ->
            webViewRef = webView
            val expectedTag = PlayerLoadTag(videoId, autoplay, muted)
            if (webView.tag != expectedTag) {
                webView.tag = expectedTag
                loadPlayer(webView)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
            if (webViewRef === webView) {
                webViewRef = null
            }
        },
    )
}
