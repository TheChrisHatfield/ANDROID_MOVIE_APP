package com.torrentmovie.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.seedbox.SeedboxResult
import com.torrentmovie.core.network.TorrentResultDto
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
    onOpenUploaded: ((storageKey: String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var magnet by remember(resultId) { mutableStateOf(initialMagnet?.takeIf { it.isNotBlank() }) }
    var loading by remember(resultId) { mutableStateOf(false) }
    var duplicate by remember(resultId) { mutableStateOf(false) }
    var magnetError by remember(resultId) { mutableStateOf<String?>(null) }
    var magnetLoading by remember(resultId) { mutableStateOf(false) }
    var resultExpired by remember(resultId) { mutableStateOf(false) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()
    val settings = remember(settingsRevision) { container.settingsRepository.load() }
    val seedboxConfigured = settings.rutorrentBaseUrl.isNotBlank() &&
        settings.username.isNotBlank() &&
        settings.password.isNotBlank()
    val downloadDirConfigured = settings.downloadDirectory.trim().isNotBlank()
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())
    val metadataRevision by container.movieMetadataStore.revision.collectAsState()
    var resolveRequest by remember(resultId) { mutableIntStateOf(0) }
    var lastSearchApiUrl by remember(resultId) { mutableStateOf(settings.searchApiBaseUrl) }
    var metadata by remember(resultId) { mutableStateOf(container.movieMetadataStore.get(resultId)) }

    LaunchedEffect(resultId, settingsRevision, metadataRevision) {
        metadata = container.movieMetadataStore.get(resultId)
    }

    LaunchedEffect(settingsRevision) {
        val apiUrl = container.settingsRepository.load().searchApiBaseUrl
        if (apiUrl != lastSearchApiUrl) {
            lastSearchApiUrl = apiUrl
            resolveRequest++
        }
    }

    fun resolveUploadedKey(magnetValue: String? = magnet): String? {
        val magnetUri = magnetValue?.takeIf { it.isNotBlank() }
        if (magnetUri != null) {
            val key = MagnetHashUtil.storageKey(magnetUri, name, site)
            if (uploaded.any { it.infoHash.equals(key, ignoreCase = true) }) return key
        }
        return uploaded.find { it.displayName == name && it.site == site }?.infoHash
    }

    fun isAlreadyUploaded(magnetValue: String?): Boolean {
        return resolveUploadedKey(magnetValue) != null
    }

    LaunchedEffect(magnet, name, site, uploaded, magnetLoading) {
        if (magnetLoading) {
            duplicate = false
            return@LaunchedEffect
        }
        duplicate = isAlreadyUploaded(magnet)
    }

    LaunchedEffect(resultId, resolveRequest, settingsRevision) {
        if (resolveRequest == 0 && !magnet.isNullOrBlank()) return@LaunchedEffect
        if (resolveRequest > 0) {
            magnet = null
            magnetError = null
            resultExpired = false
        }
        val apiUrl = container.settingsRepository.load().searchApiBaseUrl
        if (apiUrl.isBlank()) {
            magnetLoading = false
            magnetError = "Configure Search API URL in Settings (e.g. http://<PC-IP>:8765)"
            return@LaunchedEffect
        }
        magnetLoading = true
        magnetError = null
        val cachedBeforeResolve = container.searchResultStore.get(resultId)
        try {
            val resolved = withContext(Dispatchers.IO) {
                container.searchRepository.resolveMagnet(
                    resultId = resultId,
                    detailUrl = cachedBeforeResolve?.detail_url,
                    site = cachedBeforeResolve?.site?.takeIf { it.isNotBlank() } ?: site,
                    name = cachedBeforeResolve?.name?.takeIf { it.isNotBlank() } ?: name,
                )
            }
            magnet = resolved.magnet.takeIf { it.isNotBlank() }
            if (!magnet.isNullOrBlank()) {
                val cached = container.searchResultStore.get(resultId)
                container.searchResultStore.put(
                    cached?.copy(magnet = magnet)
                        ?: TorrentResultDto(
                            id = resultId,
                            name = name,
                            site = site,
                            magnet = magnet,
                        ),
                )
            }
            if (magnet.isNullOrBlank()) {
                magnetError = "Magnet unavailable — tap retry"
            }
        } catch (e: SearchException) {
            magnet = null
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
        } catch (e: Exception) {
            magnet = null
            magnetError = e.message ?: "Magnet fetch failed"
        } finally {
            magnetLoading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val headerMetadata = metadata
        if (headerMetadata != null) {
            MovieDetailHeader(
                metadata = headerMetadata,
                releaseLabel = "$site · ${name.take(80)}",
                resultId = resultId,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text(site, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (duplicate) {
            Text("Sent", color = MaterialTheme.colorScheme.error)
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
                            container.seedboxRepository.addMagnet(m, name, site)
                        }
                        when (result) {
                            is SeedboxResult.Success -> {
                                val length = if (result.message.contains("history save failed")) {
                                    Toast.LENGTH_LONG
                                } else {
                                    Toast.LENGTH_SHORT
                                }
                                Toast.makeText(context, result.message, length).show()
                            }
                            is SeedboxResult.Failure ->
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                        duplicate = isAlreadyUploaded(m)
                    } catch (e: Exception) {
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
                magnetError == null && !magnet.isNullOrBlank() && !duplicate && !magnetLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(if (loading) "Sending…" else "Send to seedbox")
        }
        if (duplicate && onOpenUploaded != null) {
            TextButton(
                onClick = {
                    resolveUploadedKey()?.let { key -> onOpenUploaded(key) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text("View in Uploaded")
            }
        }
    }
}
