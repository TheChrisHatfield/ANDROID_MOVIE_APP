package com.torrentmovie.app.ui.util

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
fun MoviePosterPlaceholder(title: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
fun RemoteMoviePoster(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    searchApiBaseUrl: String? = null,
) {
    val model = resolvePosterUrl(url, searchApiBaseUrl)
    if (model.isNullOrBlank()) {
        MoviePosterPlaceholder(title, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = model,
        contentDescription = title,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        error = { MoviePosterPlaceholder(title, Modifier.fillMaxSize()) },
    )
}
