package com.torrentmovie.app.ui.util

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

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
    return "https://www.youtube.com/embed/$videoId?autoplay=$autoplayFlag&playsinline=1&rel=0"
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
