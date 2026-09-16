package com.torrentmovie.app.ui.uploaded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.data.seedbox.SeedboxTorrentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class UploadedRowUi(
    val entry: UploadedMagnet,
    val remoteStatus: SeedboxTorrentStatus?,
    val statusLine: String,
    val showProgress: Boolean,
    val progressFraction: Float,
)

data class UploadedUiState(
    val rows: List<UploadedRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val statusError: String? = null,
    val seedboxConfigured: Boolean = false,
)

class UploadedViewModel(private val container: AppContainer) : ViewModel() {
    private val _refreshing = MutableStateFlow(false)
    private val _statusError = MutableStateFlow<String?>(null)
    private val _remoteByHash = MutableStateFlow<Map<String, SeedboxTorrentStatus>>(emptyMap())
    private val _pollSucceeded = MutableStateFlow(false)

    val uiState: StateFlow<UploadedUiState> = kotlinx.coroutines.flow.combine(
        container.uploadedRepository.observeAll(),
        _refreshing,
        _statusError,
        _remoteByHash,
        _pollSucceeded,
    ) { entries, refreshing, statusError, remoteByHash, pollSucceeded ->
        val configured = container.settingsRepository.isSeedboxConfigured()
        UploadedUiState(
            rows = entries.map { entry ->
                toRow(entry, remoteByHash, configured, pollSucceeded, statusError)
            },
            refreshing = refreshing,
            statusError = statusError,
            seedboxConfigured = configured,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UploadedUiState(),
    )

    init {
        refreshStatuses()
        viewModelScope.launch {
            container.uploadedRepository.observeAll()
                .map { entries -> entries.map { it.infoHash }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshStatuses() }
        }
    }

    fun refreshStatuses() {
        viewModelScope.launch {
            _refreshing.value = true
            _statusError.value = null
            try {
                val entries = withContext(Dispatchers.IO) {
                    container.uploadedRepository.list()
                }
                if (!container.settingsRepository.isSeedboxConfigured()) {
                    _remoteByHash.value = emptyMap()
                    _pollSucceeded.value = false
                    return@launch
                }
                val (statuses, error) = withContext(Dispatchers.IO) {
                    container.seedboxRepository.statusesForUploaded(entries)
                }
                if (error == null) {
                    _remoteByHash.value = statuses
                    _pollSucceeded.value = true
                } else {
                    _statusError.value = error
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun lookupHash(entry: UploadedMagnet): String? {
        return MagnetHashUtil.extractInfoHash(entry.magnetUri)?.uppercase(Locale.US)
    }

    private fun toRow(
        entry: UploadedMagnet,
        remoteByHash: Map<String, SeedboxTorrentStatus>,
        seedboxConfigured: Boolean,
        pollSucceeded: Boolean,
        statusError: String?,
    ): UploadedRowUi {
        val lookupHash = lookupHash(entry)
        val remote = lookupHash?.let { remoteByHash[it] }
        val statusLine = when {
            !seedboxConfigured -> "Sent locally · configure seedbox for live status"
            lookupHash == null -> "Sent · status unavailable (no info hash in magnet)"
            remote != null -> buildString {
                append(remote.statusLabel())
                remote.rateSummary()?.let { append(" · ").append(it) }
            }
            pollSucceeded -> "Not on seedbox"
            statusError != null -> "Status unavailable"
            else -> "Status unavailable"
        }
        val showProgress = remote != null && remote.sizeBytes > 0L &&
            remote.leftBytes > 0L && !remote.isHashChecking
        return UploadedRowUi(
            entry = entry,
            remoteStatus = remote,
            statusLine = statusLine,
            showProgress = showProgress,
            progressFraction = remote?.progressFraction() ?: 0f,
        )
    }
}
