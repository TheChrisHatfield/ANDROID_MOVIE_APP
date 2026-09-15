package com.torrentmovie.app.ui.detail

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.SearchException
import com.torrentmovie.core.data.seedbox.SeedboxResult
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
    var magnet by remember { mutableStateOf(initialMagnet?.takeIf { it.isNotBlank() }) }
    var loading by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }
    var magnetError by remember { mutableStateOf<String?>(null) }
    var magnetLoading by remember { mutableStateOf(false) }
    val seedboxConfigured = remember { container.settingsRepository.isSeedboxConfigured() }
    val uploaded by container.uploadedRepository.observeAll().collectAsState(initial = emptyList())

    suspend fun resolveMagnet() {
        magnetLoading = true
        magnetError = null
        try {
            val resolved = container.searchRepository.resolveMagnet(resultId)
            magnet = resolved.magnet
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

    LaunchedEffect(magnet, name, site, uploaded) {
        duplicate = container.uploadedRepository.isUploaded(magnet, name, site)
    }

    LaunchedEffect(resultId) {
        if (magnet.isNullOrBlank()) {
            resolveMagnet()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(site, modifier = Modifier.padding(vertical = 8.dp))
        if (duplicate) {
            Text("Already uploaded", color = MaterialTheme.colorScheme.error)
        }
        if (magnetLoading) {
            Text("Loading magnet…")
        }
        magnetError?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { scope.launch { resolveMagnet() } }) {
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
                if (loading) return@Button
                loading = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        container.seedboxRepository.addMagnet(m, name, site)
                    }
                    loading = false
                    when (result) {
                        is SeedboxResult.Success ->
                            Toast.makeText(context, "Sent to seedbox", Toast.LENGTH_SHORT).show()
                        is SeedboxResult.Failure ->
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                    duplicate = container.uploadedRepository.isUploaded(m, name, site)
                }
            },
            enabled = seedboxConfigured && !loading && !magnet.isNullOrBlank() && !duplicate && !magnetLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(if (loading) "Sending…" else "Send to seedbox")
        }
    }
}
