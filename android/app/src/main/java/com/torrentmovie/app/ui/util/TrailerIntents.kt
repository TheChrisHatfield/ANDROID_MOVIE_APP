package com.torrentmovie.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openYoutubeTrailer(context: Context, youtubeKey: String) {
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/watch?v=$youtubeKey"),
    )
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "No app available to play trailer", Toast.LENGTH_SHORT).show()
    }
}
