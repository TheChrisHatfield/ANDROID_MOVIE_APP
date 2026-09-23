package com.torrentmovie.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.util.RemoteMoviePoster
import com.torrentmovie.core.data.MovieSearchSuggestion

@Composable
fun SearchSuggestionsPanel(
    suggestions: List<MovieSearchSuggestion>,
    loading: Boolean,
    searchApiBaseUrl: String,
    onSelect: (MovieSearchSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!loading && suggestions.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        if (loading && suggestions.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(
                    "Finding titles…",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
            ) {
                items(
                    suggestions,
                    key = { "${it.tmdbId}-${it.title}-${it.year}" },
                ) { suggestion ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RemoteMoviePoster(
                            url = suggestion.posterUrl,
                            title = suggestion.title,
                            searchApiBaseUrl = searchApiBaseUrl,
                            modifier = Modifier.size(width = 36.dp, height = 54.dp),
                        )
                        Text(
                            text = buildString {
                                append(suggestion.title)
                                suggestion.year?.let { append(" ($it)") }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
