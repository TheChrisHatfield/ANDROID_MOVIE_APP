package com.torrentmovie.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.AppSettings

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(container.settingsRepository.load()) }
    val settingsRevision by container.settingsRepository.revision.collectAsState()

    LaunchedEffect(settingsRevision) {
        settings = container.settingsRepository.load()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
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
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.tmdbApiKey,
            onValueChange = { settings = settings.copy(tmdbApiKey = it) },
            label = { Text("TMDB API key (posters & trailers)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Optional. Get a free key at themoviedb.org. Sent to your search API for metadata only.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text("Seedbox", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        OutlinedTextField(
            value = settings.rutorrentBaseUrl,
            onValueChange = { settings = settings.copy(rutorrentBaseUrl = it) },
            label = { Text("ruTorrent base URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.username,
            onValueChange = { settings = settings.copy(username = it) },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.password,
            onValueChange = { settings = settings.copy(password = it) },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.authScheme,
            onValueChange = { settings = settings.copy(authScheme = it.lowercase()) },
            label = { Text("Auth scheme (basic or digest)") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.downloadDirectory,
            onValueChange = { settings = settings.copy(downloadDirectory = it) },
            label = { Text("Magnet download folder") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Interface", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text("Theme follows system default (Material 3).")

        Button(
            onClick = {
                val validationError = container.settingsRepository.saveError(settings)
                val message = when {
                    validationError != null -> validationError
                    container.settingsRepository.save(settings) -> "Settings saved"
                    else -> "Failed to save settings"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(top = 16.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onChecked)
    Text(label)
}
