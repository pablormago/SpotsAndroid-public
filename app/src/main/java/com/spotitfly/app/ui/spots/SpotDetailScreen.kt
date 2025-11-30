package com.spotitfly.app.ui.spots

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.data.model.SpotCategory
import com.spotitfly.app.ui.design.SpotDetailTokens as T
import com.spotitfly.app.data.weather.WeatherDaily
import com.spotitfly.app.data.weather.WeatherHourly
import com.spotitfly.app.data.weather.WeatherRepository
import com.spotitfly.app.ui.weather.Weather15DayView
import com.spotitfly.app.ui.weather.WeatherCompactPill
import com.spotitfly.app.ui.weather.WeatherHourlySheet
import com.google.maps.android.compose.*
import java.io.File
import java.util.Locale
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.context.ContextRepository
import com.spotitfly.app.data.context.PointContextResult
import com.spotitfly.app.ui.context.ContextSectionsScaffold
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.ui.favorites.FavoritesViewModel
import androidx.compose.material.icons.filled.Favorite
import com.spotitfly.app.ui.comments.CommentsSheet
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.spotitfly.app.PreferredNavApp
import com.spotitfly.app.getPreferredNavApp
import android.content.ActivityNotFoundException
import com.spotitfly.app.SpotsDeepLink
import com.spotitfly.app.SpotsDeepLinkBus
import com.spotitfly.app.ui.chats.NewChatUser
import com.spotitfly.app.ui.chats.createOrOpenChatWithUser
import kotlinx.coroutines.tasks.await
import com.spotitfly.app.ui.spots.buildSpotDeepLink
import com.spotitfly.app.ui.spots.buildSpotDeepLink
import com.spotitfly.app.ui.spots.SpotShareChatPickerSheet
import com.spotitfly.app.ui.spots.sendSpotDeepLinkToChat
import androidx.compose.foundation.clickable
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.widget.Toast


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotDetailScreen(
    spot: Spot,
    onBack: () -> Unit,
    onViewMap: (Spot) -> Unit = {},
    onSpotChanged: (Spot) -> Unit = {}
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Estado fuente del detalle: copia local que podemos refrescar
    var uiSpot by remember(spot.id) { mutableStateOf(spot) }

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var showShareSheet by remember { mutableStateOf(false) }
    var showChatPicker by remember { mutableStateOf(false) }

    // Marca este spot como "visto" por el usuario (para el badge de comentarios)
    LaunchedEffect(uiSpot.id) {
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid ?: return@LaunchedEffect
        try {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("spotReads")
                .document(uiSpot.id)
                .set(
                    mapOf("lastSeenAt" to FieldValue.serverTimestamp()),
                    SetOptions.merge()
                )
        } catch (_: Exception) {
            // No rompemos la UI si falla esta escritura
        }
    }


    // Favoritos (1:1 iOS → VM global + ids)
    val favoritesVM: FavoritesViewModel = viewModel()
    val favoriteIds by favoritesVM.favoriteIds.collectAsState()
    val isFavorite = favoriteIds.contains(uiSpot.id)

    // UI
    var showVoting by remember { mutableStateOf(false) }
    var lockVoteButton by remember { mutableStateOf(false) }
    var myVoteUi by remember { mutableStateOf<Int?>(null) }
    var voteThisSession by remember { mutableStateOf<Int?>(null) }
    val snack = remember { SnackbarHostState() }

    var showComments by remember { mutableStateOf(false) }
    var showAccess by remember { mutableStateOf(false) }
    var commentCountOverride by remember(uiSpot.id) { mutableStateOf<Int?>(null) }
    val safeBaseComments = (uiSpot.commentCount ?: 0).coerceAtLeast(0)
    val commentsCountToShow = (commentCountOverride ?: safeBaseComments).coerceAtLeast(0)

    var showRestrictions by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    // Tiempo
    val weatherRepo = remember { WeatherRepository() }
    var weatherExpanded by remember { mutableStateOf(false) }
    var daily by remember(uiSpot.latitude, uiSpot.longitude) { mutableStateOf(emptyList<WeatherDaily>()) }
    var hourly by remember { mutableStateOf(emptyList<WeatherHourly>()) }
    var showHourly by remember { mutableStateOf(false) }
    var hourlyTitle by remember { mutableStateOf("") }

    // --- Contexto Aéreo (repo por punto) ---
    val contextRepo = remember { ContextRepository(context = ctx.applicationContext) }
    var contextLoading by remember(uiSpot.latitude, uiSpot.longitude) { mutableStateOf(true) }
    var contextData by remember(uiSpot.latitude, uiSpot.longitude) { mutableStateOf<PointContextResult?>(null) }
    var contextLocality by remember(uiSpot.latitude, uiSpot.longitude) { mutableStateOf<String?>(null) }

    LaunchedEffect(uiSpot.latitude, uiSpot.longitude) {
        contextLoading = true
        val res = contextRepo.fetchForSpot(uiSpot.latitude, uiSpot.longitude)
        contextData = res
        contextLocality = null
        contextLoading = false
    }

    val scroll = rememberScrollState()
    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val isOwner = remember(uiSpot.id, uid) { uid != null && uid == uiSpot.createdBy }

    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ===== Imagen héroe: main local → thumb local → URL =====
    val heroHeightDp = 220.dp
    val heroWidthPx = with(density) {
        val padPx = T.padPage.roundToPx() * 2
        (ctx.resources.displayMetrics.widthPixels - padPx).coerceAtLeast(200)
    }
    val heroHeightPx = with(density) { heroHeightDp.roundToPx() }

    val mainFile = remember(uiSpot.id) { spotLocalMainFile(ctx, uiSpot.id) }
    val thumbFile = remember(uiSpot.id) { spotLocalThumbFile(ctx, uiSpot.id) }
    var mainExists by remember(uiSpot.id) { mutableStateOf(mainFile.exists()) }
    var imageBust by remember(uiSpot.id) { mutableStateOf(0) } // fuerza recarga de Coil cuando guardamos main

    // Cache rating (sin flicker)
    val cachedMean = remember(uiSpot.id) { loadCachedMean(ctx, uiSpot.id) }
    val cachedCount = remember(uiSpot.id) { loadCachedCount(ctx, uiSpot.id) }

    var remoteMean by remember(uiSpot.id) {
        mutableStateOf<Double?>(cachedMean ?: uiSpot.ratingMean ?: uiSpot.averageRating)
    }
    var remoteCount by remember(uiSpot.id) {
        mutableStateOf(uiSpot.ratings?.size ?: cachedCount)
    }
    var remoteUserVote by remember(uiSpot.id) {
        mutableStateOf<Int?>(uiSpot.ratings?.get(uid))
    }

    // Carga tiempo
    LaunchedEffect(uiSpot.latitude, uiSpot.longitude) {
        daily = runCatching { weatherRepo.getDaily(uiSpot.latitude, uiSpot.longitude) }.getOrElse { emptyList() }
    }

    // Prefijar mi voto local o remoto
    LaunchedEffect(uiSpot.id, uid, ctx) {
        val u = uid ?: return@LaunchedEffect
        val local = loadMyRating(ctx, uiSpot.id, u)
        val remote = uiSpot.ratings?.get(u)
        myVoteUi = local ?: remote
    }

    // Snapshot vivo: refresca ratings y resto de campos visibles + imageUrl
    DisposableEffect(uiSpot.id, uid) {
        val id = uiSpot.id
        var reg: ListenerRegistration? = null
        if (!id.isNullOrBlank()) {
            reg = FirebaseFirestore.getInstance()
                .collection("spots")
                .document(id)
                .addSnapshotListener { snap, _ ->
                    val doc = snap ?: return@addSnapshotListener

                    // ratings
                    val map = doc.get("ratings") as? Map<*, *>
                    val u = uid
                    val serverUserVote = (map?.get(u) as? Number)?.toInt()

                    var m: Double? = null
                    var c: Int? = null
                    if (map != null) {
                        val values = map.values.mapNotNull { (it as? Number)?.toInt() }
                        if (values.isNotEmpty()) {
                            c = values.size
                            m = values.sum().toDouble() / c!!
                        } else {
                            c = 0; m = 0.0
                        }
                    } else {
                        val mm = doc.getDouble("averageRating")
                        val cc = doc.getLong("ratingsCount")?.toInt()
                        if (mm != null && cc != null) { m = mm; c = cc }
                    }

                    val waiting =
                        (voteThisSession != null && u != null && serverUserVote != voteThisSession)

                    if (u != null && serverUserVote != null) {
                        remoteUserVote = serverUserVote
                    }
                    if (!waiting) {
                        if (m != null && c != null) {
                            remoteMean = m
                            remoteCount = c
                            saveCachedMeanCount(ctx, id, m, c)
                        }
                        if (voteThisSession != null && serverUserVote == voteThisSession) {
                            voteThisSession = null
                        }
                    }

                    // Resto de campos visibles
                    val updated = uiSpot.copy(
                        name = doc.getString("name") ?: uiSpot.name,
                        description = doc.getString("description") ?: uiSpot.description,
                        category = parseCategory(doc.getString("category")) ?: uiSpot.category,
                        acceso = doc.getString("acceso") ?: uiSpot.acceso,
                        bestDate = doc.getString("bestDate") ?: uiSpot.bestDate,
                        latitude = doc.getDouble("latitude") ?: uiSpot.latitude,
                        longitude = doc.getDouble("longitude") ?: uiSpot.longitude,
                        locality = doc.getString("localidad") ?: doc.getString("locality") ?: uiSpot.locality,
                        imageUrl = doc.getString("imageUrl") ?: uiSpot.imageUrl
                    )
                    if (updated != uiSpot) {
                        uiSpot = updated
                        onSpotChanged(updated)
                    }
                }
        }
        onDispose { reg?.remove() }
    }

    // Media mostrada con overlay de voto en sesión
    val ratingAvgUi by remember(remoteMean, remoteCount, voteThisSession, remoteUserVote) {
        derivedStateOf {
            val baseC = (remoteCount ?: 0).coerceAtLeast(0)
            val baseM = (remoteMean ?: 0.0)
            val mine = voteThisSession
            val old = remoteUserVote
            when {
                baseC <= 0 && mine != null -> mine.toDouble()
                mine == null || baseC <= 0 -> baseM
                old == null -> ((baseM * baseC) + mine) / (baseC + 1)
                else -> ((baseM * baseC) - old + mine) / baseC
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(T.surface)
            .statusBarsPadding()       // ← respeta safe area superior
            .navigationBarsPadding()    // 👈 empuja todo por encima de la barra de navegación
            .verticalScroll(scroll)
    ) {
        // ===== Imagen (Hero) con fallback local y guardado pre-escalado =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(T.padPage)
        ) {
            Box(Modifier.fillMaxWidth()) {
                val dataSource: Any? = when {
                    mainExists -> mainFile
                    thumbFile.exists() -> thumbFile
                    !uiSpot.imageUrl.isNullOrBlank() -> uiSpot.imageUrl
                    else -> null
                }

                if (dataSource != null) {
                    val model = ImageRequest.Builder(ctx)
                        .data(dataSource)
                        .size(heroWidthPx, heroHeightPx) // decodifica al tamaño del héroe
                        .crossfade(dataSource !is File)   // sin crossfade para local
                        .setParameter("bust", imageBust.toString())
                        .build()

                    AsyncImage(
                        model = model,
                        contentDescription = "Foto del spot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeightDp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        onSuccess = { success ->
                            // Si vino de URL o thumb, guarda main pre-escalado y conmuta
                            if (!mainExists) {
                                runCatching {
                                    saveDrawableScaledToFile(
                                        success.result.drawable,
                                        mainFile,
                                        heroWidthPx,
                                        heroHeightPx,
                                        88
                                    )
                                    mainExists = true
                                    imageBust += 1
                                }
                            }
                        }
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeightDp)
                            .shadow(4.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFECECEC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Sin imagen", color = Color(0xFF9E9E9E))
                    }
                }
            }

            // Cerrar
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(T.floatSize)
                    .shadow(T.floatShadow, CircleShape)
                    .clip(CircleShape)
                    .background(T.floatBg)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = T.floatFg)
            }

            // Favorito (activo con VM)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(T.floatSize)
                    .shadow(T.floatShadow, CircleShape)
                    .clip(CircleShape)
                    .background(T.favScrimBg)
                    .clickable {
                        favoritesVM.toggleFavoriteAsync(uiSpot.id)
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isFavorite) {
                    // Relleno rojo + contorno rojo
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = T.favOn,
                        modifier = Modifier.size(22.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = T.favOn,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    // Solo contorno (estado off)
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = T.favOff,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }


        }

        // ===== Cabecera =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isOwner) {
                // Botón "Editar" estilo iOS
                Row(
                    modifier = Modifier
                        .background(
                            color = Color(0x1F007AFF), // azul iOS con ~12% de opacidad
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { showEdit = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Editar",
                        tint = Color(0xFF007AFF)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Editar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF007AFF)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Botón "Borrar" estilo iOS destructivo
                Row(
                    modifier = Modifier
                        .background(
                            color = Color.Red.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { showDeleteConfirm = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Borrar",
                        tint = Color.Red
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Borrar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }
        }


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage)
        ) {
            Spacer(Modifier.height(T.gapXL))
            Text(
                text = uiSpot.name.ifBlank { "Nombre del spot" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = T.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(T.gapS))

            AuthorUsernameText(
                uid = uiSpot.createdBy,
                enabled = !isOwner && !uiSpot.createdBy.isNullOrBlank()
            ) {
                val authorId = uiSpot.createdBy?.trim().orEmpty()
                if (authorId.isBlank() || uid == null || authorId == uid) return@AuthorUsernameText

                scope.launch {
                    try {
                        // Leemos el usuario para montar el NewChatUser
                        val doc = db.collection("users")
                            .document(authorId)
                            .get()
                            .await()

                        val username = doc.getString("username")
                            ?: doc.getString("displayName")
                            ?: doc.getString("name")
                            ?: authorId

                        val displayName = doc.getString("displayName")
                            ?: doc.getString("name")

                        val avatarUrl = doc.getString("profileImageUrl")
                            ?: doc.getString("photoUrl")

                        val user = NewChatUser(
                            uid = authorId,
                            username = username,
                            displayName = displayName,
                            avatarUrl = avatarUrl
                        )

                        val chat = createOrOpenChatWithUser(
                            db = db,
                            auth = auth,
                            user = user,
                            scope = scope
                        )

                        if (chat != null) {
                            // Lanzamos deeplink de chat (ya integrado en AppRoot)
                            SpotsDeepLinkBus.emit(SpotsDeepLink.Chat(chat.id))
                        }
                    } catch (_: Exception) {
                        // Si quieres, aquí se podría mostrar un snackbar de error
                    }
                }
            }

            Spacer(Modifier.height(T.gapS))
            CategoryChip(label = categoryLabel(uiSpot.category))

            Spacer(Modifier.height(T.gapM))
            Text(
                text = uiSpot.description.ifBlank { "Descripción del spot…" },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = T.descFontSizeSp),
                color = T.textBody
            )

            // ===== Rating row =====
            Spacer(Modifier.height(T.gapXL))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(
                        average = ratingAvgUi,
                        size = T.starSize,
                        colorOn = T.starOn,
                        colorOff = T.starOff
                    )
                    Spacer(Modifier.width(T.gapM))
                    Text(
                        text = String.format(Locale.US, "%.1f", ratingAvgUi),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    showShareSheet = true
                }) {
                    Icon(Icons.Outlined.IosShare, contentDescription = "Compartir", tint = T.textPrimary)
                }
                Spacer(Modifier.weight(1f))

                Text(
                    text = "VOTAR",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(T.voteCorner))
                        .background(T.voteBg)
                        .clickable(enabled = !showVoting && !lockVoteButton) { showVoting = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            AnimatedVisibility(
                visible = showVoting,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
            ) {
                Spacer(Modifier.height(T.gapS))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    InteractiveStars(
                        value = myVoteUi,
                        onSelect = { v ->
                            myVoteUi = v
                            showVoting = false
                            lockVoteButton = true
                            scope.launch {
                                try {
                                    val u = uid
                                    val spotId = uiSpot.id
                                    if (u != null && !spotId.isNullOrBlank()) {
                                        voteThisSession = v
                                        saveMyRating(ctx, spotId, u, v)

                                        val baseC = (remoteCount ?: 0).coerceAtLeast(0)
                                        val baseM = (remoteMean ?: 0.0)
                                        val old = remoteUserVote
                                        val (newMean, newCount) = if (old == null) {
                                            (((baseM * baseC) + v) / (baseC + 1)) to (baseC + 1)
                                        } else {
                                            (((baseM * baseC) - old + v) / baseC) to baseC
                                        }
                                        saveCachedMeanCount(ctx, spotId, newMean, newCount)

                                        FirebaseFirestore.getInstance()
                                            .collection("spots")
                                            .document(spotId)
                                            .update("ratings.$u", v)
                                            .addOnFailureListener {
                                                voteThisSession = null
                                            }
                                    }
                                } finally {
                                    delay(260)
                                    lockVoteButton = false
                                }
                            }
                        },
                        size = 64.dp,
                        colorOn = T.starOn,
                        colorOff = T.starOff,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = T.padPage),
                        arrangement = Arrangement.SpaceBetween,
                        spacing = 1.dp
                    )
                }
                Spacer(Modifier.height(T.gapS))
            }

            // ===== Tiempo (pill + 15 días) =====
            Spacer(Modifier.height(T.gapM))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (daily.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE9E9EE))
                            .clickable { weatherExpanded = !weatherExpanded }
                    ) {
                        WeatherCompactPill(day = daily.first(), height = 26.dp)
                    }
                } else {
                    Text(
                        "—°",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE9E9EE))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(T.gapM))
                Row(
                    modifier = Modifier.clickable(enabled = daily.isNotEmpty()) {
                        weatherExpanded = !weatherExpanded
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (weatherExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF6E6E6E)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("15 días", style = MaterialTheme.typography.titleSmall)
                }
            }

            if (weatherExpanded && daily.isNotEmpty()) {
                Spacer(Modifier.height(T.gapS))
                Weather15DayView(
                    days = daily,
                    onSelect = { d ->
                        hourlyTitle = "${d.dayLabel} ${d.dateLabel}"
                        scope.launch {
                            hourly = runCatching {
                                weatherRepo.getHourly(uiSpot.latitude, uiSpot.longitude, d.isoDate)
                            }.getOrElse { emptyList() }
                            showHourly = true
                        }
                    }
                )
            }

            // ===== Comentarios + Acceso =====
            Spacer(Modifier.height(T.gapXL))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clickable { showComments = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comentarios",
                        modifier = Modifier.size(T.calendarIconSize)
                    )
                    Spacer(Modifier.width(T.gapS))
                    Text(
                        text = commentsCountToShow.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = T.textMuted
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Acceso",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .clip(RoundedCornerShape(T.accessCorner))
                        .background(T.accessGreen)
                        .clickable { showAccess = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            if (!uiSpot.bestDate.isNullOrBlank()) {
                Spacer(Modifier.height(T.gapM))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = T.bestDateTint,
                        modifier = Modifier.size(T.calendarIconSize)
                    )
                    Spacer(Modifier.width(T.gapS))
                    Text(uiSpot.bestDate ?: "", color = T.bestDateTint, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(T.gapM))
            Divider(color = T.divider, thickness = 1.dp)
        }

        // ===== Restricciones (real) =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(T.padPage),
            verticalArrangement = Arrangement.spacedBy(T.gapM)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(T.restrCorner))
                    .background(T.restrRed)
                    .clickable { showRestrictions = !showRestrictions }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.Campaign, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Restricciones",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (showRestrictions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            if (showRestrictions) {
                Column(verticalArrangement = Arrangement.spacedBy(T.gapM)) {

                    // Cabecera de contexto: localidad (si disponible)
                    contextLocality?.takeIf { it.isNotBlank() }?.let {
                        /*Text(
                            it,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )*/
                    }

                    // Contenido: secciones reales 1:1 con iOS
                    if (contextLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Cargando contexto…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val d = contextData
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp) // altura fija; el contenido dentro hace scroll
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ContextSectionsScaffold(
                                    restricciones    = d?.restricciones    ?: emptyList(),
                                    infraestructuras = d?.infraestructuras ?: emptyList(),
                                    medioambiente    = d?.medioambiente    ?: emptyList(),
                                    notams           = d?.notams           ?: emptyList(),
                                    urbanas          = d?.urbanas          ?: emptyList()
                                )
                            }
                        }

                    }

                    // Mantengo tu botón de recarga
                    TextButton(
                        onClick = {
                            scope.launch {
                                contextLoading = true
                                val res = contextRepo.fetchForSpot(uiSpot.latitude, uiSpot.longitude)
                                contextData = res
                                contextLocality = res.locality
                                contextLoading = false
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = T.textPrimary)
                    ) {
                        Text(
                            "Recargar datos",
                            modifier = Modifier
                                .clip(RoundedCornerShape(T.voteCorner))
                                .background(T.restrReloadBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

        }

        // ===== Localidad =====
        if (!uiSpot.locality.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = T.padPage),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = T.pinRed)
                Spacer(Modifier.width(T.gapS))
                Text(
                    uiSpot.locality ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = T.textPrimary
                )
            }
        }

        // ===== Mini-mapa =====
        val spotLatLng = remember(uiSpot.latitude, uiSpot.longitude) {
            LatLng(uiSpot.latitude, uiSpot.longitude)
        }
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(spotLatLng, 15f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage, vertical = T.gapXL)
                .height(180.dp)
                .shadow(T.miniMapShadow, RoundedCornerShape(T.miniMapCorner))
                .clip(RoundedCornerShape(T.miniMapCorner))
        ) {
            GoogleMap(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        showHourly = false
                        onViewMap(uiSpot)
                    },
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    scrollGesturesEnabled = false,
                    zoomGesturesEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false
                ),
                properties = MapProperties(isMyLocationEnabled = false),
                onMapClick = {
                    showHourly = false
                    onViewMap(uiSpot)
                }
            ) {
                Marker(
                    state = MarkerState(position = spotLatLng),
                    title = uiSpot.name.ifBlank { "Spot" },
                    onClick = {
                        showHourly = false
                        onViewMap(uiSpot)
                        true
                    }
                )
            }
        }

        // ===== Botón IR + snackbar host =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage)
        ) {
            Button(
                onClick = {
                    openDirectionsInGoogleMaps(
                        context = ctx,
                        lat = uiSpot.latitude,
                        lon = uiSpot.longitude
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(T.goCorner),
                colors = ButtonDefaults.buttonColors(containerColor = T.goPurple, contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null)
                Spacer(Modifier.width(T.gapM))
                Text("Ir", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            }
            Box(Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snack,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                )
            }
        }

        // ===== Reportar =====
        Spacer(Modifier.height(T.gapM))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "Reportar Spot",
                color = T.danger,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(T.reportCorner))
                    .background(T.dangerBg)
                    .clickable { showReportDialog = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false }
        ) {
            CommentsSheet(
                spotId = uiSpot.id,
                spotDescription = uiSpot.description,
                initialCount = safeBaseComments,
                onUpdatedCount = { newCount ->
                    commentCountOverride = newCount
                }
            )
        }
    }
    if (showAccess) {
        AlertDialog(
            onDismissRequest = { showAccess = false },
            confirmButton = { TextButton(onClick = { showAccess = false }) { Text("Cerrar") } },
            title = { Text("Acceso") },
            text = { Text(uiSpot.acceso ?: "") }
        )
    }
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            confirmButton = { TextButton(onClick = { showReportDialog = false }) { Text("Cerrar") } },
            title = { Text("Reportar Spot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige un motivo (placeholder):")
                    Text("• Spam")
                    Text("• Contenido falso/incorrecto")
                    Text("• Contenido inapropiado")
                    Text("• Discurso de odio/Insultos")
                    Text("• Datos personales")
                }
            }
        )
    }

    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Editar (sheet)
    if (showEdit) {
        ModalBottomSheet(
            onDismissRequest = { showEdit = false },
            sheetState = editSheetState
        ) {
            SpotEditScreen(
                existing = uiSpot,
                onCancel = { showEdit = false },
                onSaved = { updated ->
                    // Refresca inmediatamente el detalle con lo que venga del form
                    uiSpot = updated
                    onSpotChanged(updated)
                    showEdit = false
                }
            )
        }
    }

    // Confirmar borrado (borra y vuelve)
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Borrar spot") },
            text = { Text("Esta acción borrará el spot definitivamente. ¿Seguro que quieres continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val repo = SpotsRepository()
                    scope.launch { snack.showSnackbar("Spot borrado") }
                    scope.launch {
                        try {
                            repo.deleteSpot(uiSpot.id ?: return@launch)
                            delay(600)
                            onBack()
                        } catch (e: Exception) {
                            snack.showSnackbar(e.message ?: "Error al borrar")
                        }
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } }
        )
    }
    // Sheet de opciones de compartir (detalle)
    if (showShareSheet) {
        val deepLink = remember(uiSpot.id) {
            buildSpotDeepLink(uiSpot)
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                showShareSheet = false
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Compartir spot",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Opción 1: compartir en un chat
                ListItem(
                    modifier = Modifier.clickable {
                        showShareSheet = false
                        showChatPicker = true
                    },
                    headlineContent = { Text("Compartir en un chat") },
                    supportingContent = { Text("Elige un chat de destino") }
                )

                // Opción 2: copiar enlace
                ListItem(
                    modifier = Modifier.clickable {
                        clipboardManager.setText(AnnotatedString(deepLink))
                        showShareSheet = false
                        Toast.makeText(ctx, "Spot copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    headlineContent = { Text("Copiar enlace") }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Sheet 2: picker de chats para enviar el deeplink (detalle)
    if (showChatPicker) {
        val deepLink = remember(uiSpot.id) {
            buildSpotDeepLink(uiSpot)
        }

        SpotShareChatPickerSheet(
            onDismiss = {
                showChatPicker = false
            },
            onChatSelected = { chatId ->
                coroutineScope.launch {
                    try {
                        sendSpotDeepLinkToChat(chatId, deepLink)
                        Toast.makeText(ctx, "Spot compartido", Toast.LENGTH_SHORT).show()
                    } finally {
                        showChatPicker = false
                    }
                }
            }
        )
    }



}

/* ===================== helpers ui + cache ===================== */

private fun loadMyRating(ctx: Context, spotId: String, uid: String): Int? {
    val prefs = ctx.getSharedPreferences("ratings_local", Context.MODE_PRIVATE)
    val key = "spot.$spotId.user.$uid"
    val v = prefs.getInt(key, -1)
    return if (v in 1..5) v else null
}

private fun saveMyRating(ctx: Context, spotId: String, uid: String, stars: Int) {
    if (stars !in 1..5) return
    val prefs = ctx.getSharedPreferences("ratings_local", Context.MODE_PRIVATE)
    val key = "spot.$spotId.user.$uid"
    prefs.edit().putInt(key, stars).apply()
}

// Cache persistente de media/contador
private fun cachePrefs(ctx: Context) =
    ctx.getSharedPreferences("ratings_cache", Context.MODE_PRIVATE)

private fun loadCachedMean(ctx: Context, spotId: String?): Double? {
    val id = spotId ?: return null
    val s = cachePrefs(ctx).getString("mean.$id", null) ?: return null
    return s.toDoubleOrNull()
}

private fun loadCachedCount(ctx: Context, spotId: String?): Int? {
    val id = spotId ?: return null
    val v = cachePrefs(ctx).getInt("count.$id", -1)
    return if (v >= 0) v else null
}

private fun saveCachedMeanCount(ctx: Context, spotId: String?, mean: Double, count: Int) {
    val id = spotId ?: return
    cachePrefs(ctx).edit()
        .putString("mean.$id", mean.toString())
        .putInt("count.$id", count)
        .apply()
}

private fun startActivitySafe(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent) } catch (_: Exception) { }
}

// Rutas locales consistentes con la lista
private fun spotLocalMainFile(ctx: Context, spotId: String?): File {
    val safeId = (spotId ?: "unknown").replace(Regex("[^A-Za-z0-9_-]"), "_")
    val dir = File(ctx.filesDir, "spotImages")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${safeId}-main.jpg")
}

private fun spotLocalThumbFile(ctx: Context, spotId: String?): File {
    val safeId = (spotId ?: "unknown").replace(Regex("[^A-Za-z0-9_-]"), "_")
    val dir = File(ctx.filesDir, "spotImages")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${safeId}-thumb.jpg")
}

// Guardado pre-escalado a tamaño héroe para decodificación instantánea posterior
private fun saveDrawableScaledToFile(
    drawable: Drawable,
    file: File,
    targetW: Int,
    targetH: Int,
    qualityJpeg: Int
) {
    val src = when (drawable) {
        is BitmapDrawable -> drawable.bitmap
        else -> {
            val w = maxOf(1, drawable.intrinsicWidth)
            val h = maxOf(1, drawable.intrinsicHeight)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)
            bmp
        }
    }
    val ratioSrc = src.width.toFloat() / src.height.toFloat()
    val ratioDst = targetW.toFloat() / targetH.toFloat()
    val (dstW, dstH) = if (ratioSrc > ratioDst) {
        targetW to (targetW / ratioSrc).toInt().coerceAtLeast(1)
    } else {
        (targetH * ratioSrc).toInt().coerceAtLeast(1) to targetH
    }
    val scaled = if (src.width != dstW || src.height != dstH) {
        Bitmap.createScaledBitmap(src, dstW, dstH, true)
    } else src

    file.parentFile?.mkdirs()
    file.outputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, qualityJpeg.coerceIn(60, 95), out)
    }
}


