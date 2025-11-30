package com.spotitfly.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.spotitfly.app.ui.favorites.FavoritesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.ui.spots.SpotDetailScreen
import android.content.Context
import com.spotitfly.app.ui.spots.SpotsListScreen
import com.spotitfly.app.ui.spots.FavoritesListScreen
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import com.spotitfly.app.ui.spots.SpotCreateScreen
import com.spotitfly.app.ui.context.PointContextSheet
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.filled.Favorite
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import com.spotitfly.app.ui.spots.SpotRow
import com.spotitfly.app.ui.spots.SpotsListViewModel
import com.spotitfly.app.ui.spots.SpotsListViewModelFactory
import com.spotitfly.app.ui.spots.SpotWithDistance
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.comments.CommentReadService
import kotlinx.coroutines.launch
import com.spotitfly.app.data.model.SpotMapper
import kotlin.math.*
import androidx.compose.foundation.Image
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.spotitfly.app.ui.chats.ChatsHomeViewModel
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.spotitfly.app.ui.spots.MySpotsListScreen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource




// Pantallas principales
// Pantallas principales
// Pantallas principales
// Pantallas principales
private enum class Screen {
    Map,
    Chats,
    Spots,
    Favorites,
    Profile,
    MySpots,
    CommunityGuidelines,
    SpotDetail,
    Comments
}


// Origen del detalle (para decidir el back)
enum class DetailOrigin { Map, SpotsList, FavoritesList, MySpots, Chats }



