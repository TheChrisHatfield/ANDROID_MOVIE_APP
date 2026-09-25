package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.search.LanNetworkAddress
import com.torrentmovie.core.data.search.SearchApiAdaptQueue
import com.torrentmovie.core.data.search.SearchApiAutoConfig
import com.torrentmovie.core.data.search.SearchApiBootstrap
import com.torrentmovie.core.data.search.SearchApiLanDiscovery
import com.torrentmovie.core.data.search.SearchApiWifiBootstrap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

class AppContainer(
    context: Context,
    bundledSearchApiUrl: String = "",
    bundledTmdbApiKey: String = "",
) {
    private val appContext = context.applicationContext
    /** Cover/single-pane → unfolded two-pane restore payload. */
    var pendingFoldDetail: PendingFoldDetail? = null

    /** Latest fold two-pane selection (survives layout disposal on width change). */
    private val _foldActiveSelection = MutableStateFlow<PendingFoldDetail?>(null)
    val foldActiveSelectionFlow: StateFlow<PendingFoldDetail?> = _foldActiveSelection.asStateFlow()
    var foldActiveSelection: PendingFoldDetail?
        get() = _foldActiveSelection.value
        set(value) {
            _foldActiveSelection.value = value
        }

    /** Scroll/highlight target when opening Uploaded from detail after send. */
    var pendingUploadedHighlight: String? = null
    private val _uploadedHighlightSeq = MutableStateFlow(0L)
    val uploadedHighlightSeq: StateFlow<Long> = _uploadedHighlightSeq.asStateFlow()

    fun requestUploadedHighlight(storageKey: String) {
        pendingUploadedHighlight = storageKey
        _uploadedHighlightSeq.value += 1
    }

    val settingsRepository = SettingsRepository(
        context,
        bundledSearchApiUrl,
        bundledTmdbApiKey,
    )

    @Volatile
    private var resolveInFlight = false
    @Volatile
    private var lastAdaptUnreachable = false
    private val adaptQueue = SearchApiAdaptQueue()
    private val wifiBootstrap = SearchApiWifiBootstrap(
        context = context,
        onNetworkChanged = { adaptSearchApiToNetwork() },
        shouldKeepListening = { settingsRepository.shouldAdaptSearchApiToNetwork() },
    )

    init {
        try {
            settingsRepository.applyBundledTmdbIfNeeded()
            settingsRepository.applyBundledSearchApiIfNeeded(
                wifiIpv4 = LanNetworkAddress.wifiIpv4(appContext),
            )
        } catch (_: Throwable) {
            // Keystore / prefs OEM failures must not kill process create.
        }
        // LAN/Wi-Fi bootstrap waits for Activity ON_RESUME so Application.onCreate
        // stays cheap on low-RAM phones and OEM process-start scanners.
    }

    fun restartSearchApiBootstrapIfNeeded() {
        try {
            if (settingsRepository.shouldAdaptSearchApiToNetwork()) {
                wifiBootstrap.register()
                adaptSearchApiToNetwork()
            } else {
                wifiBootstrap.unregister()
            }
        } catch (_: Throwable) {
            // Search still loads; user can set the API URL in Settings.
        }
    }

    private fun adaptSearchApiToNetwork() {
        if (!settingsRepository.shouldAdaptSearchApiToNetwork()) return
        if (!adaptQueue.tryStart()) return
        thread(name = "search-api-bootstrap") {
            try {
                do {
                    if (!settingsRepository.shouldAdaptSearchApiToNetwork()) break
                    val current = settingsRepository.load().searchApiBaseUrl
                    val wifiIpv4 = LanNetworkAddress.wifiIpv4(appContext)
                    val canKeepIfHealthy = SearchApiAutoConfig.shouldKeepCurrentUrl(
                        current,
                        wifiIpv4,
                        currentHealthy = true,
                    )
                    val currentHealthy = canKeepIfHealthy &&
                        current.isNotBlank() &&
                        SearchApiLanDiscovery.probeSearchApiBaseUrl(current)
                    if (SearchApiAutoConfig.shouldKeepCurrentUrl(current, wifiIpv4, currentHealthy)) {
                        if (SearchApiAutoConfig.shouldNotifyRebound(lastAdaptUnreachable)) {
                            settingsRepository.markSearchApiRebound()
                        }
                        lastAdaptUnreachable = false
                    } else {
                        lastAdaptUnreachable = true
                        var rebound = false
                        repeat(BOOTSTRAP_ATTEMPTS) { attempt ->
                            if (!settingsRepository.shouldAdaptSearchApiToNetwork()) return@repeat
                            if (resolveAndPersistSearchApi()) {
                                lastAdaptUnreachable = false
                                rebound = true
                                return@repeat
                            }
                            if (attempt < BOOTSTRAP_ATTEMPTS - 1) {
                                Thread.sleep(BOOTSTRAP_RETRY_MS)
                            }
                        }
                        if (!rebound) {
                            clearStaleAutoLanUrl(current, wifiIpv4)
                        }
                    }
                } while (adaptQueue.consumeQueued())
            } catch (_: Throwable) {
                // Never take down the process from bootstrap; search UI still loads.
            } finally {
                if (adaptQueue.finish()) {
                    adaptSearchApiToNetwork()
                }
            }
        }
    }

    private fun resolveAndPersistSearchApi(): Boolean {
        if (!settingsRepository.shouldAdaptSearchApiToNetwork()) return false
        synchronized(this) {
            if (resolveInFlight) return false
            resolveInFlight = true
        }
        return try {
            val bundled = settingsRepository.bundledSearchApiUrlForBootstrap()
            val current = settingsRepository.load().searchApiBaseUrl
            val resolved = SearchApiBootstrap.resolveAutoSearchApiUrl(
                bundledSearchApiUrl = bundled,
                isEmulator = DeviceProfile.isEmulator(),
                wifiIpv4 = LanNetworkAddress.wifiIpv4(appContext),
                probeHealthy = SearchApiLanDiscovery::probeSearchApiBaseUrl,
                currentUrl = current,
            )
            resolved != null && settingsRepository.applyAutoConfiguredSearchApi(resolved)
        } catch (_: Throwable) {
            false
        } finally {
            resolveInFlight = false
        }
    }

    private fun clearStaleAutoLanUrl(current: String, wifiIpv4: String?) {
        val host = SearchApiAutoConfig.ipv4Host(current) ?: return
        if (SearchApiAutoConfig.slash24(host) == null) return
        if (!wifiIpv4.isNullOrBlank() && SearchApiAutoConfig.isOnWifiSubnet(current, wifiIpv4)) {
            return
        }
        settingsRepository.clearAutoConfiguredSearchApi()
    }

    private companion object {
        const val BOOTSTRAP_ATTEMPTS = 2
        const val BOOTSTRAP_RETRY_MS = 2_000L
    }
    val searchResultStore = SearchResultStore()
    val movieMetadataStore = MovieMetadataStore()
    private val database = AppDatabase.get(context)
    val searchRepository = SearchRepository(settingsRepository)
    val seedboxRepository = SeedboxRepository(context, settingsRepository, database)
    val uploadedRepository = UploadedRepository(database)
}
