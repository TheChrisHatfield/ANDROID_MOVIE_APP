package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.search.LanNetworkAddress
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

    val settingsRepository = SettingsRepository(context, bundledSearchApiUrl)
    @Volatile
    private var bootstrapThreadActive = false
    private val wifiBootstrap = SearchApiWifiBootstrap(
        context = context,
        onWifiReady = { thread(name = "search-api-wifi-retry") { resolveAndPersistSearchApi() } },
        shouldKeepListening = { settingsRepository.needsSearchApiAutoConfiguration() },
    )

    init {
        restartSearchApiBootstrapIfNeeded()
    }

    fun restartSearchApiBootstrapIfNeeded() {
        if (settingsRepository.needsSearchApiAutoConfiguration()) {
            wifiBootstrap.register()
            bootstrapSearchApiIfNeeded()
        } else {
            wifiBootstrap.unregister()
        }
    }

    private fun bootstrapSearchApiIfNeeded() {
        if (!settingsRepository.needsSearchApiAutoConfiguration()) return
        if (bootstrapThreadActive) return
        bootstrapThreadActive = true
        thread(name = "search-api-bootstrap") {
            try {
            repeat(BOOTSTRAP_ATTEMPTS) { attempt ->
                if (!settingsRepository.needsSearchApiAutoConfiguration()) {
                    wifiBootstrap.unregister()
                    return@thread
                }
                if (resolveAndPersistSearchApi()) {
                    wifiBootstrap.unregister()
                    return@thread
                }
                if (attempt < BOOTSTRAP_ATTEMPTS - 1) {
                    Thread.sleep(BOOTSTRAP_RETRY_MS)
                }
            }
            } finally {
                bootstrapThreadActive = false
            }
        }
    }

    private fun resolveAndPersistSearchApi(): Boolean {
        if (!settingsRepository.needsSearchApiAutoConfiguration()) return false
        val bundled = settingsRepository.bundledSearchApiUrlForBootstrap()
        val resolved = SearchApiBootstrap.resolveAutoSearchApiUrl(
            bundledSearchApiUrl = bundled,
            isEmulator = DeviceProfile.isEmulator(),
            wifiIpv4 = LanNetworkAddress.wifiIpv4(appContext),
            probeHealthy = SearchApiLanDiscovery::probeSearchApiBaseUrl,
        )
        return resolved != null && settingsRepository.applyAutoConfiguredSearchApi(resolved)
    }

    private companion object {
        const val BOOTSTRAP_ATTEMPTS = 5
        const val BOOTSTRAP_RETRY_MS = 2_000L
    }
    val searchResultStore = SearchResultStore()
    val movieMetadataStore = MovieMetadataStore()
    private val database = AppDatabase.get(context)
    val searchRepository = SearchRepository(settingsRepository)
    val seedboxRepository = SeedboxRepository(context, settingsRepository, database)
    val uploadedRepository = UploadedRepository(database)
}