// Tipo de mapa (paridad iOS)
enum class MapKind { STANDARD, IMAGERY, HYBRID }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    initialDeepLink: SpotsDeepLink? = null
) {
    val auth = remember { FirebaseAuth.getInstance() }
    var isLoggedIn by rememberSaveable { mutableStateOf(auth.currentUser != null) }
    var isVerified by rememberSaveable { mutableStateOf(auth.currentUser?.isEmailVerified == true) }

    // VM global de favoritos (1 instancia por Activity)
    val favoritesVM: FavoritesViewModel = viewModel()

    // Estado de scroll para las listas (se conserva al ir/volver del detalle)
    val spotsListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()



    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            val u = fa.currentUser
            isLoggedIn = u != null
            isVerified = u?.isEmailVerified == true

            if (u != null && u.isEmailVerified) {
                favoritesVM.startListening(u.uid)
            } else {
                favoritesVM.clearForLogout()
            }
        }
        auth.addAuthStateListener(listener)

        // Estado inicial al arrancar la app
        val current = auth.currentUser
        if (current != null && current.isEmailVerified) {
            favoritesVM.startListening(current.uid)
        } else {
            favoritesVM.clearForLogout()
        }

        onDispose {
            auth.removeAuthStateListener(listener)
            favoritesVM.clearForLogout()
        }
    }

    if (!isLoggedIn || !isVerified) {
        com.spotitfly.app.auth.AuthRoot(onAuthenticated = {
            val u = auth.currentUser
            isLoggedIn = u != null
            isVerified = u?.isEmailVerified == true
        })
        return
    }

    var screen by remember { mutableStateOf(Screen.Map) }
    var selectedSpot by remember { mutableStateOf<Spot?>(null) }
    var detailOrigin by remember { mutableStateOf(DetailOrigin.Map) }




    // Sheet de creación hoisted en AppRoot (no dentro de MapRoute)
    var openCreateSpotSheet by remember { mutableStateOf(false) }
    var createCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Recentrado dirigido al mapa (lista/detalle → mapa)
    var centerOnSpotTick by remember { mutableStateOf(0) }
    var centerTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // Recentrado sin offset (resultados de búsqueda genéricos)
    var centerOnCoordinateTick by remember { mutableStateOf(0) }
    var centerCoordinateTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // ÚNICO estado para el sheet de contexto aéreo
    var openPointContext by remember { mutableStateOf(false) }
    var pointContextCenter by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var openSearch by remember { mutableStateOf(false) }

    // Deep link: invitación a chat / chat / spot
    var pendingInviteForChats by remember { mutableStateOf<String?>(null) }
    var pendingChatIdForChats by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        SpotsDeepLinkBus.events.collectLatest { deepLink ->
            Log.d(
                "SpotsDeepLink",
                "AppRoot.collect: deepLink=$deepLink currentScreen=$screen selectedSpotId=${selectedSpot?.id}"
            )

            when (deepLink) {
                is SpotsDeepLink.Invite -> {
                    Log.d(
                        "SpotsDeepLink",
                        "AppRoot: handling Invite code=${deepLink.code}"
                    )
                    // Invite: guardamos el código para pasarlo a ChatsHomeScreen
                    pendingInviteForChats = deepLink.code.uppercase()
                    // Nos vamos a la pestaña de chats (igual que si pulsaras el botón de chats)
                    screen = Screen.Chats
                    Log.d(
                        "SpotsDeepLink",
                        "AppRoot: Invite -> screen=Screen.Chats pendingInviteForChats=$pendingInviteForChats"
                    )
                }

                is SpotsDeepLink.Chat -> {
                    Log.d(
                        "SpotsDeepLink",
                        "AppRoot: handling Chat chatId=${deepLink.chatId}"
                    )
                    // Chat: guardamos el chatId y abrimos la pestaña de chats
                    pendingInviteForChats = null
                    pendingChatIdForChats = deepLink.chatId
                    screen = Screen.Chats
                    Log.d(
                        "SpotsDeepLink",
                        "AppRoot: Chat -> screen=Screen.Chats pendingChatIdForChats=$pendingChatIdForChats"
                    )
                }


                is SpotsDeepLink.Spot -> {
                    Log.d(
                        "SpotsDeepLink",
                        "AppRoot: handling Spot spotId=${deepLink.spotId}"
                    )
                    // Notificación / enlace de SPOT:
                    // cargamos el spot por id y abrimos el detalle
                    try {
                        val db = FirebaseFirestore.getInstance()
                        val snap = db.collection("spots")
                            .document(deepLink.spotId)
                            .get()
                            .await()

                        val spot = SpotMapper.from(snap)

                        if (spot != null) {
                            selectedSpot = spot

                            if (deepLink.fromChatId != null) {
                                // 👇 Viene desde un chat concreto: al cerrar detalle queremos
                                // volver a la pestaña de chats y abrir otra vez ese chat.
                                pendingChatIdForChats = deepLink.fromChatId
                                detailOrigin = DetailOrigin.Chats
                            } else {
                                // Resto de casos: elegimos origen según pantalla actual
                                detailOrigin = when (screen) {
                                    Screen.Spots -> DetailOrigin.SpotsList
                                    Screen.Favorites -> DetailOrigin.FavoritesList
                                    Screen.MySpots -> DetailOrigin.MySpots
                                    else -> DetailOrigin.Map
                                }
                            }

                            screen = Screen.SpotDetail

                            Log.d(
                                "SpotsDeepLink",
                                "AppRoot: Spot loaded -> screen=Screen.SpotDetail spotId=${spot.id} detailOrigin=$detailOrigin pendingChatIdForChats=$pendingChatIdForChats"
                            )
                        } else {
                            Log.w(
                                "SpotsDeepLink",
                                "AppRoot: Spot no encontrado para deeplink spotId=${deepLink.spotId}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "SpotsDeepLink",
                            "AppRoot: Error cargando spot para deeplink spotId=${deepLink.spotId}",
                            e
                        )
                    }
                }

            }
        }
    }





    BackHandler(enabled = screen != Screen.Map) {
        if (screen == Screen.SpotDetail) {
            val dest = when (detailOrigin) {
                DetailOrigin.SpotsList -> Screen.Spots
                DetailOrigin.FavoritesList -> Screen.Favorites
                DetailOrigin.MySpots -> Screen.MySpots
                DetailOrigin.Chats -> Screen.Chats
                DetailOrigin.Map -> Screen.Map
            }
            selectedSpot = null
            screen = dest
        } else {
            screen = Screen.Map
        }

    }



    Box(Modifier.fillMaxSize()) {

        // 1) Mapa SIEMPRE montado (fondo)
        MapRoute(
            onTapAvatar = { screen = Screen.Profile },
            onTapChatSheet = { screen = Screen.Comments },
            onTapChats = { screen = Screen.Chats },
            onTapSpotsList = { screen = Screen.Spots },
            onTapFavorites = { screen = Screen.Favorites },
            onTapSearchPill = { openSearch = true },
            onSpotTapped = { spot ->
                selectedSpot = spot
                detailOrigin = DetailOrigin.Map
                screen = Screen.SpotDetail
            },
            centerOnSpotTick = centerOnSpotTick,
            centerOnCoordinateTick = centerOnCoordinateTick,
            centerCoordinateTarget = centerCoordinateTarget,
            centerTarget = centerTarget,
            onTapCreateSpot = { center ->
                createCenter = center
                openCreateSpotSheet = true
            },
            onTapInfo = { center ->
                pointContextCenter = center
                openPointContext = true
            }
        )


        // 2) Pantallas superpuestas (no desmontan el mapa)
        if (screen == Screen.Chats) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                ChatsHomeScreen(
                    onBack = { screen = Screen.Map },
                    initialJoinInviteId = pendingInviteForChats,
                    initialChatId = pendingChatIdForChats
                )
            }
        }



        if (screen == Screen.Spots) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                SpotsListScreen(
                    onBack = { screen = Screen.Map },
                    onOpenSpot = { spot ->
                        selectedSpot = spot
                        detailOrigin = DetailOrigin.SpotsList
                        screen = Screen.SpotDetail
                    },
                    onViewMap = { spot ->
                        centerTarget = spot.latitude to spot.longitude
                        centerOnSpotTick += 1
                        screen = Screen.Map
                    },
                    onAddSpot = {
                        // Abrimos el mismo sheet de creación que el "+" del mapa,
                        // usando el centro actual del mapa (MapCameraStore) como referencia.
                        createCenter = null
                        openCreateSpotSheet = true
                    },
                    listState = spotsListState
                )

            }
        }




        if (screen == Screen.Favorites) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                FavoritesListScreen(
                    onBack = { screen = Screen.Map },
                    onOpenSpot = { spot ->
                        selectedSpot = spot
                        detailOrigin = DetailOrigin.FavoritesList
                        screen = Screen.SpotDetail
                    },
                    onViewMap = { spot ->
                        centerTarget = spot.latitude to spot.longitude
                        centerOnSpotTick += 1
                        screen = Screen.Map
                    },
                    listState = favoritesListState
                )
            }
        }

        if (screen == Screen.MySpots) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                MySpotsListScreen(
                    onBack = { screen = Screen.Profile },
                    onOpenSpot = { spot ->
                        selectedSpot = spot
                        detailOrigin = DetailOrigin.MySpots
                        screen = Screen.SpotDetail
                    }
                )
            }
        }




        if (screen == Screen.Comments) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                UnreadCommentsScreen(
                    onBack = { screen = Screen.Map },
                    onOpenSpot = { spot ->
                        selectedSpot = spot
                        detailOrigin = DetailOrigin.Map
                        screen = Screen.SpotDetail
                    }
                )
            }
        }

        if (screen == Screen.Profile) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                UserProfileScreen(
                    onBack = { screen = Screen.Map },
                    onOpenMySpots = { screen = Screen.MySpots },
                    onOpenGuidelines = { screen = Screen.CommunityGuidelines }
                )
            }
        }

        if (screen == Screen.CommunityGuidelines) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                CommunityGuidelinesScreen(
                    onBack = { screen = Screen.Profile }
                )
            }
        }


        if (screen == Screen.SpotDetail && selectedSpot != null) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
                color = Color.Transparent
            ) {
                SpotDetailScreen(
                    spot = selectedSpot!!,
                    onBack = {
                        val dest = when (detailOrigin) {
                            DetailOrigin.SpotsList -> Screen.Spots
                            DetailOrigin.FavoritesList -> Screen.Favorites
                            DetailOrigin.MySpots -> Screen.MySpots
                            DetailOrigin.Chats -> Screen.Chats
                            DetailOrigin.Map -> Screen.Map
                        }
                        selectedSpot = null
                        screen = dest
                    },

                    onViewMap = { s ->
                        selectedSpot = null
                        centerTarget = s.latitude to s.longitude
                        centerOnSpotTick += 1
                        screen = Screen.Map
                    }
                )
            }
        }

    }

        val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        // ----- Crear spot (sheet) -----
        if (openCreateSpotSheet) {
            ModalBottomSheet(
                onDismissRequest = { openCreateSpotSheet = false },
                sheetState = createSheetState
            ) {
                val center = createCenter
                    ?: (com.spotitfly.app.ui.map.MapCameraStore.lat?.let { lat ->
                        val lng = com.spotitfly.app.ui.map.MapCameraStore.lng ?: -3.7038
                        lat to lng
                    } ?: (40.4168 to -3.7038))

                SpotCreateScreen(
                    initialLat = center.first,
                    initialLng = center.second,
                    onCancel = { openCreateSpotSheet = false },
                    onCreated = { s ->
                        openCreateSpotSheet = false
                        selectedSpot = null
                        centerTarget = s.latitude to s.longitude
                        centerOnSpotTick += 1
                        screen = Screen.Map
                    }
                )
            }
        }
    // ----- Búsqueda (overlay tipo sheet) -----
    if (openSearch) {
        // Fondo semitransparente
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable { openSearch = false }
                .zIndex(3f)
        )

        // Sheet de búsqueda (anclada arriba)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(4f),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.White,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                com.spotitfly.app.ui.search.SearchOverlay(
                    onClose = { openSearch = false },
                    onSelect = { lat, lon, applyOffset ->
                        if (applyOffset) {
                            // Resultados que son también Spot → con offset
                            centerTarget = lat to lon
                            centerOnSpotTick += 1
                        } else {
                            // Sitios genéricos / direcciones / coordenadas → sin offset
                            centerCoordinateTarget = lat to lon
                            centerOnCoordinateTick += 1
                        }
                    },
                    centerLat = com.spotitfly.app.ui.map.MapCameraStore.lat,
                    centerLon = com.spotitfly.app.ui.map.MapCameraStore.lng,
                    zoom = com.spotitfly.app.ui.map.MapCameraStore.zoom
                )

            }
        }
    }


    // ----- Contexto aéreo (sheet) -----
        if (openPointContext) {
            val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { openPointContext = false },
                sheetState = infoSheetState
            ) {
                // Centro del mapa (fallback Madrid si no disponible)
                val center = pointContextCenter
                    ?: (com.spotitfly.app.ui.map.MapCameraStore.lat?.let { lat ->
                        com.spotitfly.app.ui.map.MapCameraStore.lng?.let { lng -> lat to lng }
                    } ?: (40.4168 to -3.7038))

                PointContextSheet(
                    lat = center.first,
                    lng = center.second,
                    onClose = { openPointContext = false }
                )
            }
        }
    }


