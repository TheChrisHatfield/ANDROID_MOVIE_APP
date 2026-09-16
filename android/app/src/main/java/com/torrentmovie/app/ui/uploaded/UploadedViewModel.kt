package com.torrentmovie.app.ui.uploaded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.data.seedbox.SeedboxTorrentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val uiState: StateFlow<UploadedUiState> = combine(
        container.uploadedRepository.observeAll(),
        _refreshing,
        _statusError,
        _remoteByHash,
    ) { entries, refreshing, statusError, remoteByHash ->
        val configured = container.settingsRepository.isSeedboxConfigured()
        UploadedUiState(
            rows = entries.map { entry -> toRow(entry, remoteByHash, configured) },
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
    }

    fun refreshStatuses() {
        viewModelScope.launch {
            _refreshing.value = true
            _statusError.value = null
            try {
                val entries = container.uploadedRepository.list()
                val (statuses, error) = container.seedboxRepository.statusesForUploaded(entries)
                _remoteByHash.value = statuses
                _statusError.value = error
            } finally {
                _refreshing.value = false
            }
        }
    }

    private fun toRow(
        entry: UploadedMagnet,
        remoteByHash: Map<String, SeedboxTorrentStatus>,
        seedboxConfigured: Boolean,
    ): UploadedRowUi {
        val hash = entry.infoHash.uppercase()
        val remote = remoteByHash[hash]
        val trackable = SeedboxTorrentStatus.isTrackableInfoHash(entry.infoHash)
        val statusLine = when {
            !seedboxConfigured -> "Sent locally · configure seedbox for live status"
            !trackable -> "Sent · status unavailable for this entry"
            remote == null -> "Not on seedbox"
            else -> buildString {
                append(remote.statusLabel())
                remote.rateSummary()?.let { append(" · ").append(it) }
            }
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
