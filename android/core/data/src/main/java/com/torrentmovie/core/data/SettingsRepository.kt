package com.torrentmovie.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
) {
    companion object {
        const val DEFAULT_SEARCH_API = "http://10.0.2.2:8765"
        const val DEFAULT_DOWNLOAD_DIR = "/home5/chris82/downloads/MOVIES/"
    }
}

class SettingsRepository(context: Context) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "torrent_movie_settings",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): AppSettings = AppSettings(
        searchApiBaseUrl = prefs.getString(KEY_SEARCH_API, AppSettings.DEFAULT_SEARCH_API) ?: AppSettings.DEFAULT_SEARCH_API,
        rutorrentBaseUrl = prefs.getString(KEY_RUTORRENT_URL, "") ?: "",
        username = prefs.getString(KEY_USERNAME, "") ?: "",
        password = prefs.getString(KEY_PASSWORD, "") ?: "",
        authScheme = prefs.getString(KEY_AUTH_SCHEME, "basic") ?: "basic",
        downloadDirectory = prefs.getString(KEY_DOWNLOAD_DIR, AppSettings.DEFAULT_DOWNLOAD_DIR)
            ?: AppSettings.DEFAULT_DOWNLOAD_DIR,
        movieSitesOnly = prefs.getBoolean(KEY_MOVIE_SITES, true),
        disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER, false),
    )

    fun save(settings: AppSettings): Boolean {
        val ok = prefs.edit()
            .putString(KEY_SEARCH_API, settings.searchApiBaseUrl.trimEnd('/'))
            .putString(KEY_RUTORRENT_URL, settings.rutorrentBaseUrl.trimEnd('/') + "/")
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_PASSWORD, settings.password)
            .putString(KEY_AUTH_SCHEME, settings.authScheme)
            .putString(KEY_DOWNLOAD_DIR, settings.downloadDirectory)
            .putBoolean(KEY_MOVIE_SITES, settings.movieSitesOnly)
            .putBoolean(KEY_DISCLAIMER, settings.disclaimerAccepted)
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

    private companion object {
        const val KEY_SEARCH_API = "search_api_base_url"
        const val KEY_RUTORRENT_URL = "rutorrent_base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_AUTH_SCHEME = "auth_scheme"
        const val KEY_DOWNLOAD_DIR = "download_directory"
        const val KEY_MOVIE_SITES = "movie_sites_only"
        const val KEY_DISCLAIMER = "disclaimer_accepted"
    }
}
