package com.torrentmovie.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.util.InlineYoutubePlayer
import com.torrentmovie.app.ui.util.RemoteMoviePoster
import com.torrentmovie.app.ui.util.normalizeYoutubeVideoId
import com.torrentmovie.app.ui.util.openYoutubeTrailerFullscreen
import com.torrentmovie.core.data.MovieMetadata

@Composable
fun MovieDetailHeader(
    metadata: MovieMetadata,
    releaseLabel: String? = null,
    resultId: String? = null,
    searchApiBaseUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playableTrailerId = metadata.trailerYoutubeKey?.let { normalizeYoutubeVideoId(it) }
    var trailerVisible by remember(resultId, playableTrailerId) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            if (!metadata.posterUrl.isNullOrBlank()) {
                RemoteMoviePoster(
                    url = metadata.posterUrl,
                    title = metadata.title,
                    searchApiBaseUrl = searchApiBaseUrl,
                    modifier = Modifier
                        .width(96.dp)
                        .height(144.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (metadata.posterUrl.isNullOrBlank()) 0.dp else 12.dp),
            ) {
                Text(
                    text = buildString {
                        append(metadata.title)
                        metadata.year?.let { append(" ($it)") }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                releaseLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                metadata.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                playableTrailerId?.let { trailerId ->
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = { trailerVisible = !trailerVisible }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text(
                                if (trailerVisible) "Hide trailer" else "Play trailer",
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        if (trailerVisible) {
                            TextButton(
                                onClick = { openYoutubeTrailerFullscreen(context, trailerId) },
                            ) {
                                Icon(Icons.Default.Fullscreen, contentDescription = null)
                                Text("Fullscreen", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        if (trailerVisible && playableTrailerId != null) {
            InlineYoutubePlayer(
                youtubeKey = playableTrailerId,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
