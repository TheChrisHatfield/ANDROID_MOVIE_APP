package com.torrentmovie.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import com.torrentmovie.app.ui.theme.missyOutlinedTextFieldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.torrentmovie.app.ui.adaptive.ResponsiveContent
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(container.settingsRepository.load()) }
    var saving by remember { mutableStateOf(false) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()

    LaunchedEffect(settingsRevision) {
        settings = container.settingsRepository.load()
    }

    ResponsiveContent(scroll = true) {
        Text("General", style = MaterialTheme.typography.titleMedium)
        Text(
            if (settings.disclaimerAccepted) "Legal disclaimer accepted" else "Legal disclaimer not yet accepted",
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text("Search", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = settings.searchApiBaseUrl,
            onValueChange = { settings = settings.copy(searchApiBaseUrl = it) },
            label = { Text("Search API base URL") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        RowSwitch("Movie sites only", settings.movieSitesOnly) {
            settings = settings.copy(movieSitesOnly = it)
        }
        OutlinedTextField(
            value = settings.searchPages.toString(),
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }
                val pages = digits.toIntOrNull() ?: AppSettings.DEFAULT_SEARCH_PAGES
                settings = settings.copy(searchPages = pages.coerceIn(1, 10))
            },
            label = { Text("Indexer pages per search (1–10)") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.tmdbApiKey,
            onValueChange = { settings = settings.copy(tmdbApiKey = it) },
            label = { Text("TMDB API key (posters & trailers)") },
            visualTransformation = PasswordVisualTransformation(),
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Required for posters and descriptions. Free key at themoviedb.org — paste here or set TMDB_API_KEY in services/search/.env on your PC.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        RowSwitch("Fetch posters & trailers", settings.fetchMovieMetadata) {
            settings = settings.copy(fetchMovieMetadata = it)
        }

        Text("Seedbox", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = settings.rutorrentBaseUrl,
            onValueChange = { settings = settings.copy(rutorrentBaseUrl = it) },
            label = { Text("ruTorrent base URL") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Example: https://yourname.snow.seedhost.eu/rutorrent/ — do not paste addtorrent.php.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = settings.username,
            onValueChange = { settings = settings.copy(username = it) },
            label = { Text("Username") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.password,
            onValueChange = { settings = settings.copy(password = it) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.authScheme,
            onValueChange = { settings = settings.copy(authScheme = it.lowercase()) },
            label = { Text("Auth scheme (basic or digest)") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.downloadDirectory,
            onValueChange = { settings = settings.copy(downloadDirectory = it) },
            label = { Text("Magnet download folder") },
            colors = missyOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Interface", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Theme follows system default (Material 3).")
        Text(
            "Wide screens (tablet, fold unfolded, landscape): search and detail appear side by side; navigation uses the side rail.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        Button(
            onClick = {
                if (saving) return@Button
                val validationError = container.settingsRepository.saveError(settings)
                if (validationError != null) {
                    Toast.makeText(context, validationError, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (!container.settingsRepository.save(settings)) {
                    Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                saving = true
                scope.launch {
                    try {
                        val message = if (container.settingsRepository.isSeedboxConfigured()) {
                            val probe = withContext(Dispatchers.IO) {
                                container.seedboxRepository.probeSeedbox()
                            }
                            settingsSavedMessage(seedboxConfigured = true, probe = probe)
                        } else {
                            settingsSavedMessage(seedboxConfigured = false, probe = null)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Toast.makeText(context, "Settings saved", Toast.LENGTH_LONG).show()
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = !saving,
            modifier = Modifier.padding(top = 16.dp),
        ) { Text(if (saving) "Saving…" else "Save") }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
