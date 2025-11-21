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

@Composable
fun MapScreen(
    mapKind: MapKind,
    centerOnUserTick: Int,
    onSpotTapped: (Spot) -> Unit,
    centerOnSpotTick: Int = 0,
    centerTarget: Pair<Double, Double>? = null,
    toggleOverlaysTick: Int = 0,
    onOverlaysVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
                            LatLng(loc.latitude, loc.longitude), 14.5f
                        )
                    )
                    MapCameraStore.initialCentered = true
                }
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

                    // Primer pintado forzado al cargar el mapa (solo reescala pins)
                    map.setOnMapLoadedCallback {
                        val b = map.projection?.visibleRegion?.latLngBounds
                        if (b != null) {
                            spotsLayer.rescale(b)
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
                                delay(350)
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
                            spotsLayer.refresh(savedBounds)
                            overlayJob?.cancel()
                            overlayJob = scope.launch {
                                delay(350)
                                if (overlaysVisible) {
                                    val zoom = map.cameraPosition.zoom
                                    if (zoom < 12f) {
                                        overlaysLayer.rescale(zoom)
                                    } else {
                                        val renderables =
                                            overlayStore.load(savedBounds, zoom, overlayToggles)
                                        overlaysLayer.render(renderables)
                                        overlaysLayer.rescale(zoom)
                                    }
                                } else {
                                    overlaysLayer.clearAll()
                                }
                            }
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
                                                14.5f
                                            )
                                        )
                                    } else {
                                        val madrid = LatLng(40.4168, -3.7038)
                                        map.moveCamera(
                                            CameraUpdateFactory.newLatLngZoom(
                                                madrid,
                                                12f
                                            )
                                        )
                                        map.addMarker(
                                            MarkerOptions()
                                                .position(madrid)
                                                .title("Madrid")
                                        )
                                    }
                                    MapCameraStore.initialCentered = true

                                    val initBounds =
                                        map.projection?.visibleRegion?.latLngBounds
                                    if (initBounds != null) {
                                        spotsLayer.refresh(initBounds)
                                        overlayJob?.cancel()
                                        overlayJob = scope.launch {
                                            delay(350)
                                            val renderables = overlayStore.load(
                                                initBounds,
                                                map.cameraPosition.zoom,
                                                overlayToggles
                                            )
                                            overlaysLayer.render(renderables)
                                            overlaysLayer.rescale(map.cameraPosition.zoom)
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    val madrid = LatLng(40.4168, -3.7038)
                                    map.moveCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            madrid,
                                            12f
                                        )
                                    )
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(madrid)
                                            .title("Madrid")
                                    )
                                    MapCameraStore.initialCentered = true

                                    val initBounds =
                                        map.projection?.visibleRegion?.latLngBounds
                                    if (initBounds != null) {
                                        spotsLayer.refresh(initBounds)
                                        overlayJob?.cancel()
                                        overlayJob = scope.launch {
                                            delay(350)
                                            if (overlaysVisible) {
                                                val zoom = map.cameraPosition.zoom
                                                if (zoom < 12f) {
                                                    overlaysLayer.rescale(zoom)
                                                } else {
                                                    val renderables = overlayStore.load(
                                                        initBounds,
                                                        zoom,
                                                        overlayToggles
                                                    )
                                                    overlaysLayer.render(renderables)
                                                    overlaysLayer.rescale(zoom)
                                                }
                                            } else {
                                                overlaysLayer.clearAll()
                                            }
                                        }
                                    }
                                }
                        } else {
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            val madrid = LatLng(40.4168, -3.7038)
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    madrid,
                                    12f
                                )
                            )
                            map.addMarker(
                                MarkerOptions()
                                    .position(madrid)
                                    .title("Madrid")
                            )
                            MapCameraStore.initialCentered = true

                            val initBounds =
                                map.projection?.visibleRegion?.latLngBounds
                            if (initBounds != null) {
                                spotsLayer.refresh(initBounds)
                                overlayJob?.cancel()
                                overlayJob = scope.launch {
                                    delay(350)
                                    val renderables = overlayStore.load(
                                        initBounds,
                                        map.cameraPosition.zoom,
                                        overlayToggles
                                    )
                                    overlaysLayer.render(renderables)
                                    overlaysLayer.rescale(map.cameraPosition.zoom)
                                }
                            }
                        }
                    }
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            update = { }
        )

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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
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
                                LatLng(loc.latitude, loc.longitude), 14.5f
                            )
                        )
                    } else {
                        val madrid = LatLng(40.4168, -3.7038)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(madrid, 12f))
                    }
                }
                .addOnFailureListener {
                    val madrid = LatLng(40.4168, -3.7038)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(madrid, 12f))
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

    // Recentrar a un spot concreto (desde lista o detalle)
    LaunchedEffect(centerOnSpotTick) {
        if (centerOnSpotTick == 0) return@LaunchedEffect
        val map = mapRef.value ?: return@LaunchedEffect
        val target = centerTarget ?: return@LaunchedEffect
        val (lat, lon) = target
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lon), 15f
            )
        )
        MapCameraStore.save(lat, lon, 15f)
    }
}
