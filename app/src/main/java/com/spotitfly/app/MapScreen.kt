package com.spotitfly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.ui.map.MapCameraStore
import com.spotitfly.app.ui.map.SpotsMapLayer
import com.spotitfly.app.ui.map.AirspaceOverlaysLayer
import com.spotitfly.app.data.context.overlays.AirspaceOverlayStore
import com.spotitfly.app.data.context.overlays.prefs.OverlayPrefs
import com.spotitfly.app.data.context.overlays.prefs.OverlayToggles
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.auth.AuthViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import android.graphics.Point
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.outlined.Add



@Composable
fun MapScreen(
    mapKind: MapKind,
    centerOnUserTick: Int,
    onSpotTapped: (Spot) -> Unit,
    centerOnSpotTick: Int = 0,
    centerTarget: Pair<Double, Double>? = null,
    centerOnCoordinateTick: Int = 0,
    centerCoordinateTarget: Pair<Double, Double>? = null,
    toggleOverlaysTick: Int = 0,
    onOverlaysVisibilityChanged: (Boolean) -> Unit = {}
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    // --- Zooms 1:1 con iOS (≈ 2000 m) ---
    val cityZoom = 15f

    // ViewModel de auth para registrar notificaciones (usa FirebaseAuth por dentro)
    val authVm: AuthViewModel = viewModel()

    // Solo queremos pedir notificaciones una vez
    var notifAsked by remember { mutableStateOf(false) }

    // Launcher para permiso de notificaciones (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Aquí ya sabemos si el usuario ha aceptado o no las notis
        authVm.handleNotificationsAfterLogin(granted)
    }


    // Toggles persistentes (como en iOS: Restricciones ON; resto OFF)
    val overlayPrefs = remember { OverlayPrefs(context) }
    val overlayToggles by overlayPrefs.flow.collectAsState(initial = OverlayToggles())

    // MapView lifecycle
    val mapView = remember {
        MapView(context, GoogleMapOptions().liteMode(false)).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: LifecycleOwner) { mapView.onPause() }
            override fun onDestroy(owner: LifecycleOwner) { mapView.onDestroy() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mapRef = remember { mutableStateOf<GoogleMap?>(null) }



    val scope = rememberCoroutineScope()
    val overlayStore = remember { AirspaceOverlayStore(context) }
    var overlayJob by remember { mutableStateOf<Job?>(null) }

    // Tap en pines: diferenciar tap simple (detalle) vs doble tap (centrar)
    var lastTapSpotId by remember { mutableStateOf<String?>(null) }
    var lastTapTimeMs by remember { mutableStateOf(0L) }
    var singleTapJob by remember { mutableStateOf<Job?>(null) }


    val overlaysLayerRef = remember { mutableStateOf<AirspaceOverlaysLayer?>(null) }
    var overlaysVisible by remember { mutableStateOf(true) }


    // Cortina inicial para no enseñar África ni zoom-mundo
    var showInitialCover by remember { mutableStateOf(true) }

    // Notificar estado inicial al anfitrión (AppRoot)
    LaunchedEffect(Unit) {
        onOverlaysVisibilityChanged(overlaysVisible)
    }

    // Permission launcher for location (solo centra si NO se centró nunca)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val map = mapRef.value ?: return@rememberLauncherForActivityResult
        if (granted && !MapCameraStore.initialCentered && !MapCameraStore.hasSaved()) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation.addOnSuccessListener { loc ->
                map.isMyLocationEnabled = true
                if (loc != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.latitude, loc.longitude), cityZoom
                        )
                    )
                    MapCameraStore.initialCentered = true
                }
            }
        }

        // 🔔 JUSTO DESPUÉS DEL POPUP DE UBICACIÓN → gestionamos notificaciones
        if (!notifAsked) {
            notifAsked = true

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (notifGranted) {
                    // Ya tenía permiso → inicializamos notificaciones y registro de device
                    authVm.handleNotificationsAfterLogin(true)
                } else {
                    // Lanzamos el popup de notificaciones
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // < Android 13 → no hay popup; asumimos permiso y registramos
                authVm.handleNotificationsAfterLogin(true)
            }
        }
    }


    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.getMapAsync { map ->
                    mapRef.value = map

                    // Gestos y settings
                    map.uiSettings.isZoomControlsEnabled = false
                    map.uiSettings.isCompassEnabled = true
                    map.uiSettings.isMyLocationButtonEnabled = false

                    // Tap en marcador → abrir detalle
                    map.setOnMarkerClickListener { marker ->
                        val spot = marker.tag as? Spot
                        if (spot != null) {
                            onSpotTapped(spot)
                            true
                        } else {
                            false
                        }
                    }

                    // Capa de Spots
                    val spotsLayer = SpotsMapLayer(context, map)
                    spotsLayer.bustIconLayout()
                    spotsLayer.renderCachedOnce()
                    spotsLayer.loadAll()

                    // Capa de Overlays Aéreos
                    val overlaysLayer = AirspaceOverlaysLayer(context, map)
                    overlaysLayerRef.value = overlaysLayer

                    // Primer pintado forzado al cargar el mapa:
// - reescala pins
// - si ya estamos a zoom de ciudad, dispara un primer fetch de overlays sin esperar al debounce
                    map.setOnMapLoadedCallback {
                        val b = map.projection?.visibleRegion?.latLngBounds
                        if (b != null) {
                            spotsLayer.rescale(b)

                            if (overlaysVisible) {
                                val zoom = map.cameraPosition.zoom
                                if (zoom >= 12f) {
                                    // Carga inicial rápida de overlays
                                    scope.launch {
                                        val renderables = overlayStore.load(b, zoom, overlayToggles)
                                        overlaysLayer.render(renderables)
                                        overlaysLayer.rescale(zoom)
                                    }
                                }
                            }
                        }
                    }


                    // Guardar cámara, refrescar con debounce, reescalar y levantar cortina cuando el zoom ya es razonable
                    map.setOnCameraIdleListener {
                        val cam = map.cameraPosition
                        MapCameraStore.save(cam.target.latitude, cam.target.longitude, cam.zoom)

                        // 👇 solo quitamos la cortina cuando el mapa ya no está en zoom-mundo
                        if (showInitialCover && cam.zoom >= 5f) {
                            showInitialCover = false
                        }

                        val b = map.projection?.visibleRegion?.latLngBounds
                        if (b != null) {
                            spotsLayer.refresh(b)

                            overlayJob?.cancel()
                            overlayJob = scope.launch {
                                delay(120)
                                if (overlaysVisible) {
                                    val zoom = map.cameraPosition.zoom
                                    if (zoom < 12f) {
                                        overlaysLayer.rescale(zoom)
                                    } else {
                                        val renderables = overlayStore.load(b, zoom, overlayToggles)
                                        overlaysLayer.render(renderables)
                                        overlaysLayer.rescale(zoom)
                                    }
                                } else {
                                    overlaysLayer.clearAll()
                                }
                            }
                        }
                    }

                    // Tipo de mapa
                    map.mapType = when (mapKind) {
                        MapKind.STANDARD -> GoogleMap.MAP_TYPE_NORMAL
                        MapKind.IMAGERY -> GoogleMap.MAP_TYPE_SATELLITE
                        MapKind.HYBRID -> GoogleMap.MAP_TYPE_HYBRID
                    }

                    // Cámara inicial (restaura si había; si no, centra y pinta).
// OJO: aquí ya NO levantamos la cortina; solo movemos cámara.
                    if (MapCameraStore.hasSaved()) {
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(MapCameraStore.lat!!, MapCameraStore.lng!!),
                                MapCameraStore.zoom!!
                            )
                        )
                        val savedBounds = map.projection?.visibleRegion?.latLngBounds
                        if (savedBounds != null) {
                            // Solo recargamos spots; los overlays se gestionan en onCameraIdle con debounce
                            spotsLayer.refresh(savedBounds)
                        }
                    } else if (!MapCameraStore.initialCentered) {
                        val hasFine = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasFine) {
                            val fused = LocationServices.getFusedLocationProviderClient(context)
                            fused.lastLocation
                                .addOnSuccessListener { loc ->
                                    map.isMyLocationEnabled = true
                                    if (loc != null) {
                                        map.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                LatLng(loc.latitude, loc.longitude),
                                                cityZoom
                                            )
                                        )
                                    } else {
                                        val madrid = LatLng(40.4168, -3.7038)
                                        map.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                madrid,
                                                cityZoom
                                            )
                                        )
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(madrid)
                                                .title("Madrid")
                                        )
                                    }
                                    MapCameraStore.initialCentered = true

                                    val initBounds = map.projection?.visibleRegion?.latLngBounds
                                    if (initBounds != null) {
                                        // Igual que arriba: solo spots aquí; overlays siempre via onCameraIdle
                                        spotsLayer.refresh(initBounds)
                                    }
                                }
                                .addOnFailureListener {
                                    val madrid = LatLng(40.4168, -3.7038)
                                    map.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            madrid,
                                            cityZoom
                                        )
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(madrid)
                                            .title("Madrid")
                                    )
                                    MapCameraStore.initialCentered = true

                                    val initBounds = map.projection?.visibleRegion?.latLngBounds
                                    if (initBounds != null) {
                                        // Solo spots, overlays via onCameraIdle
                                        spotsLayer.refresh(initBounds)
                                    }
                                }
                        } else {
                            val madrid = LatLng(40.4168, -3.7038)
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    madrid,
                                    cityZoom
                                )
                            )
                            map.addMarker(
                                MarkerOptions()
                                    .position(madrid)
                                    .title("Madrid")
                            )
                            MapCameraStore.initialCentered = true

                            val initBounds = map.projection?.visibleRegion?.latLngBounds
                            if (initBounds != null) {
                                // Solo spots, overlays via onCameraIdle
                                spotsLayer.refresh(initBounds)
                            }
                        }
                    }

                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            update = { }
        )

        // Cruz roja en el centro (no intercepta toques)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Color(0xFFDB4437).copy(alpha = 0.55f),
                modifier = Modifier.size(34.dp)
            )
        }

        // Tapamos el mapa mientras siga en la cámara inicial por defecto ("África")
        if (showInitialCover) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Cargando mapa…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

    }

    // Cambiar tipo de mapa al vuelo (no toca cámara)
    LaunchedEffect(mapRef.value, mapKind) {
        val map = mapRef.value ?: return@LaunchedEffect
        map.mapType = when (mapKind) {
            MapKind.STANDARD -> GoogleMap.MAP_TYPE_NORMAL
            MapKind.IMAGERY -> GoogleMap.MAP_TYPE_SATELLITE
            MapKind.HYBRID -> GoogleMap.MAP_TYPE_HYBRID
        }
    }

    // Recentrar a usuario al pulsar el botón (tick)
    LaunchedEffect(centerOnUserTick) {
        if (centerOnUserTick == 0) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect

        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    map.isMyLocationEnabled = true
                    if (loc != null) {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude), cityZoom
                            )
                        )
                    } else {
                        val madrid = LatLng(40.4168, -3.7038)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            madrid,
                            cityZoom))
                    }
                }
                .addOnFailureListener {
                    val madrid = LatLng(40.4168, -3.7038)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                        madrid,
                        cityZoom))
                }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            val madrid = LatLng(40.4168, -3.7038)
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(madrid, 12f))
        }
    }

    // Reaplicar overlays cuando cambien los toggles
    LaunchedEffect(overlayToggles, mapRef.value) {
        val map = mapRef.value ?: return@LaunchedEffect
        val layer = overlaysLayerRef.value ?: return@LaunchedEffect
        val b = map.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect

        overlayJob?.cancel()
        overlayJob = scope.launch {
            delay(200)
            if (overlaysVisible) {
                val renderables = overlayStore.load(b, map.cameraPosition.zoom, overlayToggles)
                layer.render(renderables)
                layer.rescale(map.cameraPosition.zoom)
            } else {
                layer.clearAll()
            }
        }
    }

    // Toggle global de overlays
    LaunchedEffect(toggleOverlaysTick) {
        if (toggleOverlaysTick == 0) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val layer = overlaysLayerRef.value ?: return@LaunchedEffect
        overlaysVisible = !overlaysVisible
        onOverlaysVisibilityChanged(overlaysVisible)
        overlayJob?.cancel()
        if (!overlaysVisible) {
            layer.clearAll()
        } else {
            val b = map.projection?.visibleRegion?.latLngBounds ?: return@LaunchedEffect
            overlayJob = scope.launch {
                delay(100)
                val zoom = map.cameraPosition.zoom
                val renderables = overlayStore.load(b, zoom, overlayToggles)
                layer.render(renderables)
                layer.rescale(zoom)
            }
        }
    }

    // Recentrar sin offset (resultados de búsqueda genéricos)
    LaunchedEffect(centerOnCoordinateTick) {
        if (centerOnCoordinateTick == 0) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val target = centerCoordinateTarget ?: return@LaunchedEffect
        val (lat, lon) = target

        val zoom = 14.5f
        val targetLatLng = LatLng(lat, lon)

        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                targetLatLng,
                zoom
            )
        )

        // Guardamos la cámara sin aplicar offset
        MapCameraStore.save(
            lat,
            lon,
            zoom
        )
    }


    // Recentrar a un spot concreto (desde lista o detalle) con offset vertical de ~60 dp
    LaunchedEffect(centerOnSpotTick) {
        if (centerOnSpotTick == 0) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val target = centerTarget ?: return@LaunchedEffect
        val (lat, lon) = target

        val zoom = 14.5f
        val targetLatLng = LatLng(lat, lon)

        // 1) Primero centramos directamente en el spot con el zoom deseado
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                targetLatLng,
                zoom
            )
        )

        // 2) Ahora aplicamos el offset vertical de 60 dp
        val projection = map.projection
        val screenPoint = projection?.toScreenLocation(targetLatLng)

        if (screenPoint != null) {
            val offsetPx = with(density) { -60.dp.toPx() }   // 60 dp hacia abajo
            val shiftedPoint = Point(
                screenPoint.x,
                (screenPoint.y + offsetPx).toInt()
            )
            val shiftedLatLng = projection.fromScreenLocation(shiftedPoint)

            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    shiftedLatLng,
                    zoom
                )
            )

            // Guardamos la posición real de la cámara (la nueva centrada con offset)
            MapCameraStore.save(
                shiftedLatLng.latitude,
                shiftedLatLng.longitude,
                zoom
            )
        } else {
            // Fallback: centrado normal sin offset si projection aún no está listo
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    targetLatLng,
                    zoom
                )
            )
            MapCameraStore.save(lat, lon, zoom)
        }
    }

}


