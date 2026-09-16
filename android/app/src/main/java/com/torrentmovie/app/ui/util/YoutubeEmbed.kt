package com.torrentmovie.app.ui.util

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

private val YOUTUBE_ID_IN_URL = Regex("""(?:[?&]v=|youtu\.be/|/embed/)([\w-]{11})""")
private val YOUTUBE_VIDEO_ID = Regex("""^[\w-]{11}$""")

/** Origin used as base URL for the IFrame API page (app package as https origin). */
const val YOUTUBE_APP_ORIGIN = "https://com.torrentmovie.app"

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

/** HTML page that hosts the YouTube IFrame Player API (proven pattern from android-youtube-player). */
private fun youtubePlayerHtml(videoId: String, autoplay: Boolean): String {
    val autoplayFlag = if (autoplay) 1 else 0
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="referrer" content="strict-origin-when-cross-origin">
          <style>
            html, body { height: 100%; width: 100%; margin: 0; padding: 0; background: #000; overflow: hidden; position: fixed; }
            #youTubePlayerDOM { height: 100%; width: 100%; }
          </style>
        </head>
        <body>
          <div id="youTubePlayerDOM"></div>
          <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('youTubePlayerDOM', {
                height: '100%',
                width: '100%',
                videoId: '$videoId',
                playerVars: {
                  autoplay: $autoplayFlag,
                  mute: 0,
                  controls: 1,
                  enablejsapi: 1,
                  fs: 1,
                  rel: 0,
                  iv_load_policy: 3,
                  playsinline: 1,
                  origin: '$YOUTUBE_APP_ORIGIN'
                },
                events: {
                  onReady: function(event) { if ($autoplayFlag) event.target.playVideo(); },
                  onError: function(e) { console.log('YouTube player error', e.data); }
                }
              });
            }
            var tag = document.createElement('script');
            tag.src = 'https://www.youtube.com/iframe_api';
            var firstScriptTag = document.getElementsByTagName('script')[0];
            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
          </script>
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
    setBackgroundColor(Color.BLACK)
    isFocusable = true
    isFocusableInTouchMode = true
    webViewClient = WebViewClient()
    webChromeClient = WebChromeClient()
}

/** Load trailer via YouTube IFrame Player API (reliable playback; avoids Error 153 / black screen). */
fun WebView.loadYoutubeEmbed(
    videoId: String,
    autoplay: Boolean = true,
    muted: Boolean = false,
) {
    configureForYoutubeEmbed()
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    loadDataWithBaseURL(
        YOUTUBE_APP_ORIGIN,
        youtubePlayerHtml(videoId, autoplay),
        "text/html",
        "UTF-8",
        null,
    )
    onResume()
    resumeTimers()
}

/** Embed URL kept for tests and external use. */
fun youtubeEmbedUrl(
    videoId: String,
    autoplay: Boolean = true,
    muted: Boolean = false,
): String {
    val autoplayFlag = if (autoplay) 1 else 0
    val muteFlag = if (muted) 1 else 0
    return "https://www.youtube.com/embed/$videoId" +
        "?autoplay=$autoplayFlag&mute=$muteFlag&playsinline=1&rel=0&modestbranding=1" +
        "&controls=1&enablejsapi=1&fs=1&origin=$YOUTUBE_APP_ORIGIN"
}
