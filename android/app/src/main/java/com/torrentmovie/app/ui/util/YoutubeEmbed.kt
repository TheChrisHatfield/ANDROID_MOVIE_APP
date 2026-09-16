package com.torrentmovie.app.ui.util

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Origin YouTube expects for embedded playback (app package as https origin). */
const val YOUTUBE_EMBED_ORIGIN = "https://com.torrentmovie.app"

private val YOUTUBE_ID_IN_URL = Regex("""(?:[?&]v=|youtu\.be/|/embed/)([\w-]{6,})""")

fun normalizeYoutubeVideoId(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    YOUTUBE_ID_IN_URL.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
    if (!trimmed.contains("/") && !trimmed.contains("?")) return trimmed
    return null
}

fun youtubeEmbedUrl(videoId: String, autoplay: Boolean = true): String {
    val autoplayFlag = if (autoplay) 1 else 0
    val origin = URLEncoder.encode(YOUTUBE_EMBED_ORIGIN, StandardCharsets.UTF_8)
    return "https://www.youtube.com/embed/$videoId" +
        "?autoplay=$autoplayFlag&playsinline=1&rel=0&modestbranding=1&origin=$origin"
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
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
}

/** Load embed via HTML wrapper + base URL so YouTube receives a valid Referer (Error 153 fix). */
fun WebView.loadYoutubeEmbed(videoId: String, autoplay: Boolean = true) {
    configureForYoutubeEmbed()
    loadDataWithBaseURL(
        YOUTUBE_EMBED_ORIGIN,
        youtubeEmbedHtml(videoId, autoplay),
        "text/html",
        "UTF-8",
        null,
    )
}
