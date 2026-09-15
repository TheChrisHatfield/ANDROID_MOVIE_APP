package com.torrentmovie.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "uploaded_magnets")
data class UploadedMagnet(
    @PrimaryKey val infoHash: String,
    val displayName: String,
    val site: String,
    val magnetUri: String,
    val sentAt: Long,
    val downloadDirectory: String,
)

@Dao
interface UploadedMagnetDao {
    @Query("SELECT * FROM uploaded_magnets ORDER BY sentAt DESC")
    suspend fun listAll(): List<UploadedMagnet>

    @Query("SELECT * FROM uploaded_magnets ORDER BY sentAt DESC")
    fun observeAll(): Flow<List<UploadedMagnet>>

    @Query("SELECT COUNT(*) FROM uploaded_magnets WHERE infoHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: UploadedMagnet)

    @Query("DELETE FROM uploaded_magnets WHERE infoHash = :hash")
    suspend fun delete(hash: String)
}
