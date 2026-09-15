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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var magnet by remember(resultId) { mutableStateOf(initialMagnet?.takeIf { it.isNotBlank() }) }
    var loading by remember(resultId) { mutableStateOf(false) }
    var duplicate by remember(resultId) { mutableStateOf(false) }
    var magnetError by remember(resultId) { mutableStateOf<String?>(null) }
    var magnetLoading by remember(resultId) { mutableStateOf(false) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()
    val seedboxConfigured = remember(settingsRevision) {
        container.settingsRepository.isSeedboxConfigured()
    }
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())
    var resolveRequest by remember(resultId) { mutableIntStateOf(0) }
    val metadata = remember(resultId) { container.movieMetadataStore.get(resultId) }

    fun isAlreadyUploaded(magnetValue: String?): Boolean {
        val key = MagnetHashUtil.storageKey(magnetValue ?: "", name, site)
        if (uploaded.any { it.infoHash.equals(key, ignoreCase = true) }) return true
        if (!magnetValue.isNullOrBlank()) return false
        return uploaded.any { it.displayName == name && it.site == site }
    }

    LaunchedEffect(magnet, name, site, uploaded, magnetLoading) {
        if (magnetLoading) {
            duplicate = false
            return@LaunchedEffect
        }
        duplicate = isAlreadyUploaded(magnet)
    }

    LaunchedEffect(resultId, resolveRequest) {
        if (resolveRequest == 0 && !magnet.isNullOrBlank()) return@LaunchedEffect
        if (resolveRequest > 0) {
            magnet = null
        }
        magnetLoading = true
        magnetError = null
        try {
            val resolved = withContext(Dispatchers.IO) {
                container.searchRepository.resolveMagnet(resultId)
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
            magnetError = when {
                e.httpCode == 404 && e.message?.contains("expired", ignoreCase = true) == true ->
                    "Result expired — go back and search again"
                e.httpCode == 404 ->
                    "Magnet unavailable — tap retry"
                else -> e.message ?: "Magnet fetch failed"
            }
        } catch (e: Exception) {
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
        if (metadata != null) {
            MovieDetailHeader(
                metadata = metadata,
                releaseLabel = "$site · ${name.take(80)}",
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        } else {
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text(site, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (duplicate) {
            Text("Already uploaded", color = MaterialTheme.colorScheme.error)
        }
        if (magnetLoading) {
            Text("Loading magnet…")
        }
        magnetError?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { resolveRequest++ }) {
                Text("Retry magnet")
            }
        }
        if (!seedboxConfigured) {
            Text(
                "Configure ruTorrent URL and credentials in Settings before sending.",
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
                if (loading || magnetLoading || magnetError != null) return@Button
                loading = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            container.seedboxRepository.addMagnet(m, name, site)
                        }
                        when (result) {
                            is SeedboxResult.Success ->
                                Toast.makeText(context, "Sent to seedbox", Toast.LENGTH_SHORT).show()
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
            enabled = seedboxConfigured && !loading && !magnet.isNullOrBlank() &&
                !duplicate && !magnetLoading && magnetError == null,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(if (loading) "Sending…" else "Send to seedbox")
        }
    }
}
