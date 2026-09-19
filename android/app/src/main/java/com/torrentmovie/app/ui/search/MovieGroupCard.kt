package com.torrentmovie.app.ui.search

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.util.InlineYoutubePlayer
import com.torrentmovie.app.ui.util.RemoteMoviePoster
import com.torrentmovie.app.ui.util.normalizeYoutubeVideoId
import com.torrentmovie.app.ui.util.openYoutubeTrailerFullscreen
import com.torrentmovie.core.network.MovieGroupDto
import com.torrentmovie.core.network.TorrentResultDto

internal fun groupContainsSelectedRelease(
    group: MovieGroupDto,
    selectedResultId: String?,
): Boolean {
    if (selectedResultId.isNullOrBlank()) return false
    return group.releases.any { it.id == selectedResultId }
}

internal fun shouldOfferListTrailer(groupSelected: Boolean): Boolean = !groupSelected

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
    searchApiBaseUrl: String? = null,
) {
    val context = LocalContext.current
    val singleRelease = group.releases.size == 1
    val playableTrailerId = group.trailerYoutubeKey?.let { normalizeYoutubeVideoId(it) }
    val posterUrl = group.posterUrl?.takeIf { it.isNotBlank() }
        ?: group.releases.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl
    val groupSelected = groupContainsSelectedRelease(group, selectedResultId)
    val cardShape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .then(
                if (groupSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = {
                if (singleRelease) {
                    onOpenRelease(group.releases.first())
                } else {
                    onToggleExpand()
                }
            }),
        shape = cardShape,
        colors = if (groupSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RemoteMoviePoster(
                url = posterUrl,
                title = group.title,
                searchApiBaseUrl = searchApiBaseUrl,
                modifier = Modifier
                    .width(72.dp)
                    .height(108.dp),
            )
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
                    if (isAlreadyUploaded(release)) {
                        Text(
                            text = "Sent",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
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
                    playableTrailerId?.takeIf { shouldOfferListTrailer(groupSelected) }?.let {
                        TextButton(onClick = onToggleTrailer) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (trailerPlaying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = if (trailerPlaying) "Hide trailer" else "Play trailer",
                                modifier = Modifier.padding(start = 4.dp),
                                color = if (trailerPlaying) {
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
        if (trailerPlaying && playableTrailerId != null) {
            InlineYoutubePlayer(
                youtubeKey = playableTrailerId,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            )
            TextButton(
                onClick = { openYoutubeTrailerFullscreen(context, playableTrailerId) },
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            ) {
                Text("Fullscreen")
            }
        }
        if (expanded && !singleRelease) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                Text(
                    text = "Tap a release to select",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
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
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        shape = shape,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        elevation = if (selected) {
            CardDefaults.cardElevation(defaultElevation = 4.dp)
        } else {
            CardDefaults.cardElevation(defaultElevation = 0.dp)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.site,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    text = "${release.size ?: "?"} · seeds ${release.seeds ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (selected) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
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
