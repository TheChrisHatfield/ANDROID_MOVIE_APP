package com.torrentmovie.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.theme.missyFilledButtonColors
import com.torrentmovie.app.ui.theme.seedboxSendButtonColors
import com.torrentmovie.app.ui.util.magnetFallbackDetailUrl
import com.torrentmovie.app.ui.util.shouldClearMagnetLoading
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.magnetDownloadDirectory
import com.torrentmovie.core.data.seedbox.SeedboxResult
import com.torrentmovie.core.network.TorrentResultDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TorrentDetailScreen(
    container: AppContainer,
    resultId: String,
    name: String,
    site: String,
    initialMagnet: String?,
    onResultExpired: (() -> Unit)? = null,
    onResultIdChanged: ((String) -> Unit)? = null,
    onOpenUploaded: ((storageKey: String) -> Unit)? = null,
    onGenreBranchFeedback: ((success: Boolean) -> Unit)? = null,
    detailUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var magnet by remember(resultId) { mutableStateOf(initialMagnet?.takeIf { it.isNotBlank() }) }
    var loading by remember(resultId) { mutableStateOf(false) }
    var duplicate by remember(resultId) { mutableStateOf(false) }
    var justSentStorageKey by remember(resultId) { mutableStateOf<String?>(null) }
    var magnetError by remember(resultId) { mutableStateOf<String?>(null) }
    var magnetLoading by remember(resultId) { mutableStateOf(false) }
    var resultExpired by remember(resultId) { mutableStateOf(false) }
    var pendingPersist by remember(resultId) { mutableStateOf(false) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()
    val settings = remember(settingsRevision) { container.settingsRepository.load() }
    val seedboxConfigured = settings.rutorrentBaseUrl.isNotBlank() &&
        settings.username.isNotBlank() &&
        settings.password.isNotBlank()
    val storedReleaseName = remember(resultId, settingsRevision) {
        container.searchResultStore.get(resultId)?.name
    }
    val downloadDirConfigured = settings.magnetDownloadDirectory(name, storedReleaseName).isNotBlank()
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())
    val metadataRevision by container.movieMetadataStore.revision.collectAsState()
    var resolveRequest by remember(resultId) { mutableIntStateOf(0) }
    var magnetFetchGeneration by remember { mutableIntStateOf(0) }
    var metadata by remember(resultId) { mutableStateOf(container.movieMetadataStore.get(resultId)) }

    LaunchedEffect(resultId, settingsRevision, metadataRevision) {
        metadata = container.movieMetadataStore.get(resultId)
    }

    LaunchedEffect(initialMagnet) {
        if (magnet.isNullOrBlank() && !initialMagnet.isNullOrBlank()) {
            magnet = initialMagnet
            magnetError = null
        }
    }

    LaunchedEffect(magnet, name, site, settingsRevision) {
        val current = magnet ?: initialMagnet
        pendingPersist = !current.isNullOrBlank() &&
            container.seedboxRepository.isPendingPersist(current, name, site)
    }

    fun resolveUploadedKey(magnetValue: String? = magnet): String? {
        val magnetUri = magnetValue?.takeIf { it.isNotBlank() } ?: return null
        val key = MagnetHashUtil.storageKey(magnetUri, name, site)
        if (uploaded.any { it.infoHash.equals(key, ignoreCase = true) }) return key
        return null
    }

    fun isAlreadyUploaded(magnetValue: String?): Boolean {
        return !magnetValue.isNullOrBlank() && resolveUploadedKey(magnetValue) != null
    }

    LaunchedEffect(magnet, name, site, uploaded, magnetLoading) {
        if (magnetLoading) {
            duplicate = false
            return@LaunchedEffect
        }
        duplicate = isAlreadyUploaded(magnet)
        if (duplicate) {
            justSentStorageKey = null
        }
    }

    fun uploadedNavigationKey(): String? {
        resolveUploadedKey()?.let { return it }
        return justSentStorageKey
    }

    LaunchedEffect(resultId, resolveRequest, settingsRevision) {
        if (resolveRequest == 0 && !magnet.isNullOrBlank()) return@LaunchedEffect
        if (resolveRequest > 0) {
            magnet = null
            magnetError = null
            resultExpired = false
        }
        val generation = ++magnetFetchGeneration
        magnetLoading = true
        magnetError = null
        val cachedBeforeResolve = container.searchResultStore.get(resultId)
        try {
            val resolved = withContext(Dispatchers.IO) {
                container.searchRepository.resolveMagnet(
                    resultId = resultId,
                    detailUrl = magnetFallbackDetailUrl(cachedBeforeResolve?.detail_url, detailUrl),
                    site = cachedBeforeResolve?.site?.takeIf { it.isNotBlank() } ?: site,
                    name = cachedBeforeResolve?.name?.takeIf { it.isNotBlank() } ?: name,
                )
            }
            if (!shouldClearMagnetLoading(generation, magnetFetchGeneration)) {
                return@LaunchedEffect
            }
            magnet = resolved.magnet.takeIf { it.isNotBlank() }
            if (!magnet.isNullOrBlank()) {
                val cached = container.searchResultStore.get(resultId)
                val stableId = resolved.id.takeIf { it.isNotBlank() } ?: resultId
                val merged = (cached ?: TorrentResultDto(
                    id = resultId,
                    name = name,
                    site = site,
                    detail_url = detailUrl,
                )).copy(
                    id = stableId,
                    magnet = magnet,
                    detail_url = cached?.detail_url?.takeIf { it.isNotBlank() } ?: detailUrl,
                )
                container.searchResultStore.put(merged)
                if (stableId != resultId) {
                    container.searchResultStore.remove(resultId)
                    container.movieMetadataStore.get(resultId)?.let { meta ->
                        container.movieMetadataStore.put(stableId, meta)
                        container.movieMetadataStore.remove(resultId)
                    }
                    container.foldActiveSelection
                        ?.takeIf { it.resultId == resultId }
                        ?.let { pending ->
                            container.foldActiveSelection = pending.copy(
                                resultId = stableId,
                                name = merged.name.takeIf { it.isNotBlank() } ?: pending.name,
                                site = merged.site.takeIf { it.isNotBlank() } ?: pending.site,
                                detailUrl = merged.detail_url?.takeIf { it.isNotBlank() }
                                    ?: pending.detailUrl,
                            )
                        }
                    onResultIdChanged?.invoke(stableId)
                }
            }
            if (magnet.isNullOrBlank()) {
                magnetError = "Magnet unavailable — tap retry"
            }
        } catch (e: SearchException) {
            if (!shouldClearMagnetLoading(generation, magnetFetchGeneration)) {
                return@LaunchedEffect
            }
            if (magnet.isNullOrBlank()) {
                magnet = null
            }
            val expired = e.httpCode == 404 &&
                (
                    e.message?.contains("not found or expired", ignoreCase = true) == true ||
                        e.message?.contains("expired", ignoreCase = true) == true
                    )
            if (expired) {
                resultExpired = true
                container.searchResultStore.remove(resultId)
                container.movieMetadataStore.remove(resultId)
            }
            magnetError = when {
                expired -> "Result expired — search again"
                e.httpCode == 404 && e.message?.contains("Magnet unavailable", ignoreCase = true) == true ->
                    "Magnet unavailable — tap retry"
                e.httpCode == 404 -> "Result not in cache — tap retry or search again"
                else -> e.message ?: "Magnet fetch failed"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!shouldClearMagnetLoading(generation, magnetFetchGeneration)) {
                return@LaunchedEffect
            }
            if (magnet.isNullOrBlank()) {
                magnet = null
            }
            magnetError = e.message ?: "Magnet fetch failed"
        } finally {
            if (shouldClearMagnetLoading(generation, magnetFetchGeneration)) {
                magnetLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 96.dp),
        ) {
        val headerMetadata = detailHeaderMetadata(
            metadata,
            container.searchResultStore.get(resultId),
            name,
        )
        if (headerMetadata != null) {
            MovieDetailHeader(
                metadata = headerMetadata,
                releaseLabel = "$site · ${name.take(80)}",
                resultId = resultId,
                searchApiBaseUrl = settings.searchApiBaseUrl,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text(site, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (duplicate) {
            Text("Sent", color = MaterialTheme.colorScheme.error)
        } else if (pendingPersist) {
            Text("Sent to seedbox — saving history…", color = MaterialTheme.colorScheme.primary)
        }
        if (magnetLoading) {
            Text("Loading magnet…")
        }
        magnetError?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
            if (resultExpired) {
                Button(
                    onClick = { onResultExpired?.invoke() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = missyFilledButtonColors(),
                ) {
                    Text("Search again")
                }
            } else {
                TextButton(onClick = { resolveRequest++ }) {
                    Text("Retry magnet")
                }
            }
        }
        if (!seedboxConfigured) {
            Text(
                "Configure ruTorrent URL and credentials in Settings before sending.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        } else if (!downloadDirConfigured) {
            Text(
                "Set download folder in Settings before sending.",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(
                    onClick = {
                        val m = magnet
                        if (m.isNullOrBlank()) {
                            Toast.makeText(context, "Magnet not available", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (loading || magnetLoading) return@Button
                        loading = true
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    val releaseName = container.searchResultStore.get(resultId)?.name
                                    val catalogIsTv = container.movieMetadataStore.get(resultId)?.isTv == true
                                    container.seedboxRepository.addMagnet(
                                        m,
                                        name,
                                        site,
                                        releaseName,
                                        catalogIsTv,
                                    )
                                }
                                when (result) {
                                    is SeedboxResult.Success -> {
                                        val persistFailed = result.message.contains(
                                            "history save failed",
                                            ignoreCase = true,
                                        )
                                        val wasRetryingPersist = pendingPersist
                                        pendingPersist = persistFailed
                                        if (shouldRecordGenreRankingSuccess(wasRetryingPersist)) {
                                            onGenreBranchFeedback?.invoke(true)
                                        }
                                        if (!persistFailed) {
                                            duplicate = true
                                            justSentStorageKey = MagnetHashUtil.storageKey(m, name, site)
                                        } else {
                                            duplicate = false
                                        }
                                        val length = if (persistFailed) {
                                            Toast.LENGTH_LONG
                                        } else {
                                            Toast.LENGTH_SHORT
                                        }
                                        Toast.makeText(context, result.message, length).show()
                                    }
                                    is SeedboxResult.Failure -> {
                                        if (isGenreRankingSendFailure(result.message)) {
                                            onGenreBranchFeedback?.invoke(false)
                                        }
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                onGenreBranchFeedback?.invoke(false)
                                Toast.makeText(
                                    context,
                                    e.message ?: "Send failed",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = seedboxConfigured && downloadDirConfigured && !loading &&
                        !magnet.isNullOrBlank() && !duplicate && !magnetLoading && !resultExpired,
                    modifier = Modifier.fillMaxWidth(),
                    colors = seedboxSendButtonColors(),
                ) {
                    Text(
                        when {
                            loading -> "Sending…"
                            pendingPersist -> "Retry save to Uploaded"
                            else -> "Send to seedbox"
                        },
                    )
                }
                if (duplicate && onOpenUploaded != null && uploadedNavigationKey() != null) {
                    TextButton(
                        onClick = {
                            uploadedNavigationKey()?.let { key -> onOpenUploaded(key) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View in Uploaded")
                    }
                }
            }
        }
    }
}
