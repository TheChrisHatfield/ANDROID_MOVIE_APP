package com.torrentmovie.app.ui.util

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun InlineYoutubePlayer(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
    fixedAspectRatio: Boolean = true,
    requireTapToPlay: Boolean = true,
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
    var awaitingTap by remember(videoId) { mutableStateOf(requireTapToPlay) }
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

    fun startPlayback() {
        awaitingTap = false
        webViewRef?.loadYoutubeEmbed(
            videoId = videoId,
            autoplay = true,
            muted = false,
        )
    }

    Box(modifier = playerModifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                WebView(context).apply {
                    tag = videoId
                    configureForYoutubeEmbed()
                }
            },
            update = { webView ->
                webViewRef = webView
                if (webView.tag != videoId) {
                    webView.tag = videoId
                    awaitingTap = requireTapToPlay
                }
                if (!awaitingTap && webView.url.isNullOrBlank()) {
                    webView.loadYoutubeEmbed(
                        videoId = videoId,
                        autoplay = autoplay,
                        muted = false,
                    )
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
        if (awaitingTap) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black)
                    .clickable { startPlayback() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play trailer",
                        tint = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Text(
                        text = "Tap to play trailer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
