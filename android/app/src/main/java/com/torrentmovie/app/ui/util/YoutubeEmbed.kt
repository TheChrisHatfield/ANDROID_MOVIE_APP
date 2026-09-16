package com.torrentmovie.app.ui.util

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Referer/origin YouTube requires for WebView embed requests (app package as https origin). */
const val YOUTUBE_APP_REFERER = "https://com.torrentmovie.app"

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

fun youtubeEmbedUrl(
    videoId: String,
    autoplay: Boolean = true,
    muted: Boolean = false,
): String {
    val autoplayFlag = if (autoplay) 1 else 0
    val muteFlag = if (muted) 1 else 0
    val origin = URLEncoder.encode(YOUTUBE_APP_REFERER, StandardCharsets.UTF_8)
    return "https://www.youtube.com/embed/$videoId" +
        "?autoplay=$autoplayFlag&mute=$muteFlag&playsinline=1&rel=0&modestbranding=1" +
        "&controls=1&enablejsapi=1&fs=1&origin=$origin"
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
    setBackgroundColor(Color.BLACK)
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
}

/** Load YouTube embed with Referer header (required for playback; avoids Error 153 / black iframe). */
fun WebView.loadYoutubeEmbed(
    videoId: String,
    autoplay: Boolean = true,
    muted: Boolean = false,
) {
    configureForYoutubeEmbed()
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    val url = youtubeEmbedUrl(videoId, autoplay, muted)
    loadUrl(url, mapOf("Referer" to "$YOUTUBE_APP_REFERER/"))
    onResume()
    resumeTimers()
}
