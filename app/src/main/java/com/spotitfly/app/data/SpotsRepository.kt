package com.spotitfly.app.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.spotitfly.app.data.local.AppDatabase
import com.spotitfly.app.data.local.dao.SpotDao
import com.spotitfly.app.data.local.entity.SpotEntity
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.data.model.SpotCategory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

class SpotsRepository {

    // --- Contexto y dependencias principales ---
    private val appContext: Context = FirebaseApp.getInstance().applicationContext
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val spotDao: SpotDao by lazy { AppDatabase.get(appContext).spotsDao() }

    private val storage by lazy { FirebaseStorage.getInstance() }


    // --- Corrutinas / scope de trabajo en IO, con handler para logs ---
    private val handler = CoroutineExceptionHandler { _, e ->
        Log.e("SpotsRepository", "Coroutine crash", e)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    // --- Preferencias para marcas de sync ---
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("spots_sync", Context.MODE_PRIVATE)

    // --- Caché en memoria de todos los spots (mapeados a dominio) ---
    private val cachedAllRef = AtomicReference<List<Spot>>(emptyList())

    // --- Flags para tareas de backfill (evitar arrancarlas más de una vez) ---
    private val didStartCommentsBackfill = AtomicBoolean(false)
    private val didStartLocalityBackfill = AtomicBoolean(false)

    // ===== API de caché =====
    fun hasAllCache(): Boolean = cachedAllRef.get().isNotEmpty()
    fun getAllCache(): List<Spot> = cachedAllRef.get()

    // ===== Suscripción principal a Room + arranque de sincronizaciones =====
    fun listenAll(onUpdate: (List<Spot>) -> Unit) {
        scope.launch {
            spotDao.observeAllPublic().collectLatest { list ->
                val mapped = list.map { it.toDomain() }
                cachedAllRef.set(mapped)
                withContext(Dispatchers.Main) { onUpdate(mapped) }
            }
        }
        scope.launch { incrementalSync() }

        if (didStartLocalityBackfill.compareAndSet(false, true)) {
            scope.launch { backfillLocalities(appContext) }
        }
        if (didStartCommentsBackfill.compareAndSet(false, true)) {
            scope.launch { backfillCommentCounts() }
        }
    }

    /** Carga por viewport: primero local, luego remoto (lat en server, lon en cliente). */
    fun fetchInViewport(
        bounds: LatLngBounds,
        limit: Int? = 200,
        onResult: (List<Spot>) -> Unit,
        onError: (Exception) -> Unit = { }
    ) {
        scope.launch {
            try {
                val minLat = max(-90.0, min(bounds.southwest.latitude, bounds.northeast.latitude))
                val maxLat = min(90.0, max(bounds.southwest.latitude, bounds.northeast.latitude))
                val minLng = max(-180.0, min(bounds.southwest.longitude, bounds.northeast.longitude))
                val maxLng = min(180.0, max(bounds.southwest.longitude, bounds.northeast.longitude))

                // 1) Local inmediato
                val local = spotDao.getByBounds(minLat, maxLat, minLng, maxLng, (limit ?: 200))
                val mapped = local.map { it.toDomain() }
                withContext(Dispatchers.Main) { onResult(mapped) }

                // 2) Remoto (latitud en server, longitud filtrada en cliente)
                var q: Query = firestore.collection("spots")
                    .whereGreaterThanOrEqualTo("latitude", minLat)
                    .whereLessThanOrEqualTo("latitude", maxLat)
                    .orderBy("latitude", Query.Direction.ASCENDING)
                if (limit != null) q = q.limit(limit.toLong())

                val snap = q.get().await()
                val remote = snap.documents.mapNotNull { Spot.from(it) }
                    .filter { it.longitude in minLng..maxLng }
                    .filter { it.visibility == "public" }

                // Corrección inmediata de commentCount negativo ANTES de persistir
                val fixedCounts = mutableMapOf<String, Int>()
                for (s in remote) {
                    val cc = s.commentCount
                    if (cc == null || cc < 0) {
                        val id = s.id ?: continue
                        val visible = computeVisibleCommentsCount(id)
                        fixedCounts[id] = visible
                    }
                }

                val entities = remote.map { s ->
                    val e = SpotEntity.fromDomain(
                        s,
                        visibility = s.visibility,
                        updatedAtMs = s.updatedAt,
                        deletedAtMs = s.deletedAt
                    )
                    val id = s.id
                    if (id != null && fixedCounts.containsKey(id)) {
                        e.copy(commentCount = fixedCounts[id])
                    } else e
                }

                spotDao.upsertAll(entities)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e) }
            }
        }
    }

    // ====== Localidad (reverse geocoding) ======
    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeLocality(
        context: Context,
        lat: Double,
        lon: Double
    ): String? {
        return try {
            val geocoder = Geocoder(context, Locale("es", "ES"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val a = addresses?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchDocLocalityOrNull(id: String): String? {
        return try {
            val doc = firestore.collection("spots").document(id).get().await()
            doc.getString("localidad") ?: doc.getString("locality")
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun backfillLocalities(context: Context, batch: Int = 50) {
        while (true) {
            val pending = try { spotDao.getWithoutLocality(batch) } catch (_: Exception) { emptyList() }
            if (pending.isEmpty()) break

            for (e in pending) {
                try {
                    val remote = fetchDocLocalityOrNull(e.id)
                    val loc = if (!remote.isNullOrBlank()) remote
                    else reverseGeocodeLocality(context, e.latitude, e.longitude)

                    if (!loc.isNullOrBlank()) {
                        spotDao.updateLocality(e.id, loc.trim(), System.currentTimeMillis())
                    }
                } catch (_: Exception) { }
                delay(250)
            }
            delay(500)
        }
    }

    // ====== Incremental sync ======
    /**
     * Incremental:
     *  - Sin marca → seed por createdAt ASC.
     *  - Con marca → updatedAt > last ASC.
     *  - Corrige commentCount<0 ANTES de guardar en Room.
     */
    private suspend fun incrementalSync() {
        val last = prefs.getLong("lastUpdatedAtMs", 0L)

        val base = firestore.collection("spots")
        val q: Query = if (last > 0L) {
            Log.d("SpotsRepository", "Incremental by updatedAt > $last")
            base.whereGreaterThan("updatedAt", Timestamp(Date(last)))
                .orderBy("updatedAt", Query.Direction.ASCENDING)
        } else {
            Log.d("SpotsRepository", "Cold start seed by createdAt")
            base.orderBy("createdAt", Query.Direction.ASCENDING)
        }

        val snap = q.limit(500).get().await()
        var items = snap.documents.mapNotNull { Spot.from(it) }
            .filter { it.visibility == "public" }

        if (last > 0L && items.isEmpty()) {
            prefs.edit().putLong("lastUpdatedAtMs", 0L).apply()
            val snap2 = firestore.collection("spots")
                .orderBy("updatedAt", Query.Direction.ASCENDING)
                .limit(500)
                .get()
                .await()
            items = snap2.documents.mapNotNull { Spot.from(it) }
                .filter { it.visibility == "public" }
        }

        if (items.isEmpty()) return

        // Corrección inmediata para negativos
        val fixedCounts = mutableMapOf<String, Int>()
        for (s in items) {
            val cc = s.commentCount
            if (cc == null || cc < 0) {
                val id = s.id ?: continue
                val visible = computeVisibleCommentsCount(id)
                fixedCounts[id] = visible
            }
        }

        val entities = items.map { s ->
            val e = SpotEntity.fromDomain(
                s,
                visibility = s.visibility,
                updatedAtMs = s.updatedAt,
                deletedAtMs = s.deletedAt
            )
            val id = s.id
            if (id != null && fixedCounts.containsKey(id)) {
                e.copy(commentCount = fixedCounts[id])
            } else e
        }

        spotDao.upsertAll(entities)

        val maxUpdated = items
            .map { it.updatedAt }
            .filterIsInstance<Long>()
            .maxOrNull() ?: 0L

        prefs.edit().putLong("lastUpdatedAtMs", maxUpdated).apply()

        if (items.size >= 500) {
            delay(100)
            incrementalSync()
        }
    }

    // ====== Comentarios: helpers ======
    private suspend fun fetchDocCommentCountOrNull(id: String): Int? {
        return try {
            val doc = firestore.collection("spots").document(id).get().await()
            (
                    doc.getLong("commentCount")
                        ?: doc.getLong("commentsCount")
                        ?: doc.getLong("comentarios")
                    )?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun computeVisibleCommentsCount(id: String): Int {
        return try {
            val base = firestore.collection("spots").document(id).collection("comments")
            val total = base.count().get(AggregateSource.SERVER).await().count
            val hidden = base.whereEqualTo("visibility", "hidden").count().get(AggregateSource.SERVER).await().count
            val deleted = base.whereEqualTo("deleted", true).count().get(AggregateSource.SERVER).await().count
            val visible = (total - hidden - deleted).toInt()
            if (visible < 0) 0 else visible
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun backfillCommentCounts(batch: Int = 50) {
        while (true) {
            val pending = try { spotDao.getWithoutCommentCount(batch) } catch (_: Exception) { emptyList() }
            if (pending.isEmpty()) break

            for (e in pending) {
                try {
                    val remote = fetchDocCommentCountOrNull(e.id)
                    val count = if (remote != null && remote >= 0) remote
                    else computeVisibleCommentsCount(e.id)
                    spotDao.updateCommentCount(e.id, count, System.currentTimeMillis())
                } catch (_: Exception) { }
                delay(200)
            }
            delay(400)
        }
    }
    private suspend fun compressImageFromUri(uri: Uri): ByteArray =
        withContext(Dispatchers.IO) {
            val cr = appContext.contentResolver
            val input = cr.openInputStream(uri) ?: error("No se pudo abrir la imagen")
            val bmp = input.use { BitmapFactory.decodeStream(it) } ?: error("Bitmap inválido")
            val out = ByteArrayOutputStream()
            // Paridad iOS: jpegData(compressionQuality: 0.75)
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.toByteArray()
        }


    private suspend fun uploadSpotImage(spotId: String, bytes: ByteArray): String {
        val ref = storage.reference.child("spots/$spotId/main.jpg")
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    // ====== CRUD Spots (métodos miembro) ======
    suspend fun createSpot(
        name: String,
        description: String,
        latitude: Double,
        longitude: Double,
        category: SpotCategory,
        createdBy: String,
        localidad: String? = null,
        myRating: Int? = null,
        imageUri: Uri? = null
    ): Spot {
        val id = firestore.collection("spots").document().id
        val now = com.google.firebase.Timestamp.now()

        val data: MutableMap<String, Any> = mutableMapOf(
            "name" to name,
            "description" to description,
            "latitude" to latitude,
            "longitude" to longitude,
            "category" to categoryRaw(category),
            "createdBy" to createdBy,
            "createdAt" to now,
            "updatedAt" to now,
            "visibility" to "public",
            "rating" to 0,
            "commentCount" to 0
        )
        if (!localidad.isNullOrBlank()) data["localidad"] = localidad
        if (myRating != null && myRating in 1..5) {
            data["ratings"] = mapOf(createdBy to myRating)
            data["averageRating"] = myRating.toDouble()
            data["ratingsCount"] = 1
        }

        // Subir imagen (si hay) y guardar imageUrl
        val imageUrl = if (imageUri != null) {
            runCatching {
                val bytes = compressImageFromUri(imageUri)
                uploadSpotImage(id, bytes)
            }.getOrNull()
        } else null
        if (imageUrl != null) data["imageUrl"] = imageUrl

        firestore.collection("spots").document(id).set(data).await()

        val created = Spot(
            id = id,
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            category = category,
            createdBy = createdBy,
            locality = localidad
        )

        spotDao.upsertAll(
            listOf(
                SpotEntity.fromDomain(
                    created,
                    visibility = "public",
                    updatedAtMs = now.toDate().time,
                    deletedAtMs = null
                )
            )
        )
        cachedAllRef.set((cachedAllRef.get() + created).distinctBy { it.id })
        return created
    }

    suspend fun updateSpot(
        id: String,
        name: String,
        description: String,
        category: SpotCategory,
        latitude: Double? = null,
        longitude: Double? = null,
        localidad: String? = null,
        myRating: Int? = null,
        imageUri: Uri? = null
    ) {
        val snap = firestore.collection("spots").document(id).get().await()
        val owner = snap.getString("createdBy")
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (owner != null && me != null && owner != me) {
            throw IllegalStateException("No eres el autor del spot")
        }

        val now = com.google.firebase.Timestamp.now()

        // Subir nueva imagen si llega
        val newImageUrl = if (imageUri != null) {
            runCatching {
                val bytes = compressImageFromUri(imageUri)
                uploadSpotImage(id, bytes)
            }.getOrNull()
        } else null

        // Construir update parcial
        val update = mutableMapOf<String, Any>(
            "name" to name,
            "description" to description,
            "category" to categoryRaw(category),
            "updatedAt" to now
        )
        if (latitude != null) update["latitude"] = latitude
        if (longitude != null) update["longitude"] = longitude
        if (!localidad.isNullOrBlank()) update["localidad"] = localidad
        if (newImageUrl != null) update["imageUrl"] = newImageUrl
        if (myRating != null && myRating in 1..5 && me != null) {
            // Guardamos el voto del editor (propio) en el mapa ratings.{uid}
            update["ratings.$me"] = myRating
        }

        firestore.collection("spots").document(id).update(update).await()

        // Refrescar cache/local
        val existing = cachedAllRef.get().find { it.id == id }
        val updated = existing?.copy(
            name = name,
            description = description,
            category = category,
            latitude = latitude ?: existing.latitude,
            longitude = longitude ?: existing.longitude,
            locality = localidad ?: existing.locality,
            imageUrl = newImageUrl ?: existing.imageUrl
        )
        if (updated != null) {
            spotDao.upsertAll(
                listOf(
                    com.spotitfly.app.data.local.entity.SpotEntity.fromDomain(
                        updated,
                        visibility = "public",
                        updatedAtMs = now.toDate().time,
                        deletedAtMs = null
                    )
                )
            )
            cachedAllRef.set(cachedAllRef.get().map { if (it.id == id) updated else it })
        }
    }


    suspend fun deleteSpot(id: String) {
        // Borra en Firestore si existe
        try {
            firestore.collection("spots").document(id).delete().await()
        } catch (_: Exception) {
            // si no existe remoto, seguimos con local
        }
        // Borra local (fallback: marcar hidden si no hay hard-delete en el DAO)
        try {
            val nowMs = System.currentTimeMillis()
            spotDao.markVisibility(id, "hidden", nowMs, nowMs)
        } catch (_: Exception) { }

        // Limpia caché en memoria
        cachedAllRef.set(cachedAllRef.get().filter { it.id != id })
    }

    // ====== await() interno para Tasks (sin dependencias extra) ======
    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { res -> cont.resume(res) }
            addOnFailureListener { e -> cont.resumeWithException(e) }
        }
}

// === Helpers top-level ===
private fun categoryLabel(cat: SpotCategory): String = when (cat) {
    SpotCategory.FREESTYLE_CAMPO_ABIERTO -> "Freestyle campo abierto"
    SpotCategory.FREESTYLE_BANDO -> "Freestyle Bando"
    SpotCategory.CINEMATICO -> "Cinemático"
    SpotCategory.RACING -> "Racing"
    SpotCategory.OTROS -> "Otros"
}

// Para serializar categoría como string (paridad con Firestore actual)
private fun categoryRaw(cat: SpotCategory): String = categoryLabel(cat)
