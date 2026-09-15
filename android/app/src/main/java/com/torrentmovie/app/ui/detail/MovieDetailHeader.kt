package com.torrentmovie.app.ui.detail

import com.torrentmovie.app.ui.util.openYoutubeTrailer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.torrentmovie.core.data.MovieMetadata

@Composable
fun MovieDetailHeader(
    metadata: MovieMetadata,
    releaseLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (!metadata.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = metadata.posterUrl,
                contentDescription = metadata.title,
                modifier = Modifier
                    .width(96.dp)
                    .height(144.dp),
                contentScale = ContentScale.Crop,
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
            metadata.trailerYoutubeKey?.let { key ->
                TextButton(
                    onClick = { openYoutubeTrailer(context, key) },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Watch trailer", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
fun MoviePosterPlaceholder(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
