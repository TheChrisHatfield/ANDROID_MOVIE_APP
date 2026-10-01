package com.torrentmovie.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.torrentmovie.core.data.search.SearchApiAutoConfig
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
    val tvDownloadDirectory: String = DEFAULT_TV_DOWNLOAD_DIR,
    val contentFilter: SearchContentFilter = SearchContentFilter.MOVIES,
    val disclaimerAccepted: Boolean = false,
    val searchPages: Int = DEFAULT_SEARCH_PAGES,
    val tmdbApiKey: String = "",
    val fetchMovieMetadata: Boolean = true,
) {
    val movieSitesOnly: Boolean get() = contentFilter == SearchContentFilter.MOVIES

    companion object {
        const val EMULATOR_SEARCH_API = "http://10.0.2.2:8765"
        const val DEFAULT_SEARCH_API = EMULATOR_SEARCH_API
        const val DEFAULT_DOWNLOAD_DIR = "/home5/chris82/downloads/MOVIES/"
        const val DEFAULT_TV_DOWNLOAD_DIR = "/home5/chris82/downloads/TVSHOWS/"
        const val DEFAULT_SEARCH_PAGES = 2
    }
}

fun AppSettings.magnetDownloadDirectory(displayName: String): String {
    val useTv = contentFilter == SearchContentFilter.TV ||
        com.torrentmovie.core.data.ondevice.SizeFilters.isLikelyTvShow(displayName)
    return (if (useTv) tvDownloadDirectory else downloadDirectory).trim()
}

