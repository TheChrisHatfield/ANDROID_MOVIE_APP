package com.torrentmovie.app.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.torrentmovie.app.ui.util.InlineYoutubePlayer
import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto

@Composable
fun MovieGroupCard(
    group: MovieGroupDto,
    expanded: Boolean,
    selectedResultId: String?,
    trailerPlaying: Boolean,
    isAlreadyUploaded: (TorrentResultDto) -> Boolean,
    onToggleExpand: () -> Unit,
    onToggleTrailer: () -> Unit,
    onOpenRelease: (TorrentResultDto) -> Unit,
) {
    val singleRelease = group.releaseCount == 1 && group.releases.size == 1
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = {
                if (singleRelease) {
                    onOpenRelease(group.releases.first())
                } else {
                    onToggleExpand()
                }
            }),
        colors = CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!group.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = group.posterUrl,
                    contentDescription = group.title,
                    modifier = Modifier
                        .width(72.dp)
                        .height(108.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Card(
                    modifier = Modifier
                        .width(72.dp)
                        .height(108.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = group.title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = buildString {
                        append(group.title)
                        group.year?.let { append(" ($it)") }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (group.releaseCount == 1) "1 release" else "${group.releaseCount} releases",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                if (singleRelease) {
                    val release = group.releases.first()
                    Text(
                        text = "${release.site} · ${release.size ?: "?"} · seeds ${release.seeds ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                group.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!singleRelease) {
                        TextButton(onClick = onToggleExpand) {
                            Text(if (expanded) "Hide releases" else "Show releases")
                        }
                    }
                    group.trailerYoutubeKey?.let { key ->
                        IconButton(onClick = onToggleTrailer) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = if (trailerPlaying) {
                                    "Hide trailer"
                                } else {
                                    "Play trailer"
                                },
                                tint = if (trailerPlaying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
        if (trailerPlaying && !group.trailerYoutubeKey.isNullOrBlank()) {
            InlineYoutubePlayer(
                youtubeKey = group.trailerYoutubeKey!!,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
        if (expanded && !singleRelease) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                group.releases.forEach { release ->
                    CompactReleaseRow(
                        release = release,
                        alreadyUploaded = isAlreadyUploaded(release),
                        selected = release.id == selectedResultId,
                        onClick = { onOpenRelease(release) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactReleaseRow(
    release: TorrentResultDto,
    alreadyUploaded: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${release.site} · ${release.size ?: "?"} · seeds ${release.seeds ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (alreadyUploaded) {
                Text(
                    text = "Sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
