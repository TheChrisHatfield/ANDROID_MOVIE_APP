package com.torrentmovie.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.torrentmovie.app.ui.theme.TorrentMovieTheme
import com.torrentmovie.app.ui.util.youtubeEmbedUrl

class TrailerActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
        if (videoId.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            TorrentMovieTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Trailer") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close",
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl(youtubeEmbedUrl(videoId))
                            }
                        },
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }
}