@Composable
private fun MapRoute(
    onTapAvatar: () -> Unit,
    onTapChatSheet: () -> Unit,
    onTapChats: () -> Unit,
    onTapSpotsList: () -> Unit,
    onTapFavorites: () -> Unit,
    onTapSearchPill: () -> Unit,
    onSpotTapped: (Spot) -> Unit,
    centerOnSpotTick: Int,
    centerTarget: Pair<Double, Double>?,
    centerOnCoordinateTick: Int,
    centerCoordinateTarget: Pair<Double, Double>?,
    onTapCreateSpot: (Pair<Double, Double>) -> Unit,
    onTapInfo: (Pair<Double, Double>) -> Unit
) {

    Box(Modifier.fillMaxSize()) {

        // Estado de mapa (tipo + recentrado)
        val ctx = LocalContext.current
        var showMapStyleMenu by remember { mutableStateOf(false) }

        // lee el valor persistido
        val initialMapKind by remember {
            mutableStateOf(
                when (ctx.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                    .getString("map_kind", "STANDARD")) {
                    "IMAGERY" -> MapKind.IMAGERY
                    "HYBRID" -> MapKind.HYBRID
                    else -> MapKind.STANDARD
                }
            )
        }
        var mapKind by rememberSaveable { mutableStateOf(initialMapKind) }

        // guarda cada cambio
        LaunchedEffect(mapKind) {
            ctx.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("map_kind", mapKind.name)
                .apply()
        }

        var centerOnUserTick by remember { mutableStateOf(0) }
        var toggleOverlaysTick by remember { mutableStateOf(0) }
        var overlaysVisibleUi by remember { mutableStateOf(true) }   // ← NUEVO

        // 1) Mapa de fondo
        MapScreen(
            mapKind = mapKind,
            centerOnUserTick = centerOnUserTick,
            onSpotTapped = onSpotTapped,
            centerOnSpotTick = centerOnSpotTick,
            centerTarget = centerTarget,
            centerOnCoordinateTick = centerOnCoordinateTick,
            centerCoordinateTarget = centerCoordinateTarget,
            toggleOverlaysTick = toggleOverlaysTick,
            onOverlaysVisibilityChanged = { overlaysVisibleUi = it }
        )



        // 2) Tarjeta de usuario (avatar + nombre + 2 iconos chat)
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
                .zIndex(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserCard(
                name = "",
                onTapAvatar = onTapAvatar,
                onTapChatSheet = onTapChatSheet,
                onTapChats = onTapChats
            )
            Spacer(Modifier.height(10.dp))

            // 3) Barra de búsqueda tipo pill
            SearchPill(
                placeholder = "Buscar spots, lugares, coordenadas",
                onClick = onTapSearchPill

            )
            Spacer(Modifier.height(9.5.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    CircleWhite(icon = Icons.Outlined.Layers, contentDesc = "Tipo de mapa") {
                        showMapStyleMenu = true
                    }
                    DropdownMenu(expanded = showMapStyleMenu, onDismissRequest = { showMapStyleMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Estándar") },
                            onClick = {
                                mapKind = MapKind.STANDARD
                                showMapStyleMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Satélite") },
                            onClick = {
                                mapKind = MapKind.IMAGERY
                                showMapStyleMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Híbrido") },
                            onClick = {
                                mapKind = MapKind.HYBRID
                                showMapStyleMenu = false
                            }
                        )
                    }
                }
                CircleWhite(
                    icon = Icons.Filled.Favorite,
                    contentDesc = "Favoritos",
                    tint = Color(0xFFE53935)
                ) {
                    onTapFavorites()
                }

                CircleWhite(icon = Icons.Outlined.List, contentDesc = "Lista de spots") {
                    onTapSpotsList()
                }
            }
        }

        // Cruz roja en el centro (no intercepta toques)
        /*
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0.5f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Color(0xFFDB4437).copy(alpha = 0.55f),
                modifier = Modifier.size(34.dp)
            )
        }*/

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockButton(bg = Color(0xFF26C281), icon = Icons.Outlined.MyLocation, "Mi ubicación") {
                centerOnUserTick += 1
            }

            // 👉 Grupo morado + azul con 5.dp entre ellos
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockButton(bg = Color(0xFF9C27FF), icon = Icons.Outlined.Info, "Información") {
                    val lat = com.spotitfly.app.ui.map.MapCameraStore.lat ?: 40.4168
                    val lng = com.spotitfly.app.ui.map.MapCameraStore.lng ?: -3.7038
                    onTapInfo(lat to lng)
                }

                Spacer(Modifier.width(15.dp))   // ⬅️ AQUÍ defines la separación morado–azul

                DockButton(bg = Color(0xFF2196F3), icon = Icons.Outlined.Add, "Crear spot") {
                    val lat = com.spotitfly.app.ui.map.MapCameraStore.lat ?: 40.4168
                    val lng = com.spotitfly.app.ui.map.MapCameraStore.lng ?: -3.7038
                    onTapCreateSpot(lat to lng)
                }
            }

            DockButton(
                bg = if (overlaysVisibleUi) Color(0xFFFF9500) else Color(0xFFBDBDBD),
                icon = Icons.Outlined.Brush,
                contentDesc = "Brocha overlays"
            ) {
                toggleOverlaysTick += 1
            }
        }


        // 6) Wordmark SpotItFly debajo del dock (igual que en iOS)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 5.dp)      // antes 20.dp
                .zIndex(0.9f)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.spotitfly_wordmark),
                    contentDescription = "SpotItFly",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .heightIn(max = 26.dp)
                )
            }
        }

    }
}


