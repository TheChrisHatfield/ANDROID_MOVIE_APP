package com.torrentmovie.core.data

import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.db.UploadedMagnet
import com.torrentmovie.core.data.seedbox.RuTorrentClient
import com.torrentmovie.core.data.seedbox.SeedboxResult
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
        if (database.uploadedMagnetDao().countByHash(key) > 0) return true
        return database.uploadedMagnetDao().listAll().any {
            it.displayName == displayName && it.site == site
        }
    }

    suspend fun addMagnet(
        magnet: String,
        displayName: String,
        site: String,
    ): SeedboxResult = addMutex.withLock {
        val key = MagnetHashUtil.storageKey(magnet, displayName, site)
        if (isDuplicate(magnet, displayName, site)) {
            return SeedboxResult.Failure("Already uploaded — remove from Uploaded list to re-send")
        }
        val settings = settingsRepository.load()
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxResult.Failure("Configure seedbox URL and credentials in Settings")
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
}