class SettingsRepository(
    private val context: Context,
    private val bundledSearchApiUrl: String = "",
    private val bundledTmdbApiKey: String = "",
) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _searchApiBootstrapGeneration = MutableStateFlow(0)
    val searchApiBootstrapGeneration: StateFlow<Int> = _searchApiBootstrapGeneration.asStateFlow()

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
        } catch (_: Throwable) {
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
            tvDownloadDirectory = (prefs.getString(KEY_TV_DOWNLOAD_DIR, AppSettings.DEFAULT_TV_DOWNLOAD_DIR)
                ?: AppSettings.DEFAULT_TV_DOWNLOAD_DIR).trim(),
            contentFilter = SearchContentFilter.fromStored(
                prefs.getString(KEY_CONTENT_FILTER, null),
                prefs.getBoolean(KEY_MOVIE_SITES, true),
            ),
            disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER, false),
            searchPages = prefs.getInt(KEY_SEARCH_PAGES, AppSettings.DEFAULT_SEARCH_PAGES)
                .coerceIn(1, MAX_SEARCH_PAGES),
            tmdbApiKey = resolveTmdbApiKey(
                userHasStoredKey = hasUserConfiguredTmdb(),
                storedKey = prefs.getString(KEY_TMDB_API_KEY, null),
                bundledKey = bundledTmdbApiKey,
            ),
            fetchMovieMetadata = prefs.getBoolean(KEY_FETCH_METADATA, true),
        )
    }

    fun hasBundledTmdbApiKey(): Boolean = bundledTmdbApiKey.isNotBlank()

    fun hasUserConfiguredTmdb(): Boolean = prefs.contains(KEY_TMDB_API_KEY)

    /** Persist operator-bundled TMDB key on first launch (mirrors FR-040 Search API bootstrap). */
    fun applyBundledTmdbIfNeeded(): Boolean {
        if (hasUserConfiguredTmdb()) return false
        val key = bundledTmdbApiKey.trim()
        if (key.isBlank()) return false
        val committed = prefs.edit().putString(KEY_TMDB_API_KEY, key).commit()
        if (committed) {
            _revision.value += 1
        }
        return committed
    }

    fun hasManualSearchApiOverride(): Boolean =
        prefs.getBoolean(KEY_SEARCH_API_MANUAL, false)

    fun hasUserConfiguredSearchApi(): Boolean = prefs.contains(KEY_SEARCH_API)

    /** Persist operator-bundled Search API URL on first launch (FR-040), same as TMDB. */
    fun applyBundledSearchApiIfNeeded(wifiIpv4: String? = null): Boolean {
        if (!shouldAdaptSearchApiToNetwork()) return false
        val url = bundledSearchApiUrlForBootstrap()
        if (url.isBlank()) return false
        val host = SearchApiAutoConfig.ipv4Host(url)
        if (host != null && !SearchApiAutoConfig.isOnWifiSubnet(url, wifiIpv4)) {
            return false
        }
        if (load().searchApiBaseUrl.isNotBlank() && !needsSearchApiAutoConfiguration()) return false
        return applyAutoConfiguredSearchApi(url)
    }

    fun clearAutoConfiguredSearchApi(): Boolean {
        if (hasManualSearchApiOverride()) return false
        val previous = normalizeSearchApiUrl(prefs.getString(KEY_SEARCH_API, "") ?: "")
        val committed = prefs.edit()
            .putString(KEY_SEARCH_API, "")
            .putBoolean(KEY_SEARCH_API_MANUAL, false)
            .commit()
        if (!committed) return false
        if (previous.isNotBlank()) {
            _searchApiBootstrapGeneration.value += 1
            _revision.value += 1
        }
        return true
    }

    /** Persist LAN/bundled bootstrap without overriding an explicit Settings URL (FR-040). */
    fun applyAutoConfiguredSearchApi(url: String): Boolean {
        if (!shouldAdaptSearchApiToNetwork()) return false
        val normalized = normalizeSearchApiUrl(url)
        if (normalized.isBlank()) return false
        val committed = prefs.edit()
            .putString(KEY_SEARCH_API, normalized)
            .putBoolean(KEY_SEARCH_API_MANUAL, false)
            .commit()
        if (!committed) return false
        _searchApiBootstrapGeneration.value += 1
        _revision.value += 1
        return true
    }

    fun markSearchApiRebound() {
        _searchApiBootstrapGeneration.value += 1
        _revision.value += 1
    }

    fun needsSearchApiAutoConfiguration(): Boolean {
        return SearchApiAutoConfig.needsInitialAutoConfiguration(
            hasManualOverride = hasManualSearchApiOverride(),
            storedSearchApiUrl = prefs.getString(KEY_SEARCH_API, null),
            isEmulator = DeviceProfile.isEmulator(),
        )
    }

    fun shouldAdaptSearchApiToNetwork(): Boolean {
        return SearchApiAutoConfig.shouldAdaptToNetworkChanges(
            hasManualOverride = hasManualSearchApiOverride(),
            storedSearchApiUrl = prefs.getString(KEY_SEARCH_API, null),
            isEmulator = DeviceProfile.isEmulator(),
        )
    }

    fun isSearchApiUsableOnThisNetwork(): Boolean = true

    fun searchApiBlockedMessage(): String =
        SearchApiMessages.blocked(
            needsSearchApiAutoConfiguration() ||
                (shouldAdaptSearchApiToNetwork() && !isSearchApiUsableOnThisNetwork()),
        )

    fun acceptDisclaimer(): Boolean {
        val ok = prefs.edit().putBoolean(KEY_DISCLAIMER, true).commit()
        if (ok) {
            _revision.value += 1
        }
        return ok
    }

    fun bundledSearchApiUrlForBootstrap(): String = normalizeSearchApiUrl(bundledSearchApiUrl)

    private fun defaultSearchApiUrl(): String = ""

    fun saveError(settings: AppSettings): String? = validateAppSettings(settings)

    fun save(settings: AppSettings): Boolean {
        if (validateAppSettings(settings) != null) return false
        val searchApiUrl = normalizeSearchApiUrl(settings.searchApiBaseUrl)
        val downloadDir = settings.downloadDirectory.trim()
        val tvDownloadDir = settings.tvDownloadDirectory.trim()
        val rutorrentUrl = normalizeSeedboxUrl(settings.rutorrentBaseUrl)
        val authScheme = normalizeAuthScheme(settings.authScheme)
        val previousSearch = prefs.getString(KEY_SEARCH_API, null)
        val wasManual = hasManualSearchApiOverride()
        val editor = prefs.edit()
        if (searchApiUrl.isBlank()) {
            editor.remove(KEY_SEARCH_API)
            editor.putBoolean(KEY_SEARCH_API_MANUAL, false)
        } else {
            val manual = SearchApiAutoConfig.manualFlagAfterSettingsSave(
                previousStoredUrl = previousSearch,
                newUrl = searchApiUrl,
                wasManual = wasManual,
                bundledUrl = bundledSearchApiUrlForBootstrap(),
            )
            editor.putString(KEY_SEARCH_API, searchApiUrl)
            editor.putBoolean(KEY_SEARCH_API_MANUAL, manual)
        }
        val tmdb = settings.tmdbApiKey.trim()
        if (tmdb.isBlank()) {
            editor.remove(KEY_TMDB_API_KEY)
        } else {
            editor.putString(KEY_TMDB_API_KEY, tmdb)
        }
        val ok = editor
            .putString(KEY_RUTORRENT_URL, rutorrentUrl)
            .putString(KEY_USERNAME, settings.username.trim())
            .putString(KEY_PASSWORD, settings.password.trim())
            .putString(KEY_AUTH_SCHEME, authScheme)
            .putString(KEY_DOWNLOAD_DIR, downloadDir)
            .putString(KEY_TV_DOWNLOAD_DIR, tvDownloadDir)
            .putBoolean(KEY_MOVIE_SITES, settings.contentFilter == SearchContentFilter.MOVIES)
            .putString(KEY_CONTENT_FILTER, settings.contentFilter.storedValue)
            .putBoolean(KEY_DISCLAIMER, settings.disclaimerAccepted)
            .putInt(KEY_SEARCH_PAGES, settings.searchPages.coerceIn(1, MAX_SEARCH_PAGES))
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
        private const val KEY_SEARCH_API_MANUAL = "search_api_manual"
        const val KEY_RUTORRENT_URL = "rutorrent_base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_AUTH_SCHEME = "auth_scheme"
        const val KEY_DOWNLOAD_DIR = "download_directory"
        const val KEY_TV_DOWNLOAD_DIR = "tv_download_directory"
        const val KEY_MOVIE_SITES = "movie_sites_only"
        private const val KEY_CONTENT_FILTER = "search_content_filter"
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
    val tvDownloadDir = settings.tvDownloadDirectory.trim()
    if (tvDownloadDir.isNotBlank() && !tvDownloadDir.startsWith("/")) {
        return "TV download folder must be an absolute path (start with /)"
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