@Composable
private fun UserCard(
    name: String,
    onTapAvatar: () -> Unit,
    onTapChatSheet: () -> Unit,
    onTapChats: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val auth = remember { FirebaseAuth.getInstance() }
            val user = auth.currentUser
            val uid = user?.uid
            // ==== Badge de comentarios ====
            var commentsBadgeCount by remember { mutableStateOf(0) }
            // ==== Badge de chats ====
            var chatsBadgeCount by remember { mutableStateOf(0) }

            DisposableEffect(uid) {
                if (uid.isNullOrBlank()) {
                    commentsBadgeCount = 0
                    onDispose { }
                } else {
                    val db = FirebaseFirestore.getInstance()
                    var spotsListener: ListenerRegistration? = null
                    var readsListener: ListenerRegistration? = null

                    var ownedSpots: Map<String, Long?> = emptyMap()
                    var spotReads: Map<String, Long> = emptyMap()

                    var spotsLoaded = false
                    var readsLoaded = false

                    fun recomputeBadgeIfReady() {
                        if (!spotsLoaded || !readsLoaded) return

                        val unreadIds = ownedSpots.mapNotNull { (spotId, lastCommentSec) ->
                            val lastComment = lastCommentSec ?: return@mapNotNull null
                            val lastSeen = spotReads[spotId]
                            if (lastSeen == null || lastComment > lastSeen) spotId else null
                        }
                        commentsBadgeCount = unreadIds.size
                    }

                    spotsListener = db.collection("spots")
                        .whereEqualTo("createdBy", uid)
                        .addSnapshotListener { snap, _ ->
                            val docs = snap?.documents ?: emptyList()
                            val map = mutableMapOf<String, Long?>()
                            for (doc in docs) {
                                val ts = doc.getTimestamp("lastCommentAt")
                                map[doc.id] = ts?.seconds
                            }
                            ownedSpots = map
                            spotsLoaded = true
                            recomputeBadgeIfReady()
                        }

                    readsListener = db.collection("users").document(uid)
                        .collection("spotReads")
                        .addSnapshotListener { snap, _ ->
                            val docs = snap?.documents ?: emptyList()
                            val map = mutableMapOf<String, Long>()
                            for (doc in docs) {
                                val ts = doc.getTimestamp("lastSeenAt")
                                if (ts != null) {
                                    map[doc.id] = ts.seconds
                                }
                            }
                            spotReads = map
                            readsLoaded = true
                            recomputeBadgeIfReady()
                        }

                    onDispose {
                        spotsListener?.remove()
                        readsListener?.remove()
                        commentsBadgeCount = 0
                    }
                }
            }

            val ctx = LocalContext.current

            // VM de chats compartido
            val chatsHomeViewModel: ChatsHomeViewModel = viewModel()

            LaunchedEffect(uid) {
                if (uid.isNullOrBlank()) {
                    chatsBadgeCount = 0
                } else {
                    chatsHomeViewModel.start()
                }
            }

            val chats by chatsHomeViewModel.chats.collectAsState()

            LaunchedEffect(uid, chats) {
                val me = uid
                if (me.isNullOrBlank()) {
                    chatsBadgeCount = 0
                } else {
                    val count = chats.count { chat ->
                        !chat.isHiddenFor(me) && chat.isUnreadFor(me)
                    }
                    chatsBadgeCount = count
                }
            }

            var profileImageUrl by remember { mutableStateOf<String?>(null) }
            var avatarBustToken by remember { mutableStateOf<String?>(null) }
            var username by remember { mutableStateOf<String?>(null) }

            // Username + foto desde Firestore
            LaunchedEffect(uid) {
                if (uid.isNullOrBlank()) {
                    username = null
                    profileImageUrl = null
                    avatarBustToken = null
                    return@LaunchedEffect
                }

                val cached = UserPrefs.loadUsername(ctx, uid)
                if (!cached.isNullOrBlank() && username != cached) {
                    username = cached
                }

                val db = FirebaseFirestore.getInstance()

                try {
                    val doc = db.collection("users").document(uid).get().await()

                    val finalDoc = if (doc.exists()) {
                        doc
                    } else {
                        val snap = db.collection("users")
                            .whereEqualTo("uid", uid)
                            .limit(1)
                            .get()
                            .await()
                        snap.documents.firstOrNull()
                    }

                    if (finalDoc != null) {
                        val data = finalDoc.data ?: emptyMap<String, Any?>()

                        val fetchedUsername =
                            (data["username"] as? String)?.trim()
                                ?: (data["displayName"] as? String)?.trim()

                        if (!fetchedUsername.isNullOrBlank() && fetchedUsername != username) {
                            username = fetchedUsername
                            UserPrefs.saveUsername(ctx, uid, fetchedUsername)
                        }

                        val fetchedProfileUrl = data["profileImageUrl"] as? String
                        if (fetchedProfileUrl != profileImageUrl) {
                            profileImageUrl = fetchedProfileUrl
                        }

                        val fetchedBustToken = data["avatarBustToken"] as? String
                        if (fetchedBustToken != avatarBustToken) {
                            avatarBustToken = fetchedBustToken
                        }
                    }
                } catch (_: Exception) {
                    // silencioso
                }
            }

            val effectiveName = (
                    username?.takeIf { it.isNotBlank() }
                        ?: user?.displayName?.takeIf { it.isNotBlank() }
                        ?: name
                    ).trim()

            val effectivePhoto = profileImageUrl.orEmpty()

            val ringBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFF00D4FF),
                    Color(0xFF7C4DFF),
                    Color(0xFF00D4FF)
                )
            )

