package com.torrentmovie.app.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.torrentmovie.app.TrailerActivity

private fun Context.launchTrailerIntent(intent: Intent) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

/** Full-screen in-app trailer (same embed as inline player). */
fun openYoutubeTrailerFullscreen(context: Context, youtubeKey: String) {
    val key = normalizeYoutubeVideoId(youtubeKey) ?: return
    context.launchTrailerIntent(
        Intent(context, TrailerActivity::class.java)
            .putExtra(TrailerActivity.EXTRA_VIDEO_ID, key),
    )
}
