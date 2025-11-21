package com.spotitfly.app.data.local.dao

import androidx.room.*
import com.spotitfly.app.data.local.entity.SpotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {

    @Query("SELECT * FROM spots WHERE visibility = 'public' ORDER BY updatedAtMs DESC")
    fun observeAllPublic(): Flow<List<SpotEntity>>

    @Query("SELECT * FROM spots WHERE (commentCount IS NULL OR commentCount < 0) LIMIT :limit")
    suspend fun getWithoutCommentCount(limit: Int): List<SpotEntity>

    @Query("UPDATE spots SET commentCount = :count, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateCommentCount(id: String, count: Int?, updatedAtMs: Long)

    @Query("SELECT * FROM spots WHERE (locality IS NULL OR locality = '') LIMIT :limit")
    suspend fun getWithoutLocality(limit: Int): List<SpotEntity>

    @Query("UPDATE spots SET locality = :locality, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateLocality(id: String, locality: String?, updatedAtMs: Long)

    @Query("SELECT * FROM spots WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SpotEntity?

    @Query(
        "SELECT * FROM spots " +
                "WHERE visibility = 'public' " +
                "AND latitude BETWEEN :minLat AND :maxLat " +
                "AND longitude BETWEEN :minLng AND :maxLng " +
                "ORDER BY updatedAtMs DESC " +
                "LIMIT :limit"
    )
    suspend fun getByBounds(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int
    ): List<SpotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SpotEntity>)

    @Query("UPDATE spots SET visibility = :visibility, deletedAtMs = :deletedAtMs, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun markVisibility(id: String, visibility: String, deletedAtMs: Long?, updatedAtMs: Long)

    @Query("SELECT MAX(updatedAtMs) FROM spots")
    suspend fun getMaxUpdatedAt(): Long?

    @Query("DELETE FROM spots WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
