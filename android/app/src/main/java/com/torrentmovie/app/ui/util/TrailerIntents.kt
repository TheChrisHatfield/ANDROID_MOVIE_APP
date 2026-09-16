package com.torrentmovie.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.torrentmovie.app.TrailerActivity

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

private val YOUTUBE_ID_IN_URL = Regex("""(?:[?&]v=|youtu\.be/|/embed/)([\w-]{6,})""")

internal fun normalizeYoutubeVideoId(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    YOUTUBE_ID_IN_URL.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
    if (!trimmed.contains("/") && !trimmed.contains("?")) return trimmed
    return null
}

internal fun youtubeEmbedUrl(videoId: String): String {
    return "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1"
}

fun openYoutubeTrailer(context: Context, youtubeKey: String) {
    val key = normalizeYoutubeVideoId(youtubeKey) ?: return

    val externalIntents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key")).setPackage(YOUTUBE_PACKAGE),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$key")),
    )
    for (intent in externalIntents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            continue
        }
    }

    context.startActivity(
        Intent(context, TrailerActivity::class.java)
            .putExtra(TrailerActivity.EXTRA_VIDEO_ID, key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
