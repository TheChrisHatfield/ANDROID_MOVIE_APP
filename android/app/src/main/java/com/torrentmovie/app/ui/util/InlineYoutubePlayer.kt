package com.torrentmovie.app.ui.util

import android.webkit.WebView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

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
    val embedUrl = remember(videoId, autoplay) { youtubeEmbedUrl(videoId, autoplay) }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                configureForYoutubeEmbed()
                loadUrl(embedUrl)
            }
        },
        update = { webView ->
            if (webView.url != embedUrl) {
                webView.loadUrl(embedUrl)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
