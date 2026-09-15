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

    suspend fun isDuplicate(magnet: String): Boolean {
        val hash = MagnetHashUtil.extractInfoHash(magnet) ?: return false
        return database.uploadedMagnetDao().countByHash(hash) > 0
    }

    suspend fun addMagnet(
        magnet: String,
        displayName: String,
        site: String,
    ): SeedboxResult = addMutex.withLock {
        if (isDuplicate(magnet)) {
            return SeedboxResult.Failure("Already uploaded — remove from Uploaded list to re-send")
        }
        val settings = settingsRepository.load()
        if (!settingsRepository.isSeedboxConfigured()) {
            return SeedboxResult.Failure("Configure seedbox URL and credentials in Settings")
        }
        val result = client().addMagnet(magnet, settings.downloadDirectory)
        if (result is SeedboxResult.Success) {
            val hash = MagnetHashUtil.extractInfoHash(magnet)
            if (hash != null) {
                database.uploadedMagnetDao().insert(
                    UploadedMagnet(
                        infoHash = hash,
                        displayName = displayName,
                        site = site,
                        magnetUri = magnet,
                        sentAt = System.currentTimeMillis(),
                        downloadDirectory = settings.downloadDirectory,
                    ),
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
