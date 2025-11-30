package com.spotitfly.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.data.model.SpotCategory

@Entity(tableName = "spots")
data class SpotEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String?,      // enum name (SpotCategory)
    val createdBy: String?,
    val ratingLegacy: Int?,     // legacy 'rating'
    val averageRating: Double?, // media nueva (si existe)
    val ratingMean: Double?,    // espejo para paridad con iOS
    val commentCount: Int?,
    val bestDate: String?,
    val acceso: String?,
    val locality: String?,
    val visibility: String,     // 'public' | 'hidden'...
    val updatedAtMs: Long,      // epoch millis
    val deletedAtMs: Long?      // soft delete
) {
    fun toDomain(): Spot {
        val cat = category
            ?.let { runCatching { SpotCategory.valueOf(it) }.getOrNull() }
            ?: SpotCategory.OTROS

        return Spot(
            id = id,
            name = name,
            description = description ?: "",
            latitude = latitude,
            longitude = longitude,
            category = cat,
            imageUrl = imageUrl,
            createdBy = createdBy,
            rating = ratingLegacy ?: 0,
            averageRating = averageRating,
            // ratings (map) no lo persistimos en local (mantener null)
            commentCount = commentCount,
            bestDate = bestDate,
            acceso = acceso,
            locality = locality,
            visibility = visibility,
            updatedAt = updatedAtMs,
            deletedAt = deletedAtMs
        )
    }

    companion object {
        fun fromDomain(
            s: Spot,
            visibility: String,
            updatedAtMs: Long,
            deletedAtMs: Long?
        ): SpotEntity =
            SpotEntity(
                id = s.id,
                name = s.name,
                description = s.description,
                imageUrl = s.imageUrl,
                latitude = s.latitude,
                longitude = s.longitude,
                category = s.category.name,
                createdBy = s.createdBy,
                ratingLegacy = s.rating,
                averageRating = s.averageRating,
                ratingMean = s.ratingMean, // disponible como propiedad calculada en Spot
                commentCount = s.commentCount,
                bestDate = s.bestDate,
                acceso = s.acceso,
                locality = s.locality,
                visibility = visibility,
                updatedAtMs = updatedAtMs,
                deletedAtMs = deletedAtMs
            )
    }
}