// 🔧 Escalas para controlar grosor de aros en el avatar de la barra
            val outerRingScale = 32f / 36f   // subir hacia 1f → aro degradado más fino
            val innerContentScale = 26f / 30f // subir hacia 1f → aro blanco más fino

// Avatar con 3 círculos: degradado → blanco → contenido
            Box(
                Modifier
                    .size(46.dp)
                    .clickable { onTapAvatar() },
                contentAlignment = Alignment.Center
            ) {
                // Círculo exterior degradado
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(brush = ringBrush)
                )

                // Anillo blanco
                Box(
                    modifier = Modifier
                        .fillMaxSize(outerRingScale)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    // Círculo interior con foto / iniciales
                    Box(
                        modifier = Modifier
                            .fillMaxSize(innerContentScale)
                            .clip(CircleShape)
                            .background(Color(0xFFECECEC)),
                        contentAlignment = Alignment.Center
                    ) {
                        val local = uid?.let { avatarLocalFile(ctx, it) }
                        val hasLocal = local?.exists() == true

                        val effUrl = effectivePhoto.let { url ->
                            if (!avatarBustToken.isNullOrBlank() && url.isNotBlank()) {
                                "$url?bust=$avatarBustToken"
                            } else {
                                url
                            }
                        }

                        when {
                            // 1️⃣ Fichero local
                            hasLocal -> {
                                val bitmap = remember(local?.path) {
                                    try {
                                        BitmapFactory.decodeFile(local?.path ?: "")
                                            ?.asImageBitmap()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }

                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (effUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = effUrl,
                                        contentDescription = "Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        onSuccess = { success: AsyncImagePainter.State.Success ->
                                            if (local != null) {
                                                saveDrawableToFile(success.result.drawable, local)
                                            }
                                        }
                                    )
                                } else {
                                    Text(
                                        text = nameInitials(effectiveName),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2️⃣ Solo URL remota
                            effUrl.isNotBlank() -> {
                                AsyncImage(
                                    model = effUrl,
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    onSuccess = { success: AsyncImagePainter.State.Success ->
                                        if (local != null) {
                                            saveDrawableToFile(success.result.drawable, local)
                                        }
                                    }
                                )
                            }

                            // 3️⃣ Sin nada → iniciales
                            else -> {
                                Text(
                                    text = nameInitials(effectiveName),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }


            Spacer(Modifier.width(12.dp))

            Text(
                text = effectiveName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTapAvatar() },
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Box(
                modifier = Modifier.wrapContentSize()
            ) {
                IconButton(onClick = onTapChatSheet) {
                    Icon(
                        Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Abrir comentarios nuevos",
                        tint = Color(0xFF1E88E5)
                    )
                }
                if (commentsBadgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color(0xFFD32F2F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = commentsBadgeCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.wrapContentSize()
            ) {
                IconButton(onClick = onTapChats) {
                    Icon(
                        Icons.Outlined.Forum,
                        contentDescription = "Abrir chats",
                        tint = Color(0xFF1E88E5)
                    )
                }
                if (chatsBadgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(Color(0xFFD32F2F), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chatsBadgeCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun SearchPill(placeholder: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable { onClick() }
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.List, contentDescription = null, tint = Color(0xFF9E9E9E))
            Spacer(Modifier.width(10.dp))
            Text(
                placeholder,
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CircleWhite(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    tint: Color = Color(0xFF1E88E5),
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(54.dp)
            .shadow(8.dp, CircleShape, clip = true)
            .clip(CircleShape)
            .background(Color.White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun DockButton(
    bg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(54.dp)
            .shadow(10.dp, CircleShape, clip = true)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

private fun nameInitials(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val first = parts.getOrNull(0)?.firstOrNull()?.uppercase() ?: ""
    val second = parts.getOrNull(1)?.firstOrNull()?.uppercase() ?: ""
    val initials = (first + second)
    return if (initials.isNotBlank()) initials else "?"
}

@Composable
private fun CommunityGuidelinesScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header con botón atrás + título (similar a una navigation bar)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                Text("Atrás", color = Color(0xFF007AFF))
            }

            Spacer(Modifier.width(8.dp))
            Text(
                text = "Normas de la comunidad",
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Normas de la comunidad",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = """
Estas normas te ayudan a usar SpotItFly con respeto y seguridad. Al usar la app, aceptas cumplirlas.

NO PERMITIDO
• Spam o promoción engañosa  
• Insultos, acoso, amenazas o incitación al odio  
• Contenido sexualmente explícito o violento  
• Información falsa que pueda causar daño (ubicación errónea, accesos peligrosos, etc.)  
• Revelar datos personales de terceros sin permiso  
• Publicar material con derechos de autor sin autorización

SPOTS Y DESCRIPCIONES
• Verifica la ubicación, accesos y restricciones antes de publicar  
• Indica riesgos y limitaciones cuando existan  
• No subas imágenes de otras personas sin su consentimiento

COMENTARIOS Y CHAT
• Debate con respeto, sin ataques personales  
• Reporta contenido inadecuado: mantén la comunidad limpia

SEGURIDAD
• Respeta la normativa local (zonas restringidas, NOTAM, parques, etc.)  
• Evita poner en peligro a personas, fauna o patrimonio

MODERACIÓN
Los reportes pueden derivar en edición o eliminación de contenido y/o suspensión de cuentas.
""".trimIndent(),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "¿Ves algo que incumple estas normas? Usa el botón \"Reportar\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun UnreadCommentsScreen(
    onBack: () -> Unit,
    onOpenSpot: (Spot) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    val uid = user?.uid

    // Spots propios del usuario
    var ownSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }
    // Ids de spots con comentarios nuevos
    var unreadIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 1) Cargar spots creados por el usuario
    LaunchedEffect(uid) {
        if (uid.isNullOrBlank()) {
            ownSpots = emptyList()
            unreadIds = emptySet()
        } else {
            val db = FirebaseFirestore.getInstance()
            val querySnap = db.collection("spots")
                .whereEqualTo("createdBy", uid)
                .get()
                .await()

            val spots = querySnap.documents.mapNotNull { doc ->
                SpotMapper.from(doc)
            }
            ownSpots = spots
        }
    }

    // 2) Calcular qué spots tienen comentarios sin leer usando CommentReadService
    LaunchedEffect(uid, ownSpots) {
        if (uid.isNullOrBlank() || ownSpots.isEmpty()) {
            unreadIds = emptySet()
        } else {
            val service = CommentReadService()
            unreadIds = service.getUnreadSpotIds(uid, ownSpots)
        }
    }

    // 3) Filtrar spots con comentarios sin leer
    val unreadSpots = remember(ownSpots, unreadIds) {
        ownSpots.filter { unreadIds.contains(it.id) }
    }

    // 4) Distancia desde el centro actual del mapa
    val centerLat = com.spotitfly.app.ui.map.MapCameraStore.lat
    val centerLng = com.spotitfly.app.ui.map.MapCameraStore.lng

    val itemsWithDistance = remember(unreadSpots, centerLat, centerLng) {
        val center = if (centerLat != null && centerLng != null) {
            centerLat to centerLng
        } else {
            null
        }

        unreadSpots.map { spot ->
            val dist = center?.let { (clat, clng) ->
                distanceMeters(clat, clng, spot.latitude, spot.longitude)
            }
            SpotWithDistance(spot = spot, distanceMeters = dist)
        }.sortedBy { it.distanceMeters ?: Double.POSITIVE_INFINITY }
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            TextButton(onClick = onBack) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = Color(0xFF007AFF)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Atrás",
                        color = Color(0xFF007AFF)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Nuevos comentarios",
                style = MaterialTheme.typography.titleLarge
            )
        }


        Spacer(Modifier.height(8.dp))

        when {
            uid.isNullOrBlank() -> {
                Text(
                    text = "Inicia sesión para ver los comentarios.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            itemsWithDistance.isEmpty() -> {
                Text(
                    text = "No tienes comentarios nuevos en tus spots.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(itemsWithDistance, key = { it.spot.id }) { item ->
                        SpotRow(
                            spot = item.spot,
                            distanceMeters = item.distanceMeters,
                            onClick = { s ->
                                if (!uid.isNullOrBlank()) {
                                    scope.launch {
                                        runCatching {
                                            CommentReadService().markCommentsSeen(uid, s.id)
                                        }
                                    }
                                }
                                onOpenSpot(s)
                            },
                            onViewMap = { s ->
                                onOpenSpot(s)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) *
            cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}
