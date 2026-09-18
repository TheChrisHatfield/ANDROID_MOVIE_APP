package com.torrentmovie.core.data

import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.data.seedbox.RuTorrentClient
import com.torrentmovie.core.data.seedbox.SeedboxListResult
import com.torrentmovie.core.data.seedbox.SeedboxProbeResult
import com.torrentmovie.core.data.seedbox.SeedboxResult
import com.torrentmovie.core.data.seedbox.SeedboxTorrentStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SeedboxRepository(
    private val settingsRepository: SettingsRepository,
    private val database: AppDatabase,
) {
    private val addMutex = Mutex()
    private val sentWithoutPersist = mutableSetOf<String>()
    private var cachedClient: RuTorrentClient? = null
    private var cachedClientRevision = -1
    private var lastSeedboxSettingsKey: String? = null

    private fun seedboxSettingsKey(settings: AppSettings): String = listOf(
        settings.rutorrentBaseUrl,
        settings.username,
        settings.password,
        settings.authScheme,
        settings.downloadDirectory,
    ).joinToString("|")

    @Synchronized
    private fun client(): RuTorrentClient {
        val revision = settingsRepository.revision.value
        if (cachedClient != null && cachedClientRevision == revision) {
            return cachedClient!!
        }
        val s = settingsRepository.load()
        cachedClient = RuTorrentClient(s.rutorrentBaseUrl, s.username, s.password, s.authScheme)
        cachedClientRevision = revision
        return cachedClient!!
    }

    private suspend fun isDuplicate(magnet: String, displayName: String, site: String): Boolean {
        val key = MagnetHashUtil.storageKey(magnet, displayName, site)
        return database.uploadedMagnetDao().countByHash(key) > 0
    }

    suspend fun addMagnet(
        magnet: String,
        displayName: String,
        site: String,
    ): SeedboxResult = addMutex.withLock {
        val settings = settingsRepository.load()
        val seedboxKey = seedboxSettingsKey(settings)
        if (seedboxKey != lastSeedboxSettingsKey) {
            sentWithoutPersist.clear()
            lastSeedboxSettingsKey = seedboxKey
        }
        val key = MagnetHashUtil.storageKey(magnet, displayName, site)
        if (MagnetHashUtil.extractInfoHash(magnet) == null) {
            return SeedboxResult.Failure("Magnet missing info hash — cannot send or track status")
        }
        if (isDuplicate(magnet, displayName, site)) {
            return SeedboxResult.Failure("Already uploaded — remove from Uploaded list to re-send")
        }
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxResult.Failure("Configure seedbox URL and credentials in Settings")
        }
        if (settings.downloadDirectory.trim().isBlank()) {
            return SeedboxResult.Failure("Set download folder in Settings before sending")
        }
        if (key in sentWithoutPersist) {
            return persistUploadedMagnet(
                key = key,
                magnet = magnet,
                displayName = displayName,
                site = site,
                downloadDirectory = settings.downloadDirectory,
            )
        }
        val result = client().addMagnet(magnet, settings.downloadDirectory)
        if (result is SeedboxResult.Success) {
            val persisted = persistUploadedMagnet(
                key = key,
                magnet = magnet,
                displayName = displayName,
                site = site,
                downloadDirectory = settings.downloadDirectory,
            )
            if (persisted is SeedboxResult.Failure) {
                sentWithoutPersist.add(key)
                return SeedboxResult.Success(
                    "Sent to seedbox (local history save failed — tap send again to retry)",
                )
            }
            return persisted
        }
        result
    }

    fun clearSentWithoutPersist(infoHash: String) {
        sentWithoutPersist.remove(infoHash)
    }

    private suspend fun persistUploadedMagnet(
        key: String,
        magnet: String,
        displayName: String,
        site: String,
        downloadDirectory: String,
    ): SeedboxResult {
        return try {
            database.uploadedMagnetDao().insert(
                UploadedMagnet(
                    infoHash = key,
                    displayName = displayName,
                    site = site,
                    magnetUri = magnet,
                    sentAt = System.currentTimeMillis(),
                    downloadDirectory = downloadDirectory,
                ),
            )
            sentWithoutPersist.remove(key)
            SeedboxResult.Success()
        } catch (_: Exception) {
            SeedboxResult.Failure(
                "Sent to seedbox but failed to save locally — tap send again",
            )
        }
    }

    suspend fun pingSeedbox(): Boolean {
        if (!settingsRepository.isSeedboxConfigured()) return false
        return try {
            client().ping()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun fetchTorrentStatuses(): SeedboxListResult {
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxListResult.Failure("Configure seedbox in Settings to see live status")
        }
        return try {
            client().listTorrentStatuses()
        } catch (e: Exception) {
            SeedboxListResult.Failure(e.message ?: "Could not load seedbox status")
        }
    }

    suspend fun probeSeedbox(): SeedboxProbeResult {
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxProbeResult(
                addReachable = false,
                httprpcAvailable = false,
                message = "Configure seedbox URL and credentials",
            )
        }
        val addOk = pingSeedbox()
        if (!addOk) {
            return SeedboxProbeResult(
                addReachable = false,
                httprpcAvailable = false,
                message = "Seedbox unreachable — check URL and credentials",
            )
        }
        return when (val status = fetchTorrentStatuses()) {
            is SeedboxListResult.Success -> SeedboxProbeResult(
                addReachable = true,
                httprpcAvailable = true,
                message = "Seedbox ready",
            )
            is SeedboxListResult.Failure -> SeedboxProbeResult(
                addReachable = true,
                httprpcAvailable = false,
                message = status.message,
            )
        }
    }

    suspend fun statusesForUploaded(
        entries: List<UploadedMagnet>,
    ): Pair<Map<String, SeedboxTorrentStatus>, String?> {
        if (entries.isEmpty()) return emptyMap<String, SeedboxTorrentStatus>() to null
        val lookupHashes = entries.mapNotNull { entry ->
            MagnetHashUtil.extractInfoHash(entry.magnetUri)?.uppercase(java.util.Locale.US)
        }
        if (lookupHashes.isEmpty()) {
            return emptyMap<String, SeedboxTorrentStatus>() to null
        }
        return when (val result = fetchTorrentStatuses()) {
            is SeedboxListResult.Success -> {
                val wanted = lookupHashes.toSet()
                val matched = result.statuses.filterKeys { it in wanted }
                matched to null
            }
            is SeedboxListResult.Failure -> emptyMap<String, SeedboxTorrentStatus>() to result.message
        }
    }
}