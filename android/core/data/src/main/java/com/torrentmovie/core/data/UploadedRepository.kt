package com.torrentmovie.core.data

import com.torrentmovie.core.data.db.AppDatabase
import com.torrentmovie.core.data.db.UploadedMagnet
import kotlinx.coroutines.flow.Flow

class UploadedRepository(private val database: AppDatabase) {
    fun observeAll(): Flow<List<UploadedMagnet>> = database.uploadedMagnetDao().observeAll()
    suspend fun list(): List<UploadedMagnet> = database.uploadedMagnetDao().listAll()

    suspend fun delete(infoHash: String) = database.uploadedMagnetDao().delete(infoHash)

    suspend fun contains(infoHash: String): Boolean =
        database.uploadedMagnetDao().countByHash(infoHash) > 0

    suspend fun containsMagnet(magnet: String?, displayName: String, site: String): Boolean {
        if (magnet.isNullOrBlank()) return false
        return contains(MagnetHashUtil.storageKey(magnet, displayName, site))
    }

    suspend fun isUploaded(magnet: String?, displayName: String, site: String): Boolean {
        if (!magnet.isNullOrBlank()) {
            return contains(MagnetHashUtil.storageKey(magnet, displayName, site))
        }
        return list().any { it.displayName == displayName && it.site == site }
    }
}