@Composable
private fun AuthorUsernameText(
    uid: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var name by remember(uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        val u = uid?.trim().orEmpty()
        if (u.isEmpty()) return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(u)
            .get()
            .addOnSuccessListener { doc ->
                name = doc.getString("username")
                    ?: doc.getString("displayName")
                            ?: doc.getString("name")
                            ?: u
            }
            .addOnFailureListener { name = u }
    }

    Text(
        text = name ?: "",
        color = Color(0xFF1976D2),
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.then(
            if (enabled) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    )
}


@Composable
private fun CategoryChip(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(T.catCorner))
            .background(T.catBlueBg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(Locale.getDefault()), color = T.catBlue, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RatingStars(average: Double, size: Dp, colorOn: Color, colorOff: Color) {
    val filled = average.coerceIn(0.0, 5.0).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val on = i < filled
            Text(
                if (on) "★" else "☆",
                color = if (on) colorOn else colorOff,
                fontSize = size.value.sp
            )
            if (i < 4) Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun InteractiveStars(
    value: Int?,
    onSelect: (Int) -> Unit,
    size: Dp,
    colorOn: Color,
    colorOff: Color,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.Center,
    spacing: Dp = 2.dp
) {
    val current = (value ?: 0).coerceIn(0, 5)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = arrangement
    ) {
        repeat(5) { i ->
            val idx = i + 1
            val on = idx <= current
            Text(
                text = if (on) "★" else "☆",
                color = if (on) colorOn else colorOff,
                fontSize = size.value.sp,
                modifier = Modifier
                    .padding(horizontal = spacing / 2)
                    .clickable { onSelect(idx) }
            )
        }
    }
}

private fun parseCategory(raw: String?): SpotCategory? {
    if (raw.isNullOrBlank()) return null
    return when (raw.trim()) {
        "Freestyle campo abierto" -> SpotCategory.FREESTYLE_CAMPO_ABIERTO
        "Freestyle Bando" -> SpotCategory.FREESTYLE_BANDO
        "Cinemático" -> SpotCategory.CINEMATICO
        "Racing" -> SpotCategory.RACING
        "Otros" -> SpotCategory.OTROS
        else -> runCatching { SpotCategory.valueOf(raw.trim()) }.getOrNull()
    }
}

private fun categoryLabel(cat: SpotCategory?): String = when (cat) {
    SpotCategory.FREESTYLE_CAMPO_ABIERTO -> "Freestyle campo abierto"
    SpotCategory.FREESTYLE_BANDO -> "Freestyle Bando"
    SpotCategory.CINEMATICO -> "Cinemático"
    SpotCategory.RACING -> "Racing"
    SpotCategory.OTROS, null -> "Otros"
}

private fun shouldUseWaze(context: Context): Boolean {
    val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    val raw = prefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
    return raw == "waze"
}


private fun openDirectionsInGoogleMaps(
    context: Context,
    lat: Double,
    lon: Double
) {
    val pm = context.packageManager

    val prefs = context.getSharedPreferences("user_profile", Context.MODE_PRIVATE)
    val raw = prefs.getString("preferredNavApp", "google_maps") ?: "google_maps"
    val useWaze = raw == "waze"

    // 1️⃣ Intentar Waze primero si el usuario lo ha elegido
    if (useWaze) {
        try {
            pm.getPackageInfo("com.waze", 0)

            val wazeUri = Uri.parse("waze://?ll=$lat,$lon&navigate=yes")
            val wazeIntent = Intent(Intent.ACTION_VIEW, wazeUri).apply {
                setPackage("com.waze")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(wazeIntent)
            return
        } catch (_: Exception) {
            // Si falla, saltamos al fallback Google Maps
        }
    }

    // 2️⃣ Fallback: Google Maps (app)
    val gmmIntentUri = Uri.parse("google.navigation:q=$lat,$lon&mode=d")
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val hasGMaps = try {
        pm.getPackageInfo("com.google.android.apps.maps", PackageManager.GET_ACTIVITIES)
        true
    } catch (_: Exception) {
        false
    }

    if (hasGMaps) {
        context.startActivity(mapIntent)
    } else {
        // 3️⃣ Último recurso: navegador web
        val webUri = Uri.parse("https://maps.google.com/?daddr=$lat,$lon")
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}




