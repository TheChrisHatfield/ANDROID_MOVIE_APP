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

@Composable
fun InlineYoutubePlayer(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewRef?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewRef?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                tag = videoId
                loadYoutubeEmbed(videoId, autoplay)
            }
        },
        update = { webView ->
            webViewRef = webView
            if (webView.tag != videoId) {
                webView.tag = videoId
                webView.loadYoutubeEmbed(videoId, autoplay)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.onPause()
            webView.destroy()
            if (webViewRef === webView) {
                webViewRef = null
            }
        },
    )
}
