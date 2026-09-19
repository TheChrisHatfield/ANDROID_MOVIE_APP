package com.torrentmovie.app.ui.uploaded

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torrentmovie.core.data.AppContainer
import com.torrentmovie.core.data.MagnetHashUtil
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.data.seedbox.SeedboxTorrentStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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
    private companion object {
        const val ADDING_GRACE_MS = 120_000L
        const val ADDING_EXTENDED_MS = ADDING_GRACE_MS * 3
        const val POLL_IDLE_MS = 15_000L
        const val POLL_ACTIVE_MS = 5_000L
        const val ETA_TICK_MS = 1_000L
    }

    private val _refreshing = MutableStateFlow(false)
    private val _statusError = MutableStateFlow<String?>(null)
    private val _remoteByHash = MutableStateFlow<Map<String, SeedboxTorrentStatus>>(emptyMap())
    private val _pollSucceeded = MutableStateFlow(false)
    private val _syncClock = MutableStateFlow(SyncClock())
    private val _screenVisible = MutableStateFlow(false)
    private var refreshGeneration = 0
    private var pollLoopJob: Job? = null
    private var etaTickJob: Job? = null

    val uiState: StateFlow<UploadedUiState> = combine(
        combine(
            container.uploadedRepository.observeAll(),
            _refreshing,
            _statusError,
            _remoteByHash,
            _pollSucceeded,
        ) { entries, refreshing, statusError, remoteByHash, pollSucceeded ->
            PollUiInputs(entries, refreshing, statusError, remoteByHash, pollSucceeded)
        },
        _syncClock,
    ) { inputs, clock ->
        val configured = container.settingsRepository.isSeedboxConfigured()
        val elapsedSincePollSeconds = clock.elapsedSincePollSeconds()
        UploadedUiState(
            rows = inputs.entries.map { entry ->
                toRow(
                    entry,
                    inputs.remoteByHash,
                    configured,
                    inputs.pollSucceeded,
                    inputs.statusError,
                    elapsedSincePollSeconds,
                )
            },
            refreshing = inputs.refreshing,
            statusError = inputs.statusError,
            seedboxConfigured = configured,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UploadedUiState(),
    )

    init {
        viewModelScope.launch {
            container.uploadedRepository.observeAll()
                .map { entries -> entries.map { it.infoHash }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (_screenVisible.value) {
                        refreshStatuses()
                    }
                }
        }
        viewModelScope.launch {
            container.settingsRepository.revision.drop(1).collect {
                if (!container.settingsRepository.isSeedboxConfigured()) {
                    _remoteByHash.value = emptyMap()
                    _pollSucceeded.value = false
                    _syncClock.value = SyncClock()
                }
                if (_screenVisible.value) {
                    refreshStatuses()
                }
            }
        }
        startEtaTicker()
    }

    private data class PollUiInputs(
        val entries: List<UploadedMagnet>,
        val refreshing: Boolean,
        val statusError: String?,
        val remoteByHash: Map<String, SeedboxTorrentStatus>,
        val pollSucceeded: Boolean,
    )

    private data class SyncClock(
        val lastPollAtMs: Long = 0L,
        val displayTickMs: Long = 0L,
    ) {
        fun elapsedSincePollSeconds(): Long {
            if (lastPollAtMs <= 0L) return 0L
            return ((displayTickMs.coerceAtLeast(lastPollAtMs) - lastPollAtMs) / 1_000L)
        }
    }

    fun setScreenVisible(visible: Boolean) {
        if (_screenVisible.value == visible) return
        _screenVisible.value = visible
        if (visible) {
            refreshStatuses()
            startPollLoop()
        } else {
            pollLoopJob?.cancel()
            pollLoopJob = null
        }
    }

    fun refreshStatuses() {
        val generation = ++refreshGeneration
        viewModelScope.launch {
            _refreshing.value = true
            _statusError.value = null
            try {
                val entries = withContext(Dispatchers.IO) {
                    container.uploadedRepository.list()
                }
                if (generation != refreshGeneration) return@launch
                if (!container.settingsRepository.isSeedboxConfigured()) {
                    _remoteByHash.value = emptyMap()
                    _pollSucceeded.value = false
                    _syncClock.value = SyncClock()
                    return@launch
                }
                val (statuses, error) = withContext(Dispatchers.IO) {
                    container.seedboxRepository.statusesForUploaded(entries)
                }
                if (generation != refreshGeneration) return@launch
                if (error == null) {
                    val now = System.currentTimeMillis()
                    _remoteByHash.value = statuses
                    _pollSucceeded.value = true
                    _syncClock.value = SyncClock(lastPollAtMs = now, displayTickMs = now)
                } else {
                    _statusError.value = error
                    _pollSucceeded.value = false
                }
            } finally {
                if (generation == refreshGeneration) {
                    _refreshing.value = false
                }
            }
        }
    }

    private fun startPollLoop() {
        pollLoopJob?.cancel()
        pollLoopJob = viewModelScope.launch {
            while (isActive && _screenVisible.value) {
                val hasActiveDownloads = _remoteByHash.value.values.any { it.isActivelyDownloading() }
                delay(if (hasActiveDownloads) POLL_ACTIVE_MS else POLL_IDLE_MS)
                if (_screenVisible.value) {
                    refreshStatuses()
                }
            }
        }
    }

    private fun startEtaTicker() {
        if (etaTickJob?.isActive == true) return
        etaTickJob = viewModelScope.launch {
            while (isActive) {
                delay(ETA_TICK_MS)
                if (_remoteByHash.value.values.any { it.isActivelyDownloading() && it.etaSeconds() != null }) {
                    val clock = _syncClock.value
                    if (clock.lastPollAtMs > 0L) {
                        _syncClock.value = clock.copy(displayTickMs = System.currentTimeMillis())
                    }
                }
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
        elapsedSincePollSeconds: Long,
    ): UploadedRowUi {
        val lookupHash = lookupHash(entry)
        val remote = lookupHash?.let { remoteByHash[it] }
        val ageMs = System.currentTimeMillis() - entry.sentAt
        val recentlySent = ageMs < ADDING_GRACE_MS
        val extendedPending = ageMs < ADDING_EXTENDED_MS
        val statusLine = when {
            !seedboxConfigured -> "Sent locally · configure seedbox for live status"
            lookupHash == null -> "Sent · status unavailable (no info hash in magnet)"
            remote != null -> buildString {
                append(remote.statusLabel())
                remote.rateSummary()?.let { append(" · ").append(it) }
                remote.etaSummary(elapsedSincePollSeconds)?.let { append(" · ").append(it) }
                if (statusError != null) append(" · last known")
            }
            lookupHash != null && (recentlySent || (extendedPending && pollSucceeded)) ->
                "Adding to seedbox…"
            statusError != null -> "Status unavailable"
            pollSucceeded -> "Not on seedbox"
            else -> "Status unavailable"
        }
        val showProgress = remote?.showsDownloadProgress() == true
        return UploadedRowUi(
            entry = entry,
            remoteStatus = remote,
            statusLine = statusLine,
            showProgress = showProgress,
            progressFraction = remote?.progressFraction() ?: 0f,
        )
    }
}
