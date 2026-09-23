package com.torrentmovie.core.data

import android.content.Context
import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.search.LanNetworkAddress
import com.torrentmovie.core.data.search.SearchApiBootstrap
import com.torrentmovie.core.data.search.SearchApiLanDiscovery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

class AppContainer(
    context: Context,
    bundledSearchApiUrl: String = "",
) {
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

    init {
        bootstrapSearchApiIfNeeded(context)
    }

    private fun bootstrapSearchApiIfNeeded(context: Context) {
        if (!settingsRepository.needsSearchApiAutoConfiguration()) return
        thread(name = "search-api-bootstrap") {
            val bundled = settingsRepository.bundledSearchApiUrlForBootstrap()
            val isEmulator = DeviceProfile.isEmulator()
            repeat(BOOTSTRAP_ATTEMPTS) { attempt ->
                if (!settingsRepository.needsSearchApiAutoConfiguration()) return@thread
                val wifiIp = LanNetworkAddress.wifiIpv4(context)
                val resolved = SearchApiBootstrap.resolveAutoSearchApiUrl(
                    bundledSearchApiUrl = bundled,
                    isEmulator = isEmulator,
                    wifiIpv4 = wifiIp,
                    probeHealthy = SearchApiLanDiscovery::probeSearchApiBaseUrl,
                )
                if (resolved != null) {
                    settingsRepository.applyAutoConfiguredSearchApi(resolved)
                    return@thread
                }
                if (attempt < BOOTSTRAP_ATTEMPTS - 1) {
                    Thread.sleep(BOOTSTRAP_RETRY_MS)
                }
            }
        }
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
