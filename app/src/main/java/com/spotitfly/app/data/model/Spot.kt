package com.spotitfly.app.data.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

data class Spot(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    // Compat legado (iOS mantiene 'rating')
    val rating: Int = 0,

    // Campos iOS comunes
    val bestDate: String? = null,
    val category: SpotCategory = SpotCategory.OTROS,
    val imageUrl: String? = null,
    val createdBy: String? = null,
    val createdAt: Timestamp? = null,
    val locality: String? = null,
    val acceso: String? = null,
    val commentCount: Int? = null,
    val lastCommentAt: Timestamp? = null,

    // Ratings nuevos (opcionales)
    val ratings: Map<String, Int>? = null,
    val averageRating: Double? = null,
    // Solo local (no Firestore), igual que en iOS
    val isFavorite: Boolean = false,

    // Local-first (paridad iOS)
    val visibility: String = "public",
    val updatedAt: Long = 0L,       // epoch millis
    val deletedAt: Long? = null     // soft delete
) {
    val coord: LatLng get() = LatLng(latitude, longitude)
    val ratingMean: Double
        get() = averageRating ?: (ratings?.values?.average() ?: rating.toDouble())

    companion object {
        fun from(doc: DocumentSnapshot): Spot? = SpotMapper.from(doc)
    }
}

enum class SpotCategory {
    FREESTYLE_CAMPO_ABIERTO,
    FREESTYLE_BANDO,
    CINEMATICO,
    RACING,
    OTROS;

    companion object {
        // Map 1:1 desde los rawValue de iOS
        fun fromRaw(raw: String?): SpotCategory = when (raw?.trim()) {
            "Freestyle campo abierto" -> FREESTYLE_CAMPO_ABIERTO
            "Freestyle Bando" -> FREESTYLE_BANDO
            "Cinemático" -> CINEMATICO
            "Racing" -> RACING
            else -> OTROS
        }
    }
}

object SpotMapper {
    fun from(doc: DocumentSnapshot): Spot? {
        val id = doc.id
        val name = doc.getString("name") ?: doc.getString("title") ?: return null
        val description = doc.getString("description") ?: ""

        val lat = doc.getDouble("latitude") ?: return null
        val lng = doc.getDouble("longitude") ?: return null

        val rating = (doc.getLong("rating") ?: 0L).toInt()
        val bestDate = doc.getString("bestDate")
        val category = SpotCategory.fromRaw(doc.getString("category"))
        val imageUrl = doc.getString("imageUrl") ?: doc.getString("thumbnailUrl")
        val createdBy = doc.getString("createdBy") ?: doc.getString("authorId")
        val createdAt = doc.getTimestamp("createdAt")
        val locality = doc.getString("localidad") ?: doc.getString("locality")
        val acceso = doc.getString("acceso")
        val commentCount = (
                doc.getLong("commentCount")
                    ?: doc.getLong("commentsCount")
                    ?: doc.getLong("comentarios")
                )?.toInt()

        val lastCommentAt = doc.getTimestamp("lastCommentAt")

        val averageRating = doc.getDouble("averageRating")
        val ratingsMap = (doc.get("ratings") as? Map<*, *>)?.mapNotNull { (k, v) ->
            (k as? String)?.let { key ->
                val vi = when (v) {
                    is Long -> v.toInt()
                    is Int -> v
                    else -> null
                }
                vi?.let { key to it }
            }
        }?.toMap()

        val visibility = doc.getString("visibility") ?: "public"
        val updatedAtMs = (doc.getTimestamp("updatedAt") ?: createdAt ?: Timestamp.now()).toDate().time
        val deletedAtMs = doc.getTimestamp("deletedAt")?.toDate()?.time

        return Spot(
            id = id,
            name = name,
            description = description,
            latitude = lat,
            longitude = lng,
            rating = rating,
            bestDate = bestDate,
            category = category,
            imageUrl = imageUrl,
            createdBy = createdBy,
            createdAt = createdAt,
            locality = locality,
            acceso = acceso,
            commentCount = commentCount,
            lastCommentAt = lastCommentAt,
            ratings = ratingsMap,
            averageRating = averageRating,
            visibility = visibility,
            updatedAt = updatedAtMs,
            deletedAt = deletedAtMs
        )
    }
}
