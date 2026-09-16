package com.torrentmovie.app.ui.util

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Origin/referrer YouTube accepts for embedded WebView playback. */
const val YOUTUBE_EMBED_ORIGIN = "https://www.youtube.com"

private val YOUTUBE_ID_IN_URL = Regex("""(?:[?&]v=|youtu\.be/|/embed/)([\w-]{11})""")
private val YOUTUBE_VIDEO_ID = Regex("""^[\w-]{11}$""")

fun normalizeYoutubeVideoId(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    YOUTUBE_ID_IN_URL.find(trimmed)?.groupValues?.getOrNull(1)?.let { candidate ->
        return candidate.takeIf { YOUTUBE_VIDEO_ID.matches(it) }
    }
    if (!trimmed.contains("/") && !trimmed.contains("?")) {
        return trimmed.takeIf { YOUTUBE_VIDEO_ID.matches(it) }
    }
    return null
}

fun youtubeEmbedUrl(videoId: String, autoplay: Boolean = true): String {
    val autoplayFlag = if (autoplay) 1 else 0
    val origin = URLEncoder.encode(YOUTUBE_EMBED_ORIGIN, StandardCharsets.UTF_8)
    return "https://www.youtube.com/embed/$videoId" +
        "?autoplay=$autoplayFlag&playsinline=1&rel=0&modestbranding=1" +
        "&enablejsapi=1&fs=1&origin=$origin"
}

fun youtubeEmbedHtml(videoId: String, autoplay: Boolean = true): String {
    val src = youtubeEmbedUrl(videoId, autoplay)
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            html, body { margin: 0; padding: 0; background: #000; height: 100%; overflow: hidden; }
            iframe { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
          </style>
        </head>
        <body>
          <iframe
            src="$src"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            referrerpolicy="strict-origin-when-cross-origin"
            allowfullscreen
          ></iframe>
        </body>
        </html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
fun WebView.configureForYoutubeEmbed() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.loadWithOverviewMode = true
    settings.useWideViewPort = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    settings.userAgentString = settings.userAgentString.replace("; wv)", ")")
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
}

/** Load embed with Referer + base URL so YouTube accepts WebView playback (Error 153 / black screen). */
fun WebView.loadYoutubeEmbed(videoId: String, autoplay: Boolean = true) {
    configureForYoutubeEmbed()
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    val referer = "$YOUTUBE_EMBED_ORIGIN/"
    loadDataWithBaseURL(
        referer,
        youtubeEmbedHtml(videoId, autoplay),
        "text/html",
        "UTF-8",
        null,
    )
    onResume()
}
