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
        if (key in sentWithoutPersist) {
            return SeedboxResult.Failure(
                "Magnet may already be on seedbox (local save failed earlier). Check ruTorrent before resending.",
            )
        }
        if (isDuplicate(magnet, displayName, site)) {
            return SeedboxResult.Failure("Already uploaded — remove from Uploaded list to re-send")
        }
        val settings = settingsRepository.load()
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxResult.Failure("Configure seedbox URL and credentials in Settings")
        }
        val result = client().addMagnet(magnet, settings.downloadDirectory)
        if (result is SeedboxResult.Success) {
            val key = MagnetHashUtil.storageKey(magnet, displayName, site)
            try {
                database.uploadedMagnetDao().insert(
                    UploadedMagnet(
                        infoHash = key,
                        displayName = displayName,
                        site = site,
                        magnetUri = magnet,
                        sentAt = System.currentTimeMillis(),
                        downloadDirectory = settings.downloadDirectory,
                    ),
                )
                sentWithoutPersist.remove(key)
            } catch (_: Exception) {
                sentWithoutPersist.add(key)
                return SeedboxResult.Failure(
                    "Sent to seedbox but failed to save locally — check Uploaded list",
                )
            }
        }
        result
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
