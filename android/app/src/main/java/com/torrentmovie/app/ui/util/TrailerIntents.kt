package com.torrentmovie.app.ui.util

import android.net.Uri
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.torrentmovie.app.TrailerActivity

private fun Context.launchTrailerIntent(intent: Intent) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/** Full-screen in-app trailer (same embed as inline player). */
fun openYoutubeTrailerFullscreen(context: Context, youtubeKey: String) {
    val key = normalizeYoutubeVideoId(youtubeKey)
    if (key == null) {
        Toast.makeText(context, "Trailer unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    context.launchTrailerIntent(
        Intent(context, TrailerActivity::class.java)
            .putExtra(TrailerActivity.EXTRA_VIDEO_ID, key),
    )
}

/** Open trailer in the YouTube app or system browser when the WebView embed fails. */
fun openYoutubeExternal(context: Context, youtubeKey: String) {
    val key = normalizeYoutubeVideoId(youtubeKey)
    if (key == null) {
        Toast.makeText(context, "Trailer unavailable", Toast.LENGTH_SHORT).show()
        return
    }
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$key"))
    if (appIntent.resolveActivity(context.packageManager) != null) {
        context.launchTrailerIntent(appIntent)
        return
    }
    context.launchTrailerIntent(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key")),
    )
}
