package com.torrentmovie.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.torrentmovie.core.data.seedbox.normalizeSearchApiUrl
import com.torrentmovie.core.data.seedbox.normalizeSeedboxUrl
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val searchApiBaseUrl: String = DEFAULT_SEARCH_API,
    val rutorrentBaseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val authScheme: String = "basic",
    val downloadDirectory: String = DEFAULT_DOWNLOAD_DIR,
    val movieSitesOnly: Boolean = true,
    val disclaimerAccepted: Boolean = false,
    val searchPages: Int = DEFAULT_SEARCH_PAGES,
    val tmdbApiKey: String = "",
    val fetchMovieMetadata: Boolean = true,
) {
    companion object {
        const val EMULATOR_SEARCH_API = "http://10.0.2.2:8765"
        const val DEFAULT_SEARCH_API = EMULATOR_SEARCH_API
        const val DEFAULT_DOWNLOAD_DIR = "/home5/chris82/downloads/MOVIES/"
        const val DEFAULT_SEARCH_PAGES = 2
    }
}

class SettingsRepository(
    private val context: Context,
    private val bundledSearchApiUrl: String = "",
) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val prefs: SharedPreferences = openSettingsPrefs(context)

    private fun openSettingsPrefs(context: Context): SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                "torrent_movie_settings",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            context.getSharedPreferences("torrent_movie_settings_plain", Context.MODE_PRIVATE)
        }
    }

    fun load(): AppSettings {
        val rawSearchApi = prefs.getString(KEY_SEARCH_API, null)
            ?: defaultSearchApiUrl()
        val rawRutorrent = prefs.getString(KEY_RUTORRENT_URL, "") ?: ""
        return AppSettings(
            searchApiBaseUrl = normalizeSearchApiUrl(rawSearchApi),
            rutorrentBaseUrl = if (rawRutorrent.isBlank()) "" else normalizeSeedboxUrl(rawRutorrent),
            username = (prefs.getString(KEY_USERNAME, "") ?: "").trim(),
            password = (prefs.getString(KEY_PASSWORD, "") ?: "").trim(),
            authScheme = normalizeLoadedAuthScheme(prefs.getString(KEY_AUTH_SCHEME, "basic") ?: "basic"),
            downloadDirectory = (prefs.getString(KEY_DOWNLOAD_DIR, AppSettings.DEFAULT_DOWNLOAD_DIR)
                ?: AppSettings.DEFAULT_DOWNLOAD_DIR).trim(),
            movieSitesOnly = prefs.getBoolean(KEY_MOVIE_SITES, true),
            disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER, false),
            searchPages = prefs.getInt(KEY_SEARCH_PAGES, AppSettings.DEFAULT_SEARCH_PAGES)
                .coerceIn(1, MAX_SEARCH_PAGES),
            tmdbApiKey = (prefs.getString(KEY_TMDB_API_KEY, "") ?: "").trim(),
            fetchMovieMetadata = prefs.getBoolean(KEY_FETCH_METADATA, true),
        )
    }

    fun hasUserConfiguredSearchApi(): Boolean = prefs.contains(KEY_SEARCH_API)

    /** Persist LAN/bundled bootstrap without overriding an explicit user save (FR-040). */
    fun applyAutoConfiguredSearchApi(url: String): Boolean {
        if (hasUserConfiguredSearchApi()) return false
        val normalized = normalizeSearchApiUrl(url)
        if (normalized.isBlank()) return false
        val committed = prefs.edit().putString(KEY_SEARCH_API, normalized).commit()
        if (!committed) return false
        _revision.value += 1
        return true
    }

    fun needsSearchApiAutoConfiguration(): Boolean = !hasUserConfiguredSearchApi()

    fun bundledSearchApiUrlForBootstrap(): String = normalizeSearchApiUrl(bundledSearchApiUrl)

    private fun defaultSearchApiUrl(): String {
        val bundled = normalizeSearchApiUrl(bundledSearchApiUrl)
        if (bundled.isNotBlank()) return bundled
        return if (DeviceProfile.isEmulator()) {
            AppSettings.EMULATOR_SEARCH_API
        } else {
            ""
        }
    }

    fun saveError(settings: AppSettings): String? = validateAppSettings(settings)

    fun save(settings: AppSettings): Boolean {
        if (validateAppSettings(settings) != null) return false
        val searchApiUrl = normalizeSearchApiUrl(settings.searchApiBaseUrl)
        val downloadDir = settings.downloadDirectory.trim()
        val rutorrentUrl = normalizeSeedboxUrl(settings.rutorrentBaseUrl)
        val authScheme = normalizeAuthScheme(settings.authScheme)
        val ok = prefs.edit()
            .putString(KEY_SEARCH_API, searchApiUrl)
            .putString(KEY_RUTORRENT_URL, rutorrentUrl)
            .putString(KEY_USERNAME, settings.username.trim())
            .putString(KEY_PASSWORD, settings.password.trim())
            .putString(KEY_AUTH_SCHEME, authScheme)
            .putString(KEY_DOWNLOAD_DIR, downloadDir)
            .putBoolean(KEY_MOVIE_SITES, settings.movieSitesOnly)
            .putBoolean(KEY_DISCLAIMER, settings.disclaimerAccepted)
            .putInt(KEY_SEARCH_PAGES, settings.searchPages.coerceIn(1, MAX_SEARCH_PAGES))
            .putString(KEY_TMDB_API_KEY, settings.tmdbApiKey.trim())
            .putBoolean(KEY_FETCH_METADATA, settings.fetchMovieMetadata)
            .commit()
        if (ok) {
            _revision.value += 1
        }
        return ok
    }

    fun isSeedboxConfigured(): Boolean {
        val s = load()
        return s.rutorrentBaseUrl.isNotBlank() && s.username.isNotBlank() && s.password.isNotBlank()
    }

    companion object {
        internal fun normalizeAuthScheme(value: String): String = value.trim().lowercase()

        internal fun normalizeLoadedAuthScheme(value: String): String {
            val normalized = normalizeAuthScheme(value)
            return if (normalized in setOf("basic", "digest")) normalized else "basic"
        }

        private const val KEY_SEARCH_API = "search_api_base_url"
        const val KEY_RUTORRENT_URL = "rutorrent_base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_AUTH_SCHEME = "auth_scheme"
        const val KEY_DOWNLOAD_DIR = "download_directory"
        const val KEY_MOVIE_SITES = "movie_sites_only"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
        private const val KEY_SEARCH_PAGES = "search_pages"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_FETCH_METADATA = "fetch_movie_metadata"
        private const val MAX_SEARCH_PAGES = 10
    }
}

internal fun validateAppSettings(settings: AppSettings): String? {
    val searchApiUrl = normalizeSearchApiUrl(settings.searchApiBaseUrl)
    if (searchApiUrl.isNotBlank() && !isValidHttpUrl(searchApiUrl)) {
        return "Invalid search API URL"
    }
    val downloadDir = settings.downloadDirectory.trim()
    if (downloadDir.isNotBlank() && !downloadDir.startsWith("/")) {
        return "Download folder must be an absolute path (start with /)"
    }
    val rutorrentUrl = normalizeSeedboxUrl(settings.rutorrentBaseUrl)
    if (settings.rutorrentBaseUrl.isNotBlank() && !isValidHttpUrl(rutorrentUrl)) {
        return "Invalid ruTorrent URL"
    }
    val authScheme = SettingsRepository.normalizeAuthScheme(settings.authScheme)
    if (authScheme !in setOf("basic", "digest")) {
        return "Auth scheme must be basic or digest"
    }
    if (settings.searchPages !in 1..10) {
        return "Search pages must be between 1 and 10"
    }
    return null
}

private fun isValidHttpUrl(url: String): Boolean {
    return try {
        val host = URI(url).host
        !host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}
