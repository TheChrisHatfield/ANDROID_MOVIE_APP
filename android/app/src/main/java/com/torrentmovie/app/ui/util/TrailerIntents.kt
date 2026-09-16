package com.torrentmovie.app.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

private val YOUTUBE_ID_IN_URL = Regex("""(?:[?&]v=|youtu\.be/|/embed/)([\w-]{6,})""")

internal fun normalizeYoutubeVideoId(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    YOUTUBE_ID_IN_URL.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
    if (!trimmed.contains("/") && !trimmed.contains("?")) return trimmed
    return null
}

fun openYoutubeTrailer(context: Context, youtubeKey: String) {
    val key = normalizeYoutubeVideoId(youtubeKey) ?: return

    val youtubeApp = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("vnd.youtube:$key"),
    ).setPackage(YOUTUBE_PACKAGE)

    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/watch?v=$key"),
    )

    try {
        context.startActivity(youtubeApp)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to play trailer", Toast.LENGTH_SHORT).show()
        }
    }
}
