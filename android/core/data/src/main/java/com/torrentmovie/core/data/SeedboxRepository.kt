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

    private fun client(): RuTorrentClient {
        val s = settingsRepository.load()
        return RuTorrentClient(s.rutorrentBaseUrl, s.username, s.password, s.authScheme)
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
        if (isDuplicate(magnet, displayName, site)) {
            return SeedboxResult.Failure("Already uploaded — remove from Uploaded list to re-send")
        }
        val settings = settingsRepository.load()
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxResult.Failure("Configure seedbox URL and credentials in Settings")
        }
        val result = client().addMagnet(magnet, settings.downloadDirectory)
        if (result is SeedboxResult.Success) {
            database.uploadedMagnetDao().insert(
                UploadedMagnet(
                    infoHash = MagnetHashUtil.storageKey(magnet, displayName, site),
                    displayName = displayName,
                    site = site,
                    magnetUri = magnet,
                    sentAt = System.currentTimeMillis(),
                    downloadDirectory = settings.downloadDirectory,
                ),
            )
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
