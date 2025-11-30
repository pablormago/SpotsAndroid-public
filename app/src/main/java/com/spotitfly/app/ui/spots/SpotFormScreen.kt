package com.spotitfly.app.ui.spots

import android.content.Context
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.data.model.SpotCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.clickable
import com.spotitfly.app.ui.design.SpotDetailTokens as T

data class SpotInput(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val category: SpotCategory,
    val acceso: String?,
    val locality: String?,
    val bestDate: String?,
    val imageUri: Uri?,
    val myRating: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotFormScreen(
    existing: Spot? = null,
    initialLat: Double? = null,
    initialLng: Double? = null,
    onBack: () -> Unit,
    onSave: (SpotInput) -> Unit
) {
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val corner = 12.dp
    val fieldHeight = 52.dp
    val sectionGap = 14.dp

    val uid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }

    var latitudeText by remember {
        mutableStateOf((existing?.latitude ?: initialLat)?.toString().orEmpty())
    }
    var longitudeText by remember {
        mutableStateOf((existing?.longitude ?: initialLng)?.toString().orEmpty())
    }

    var category by remember { mutableStateOf(existing?.category ?: SpotCategory.OTROS) }
    var acceso by remember { mutableStateOf(existing?.acceso ?: "") }
    var locality by remember { mutableStateOf(existing?.locality ?: "") }
    var bestDate by remember { mutableStateOf(existing?.bestDate ?: "") }

    var myRating by remember(existing?.id, uid) {
        mutableStateOf(existing?.ratings?.get(uid) ?: 0)
    }

    val initialImageUrl = existing?.imageUrl
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
    }

    LaunchedEffect(latitudeText, longitudeText) {
        val lat = latitudeText.toDoubleOrNull()
        val lon = longitudeText.toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            delay(250)
            val loc = reverseGeocodeLocality(ctx, lat, lon)
            if (!loc.isNullOrBlank()) locality = loc
        }
    }

    fun performSave() {
        val lat = latitudeText.toDoubleOrNull()
        val lon = longitudeText.toDoubleOrNull()
        when {
            name.isBlank() -> { scope.launch { snack.showSnackbar("El nombre es obligatorio") } }
            lat == null || lat !in -90.0..90.0 -> { scope.launch { snack.showSnackbar("Latitud inválida") } }
            lon == null || lon !in -180.0..180.0 -> { scope.launch { snack.showSnackbar("Longitud inválida") } }
            else -> {
                onSave(
                    SpotInput(
                        name = name.trim(),
                        description = description.trim(),
                        latitude = lat,
                        longitude = lon,
                        category = category,
                        acceso = acceso.ifBlank { null },
                        locality = locality.ifBlank { null },
                        bestDate = bestDate.ifBlank { null },
                        imageUri = imageUri,
                        myRating = myRating.takeIf { it in 1..5 }
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Nuevo Spot" else "Editar Spot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color(0xFF007AFF) // Action Blue iOS
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { performSave() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF007AFF) // Action Blue iOS
                        )
                    ) {
                        Text(
                            text = if (existing == null) "Crear" else "Guardar",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { inner ->

    Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(inner)
                .imePadding()
                .navigationBarsPadding()
                .pointerInput(Unit) { detectTapGestures(onTap = { focus.clearFocus() }) }
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(sectionGap)
        ) {
            // 1) Imagen (preview 88×88 + botones)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(88.dp)
                ) {
                    val preview = imageUri
                    if (preview != null) {
                        AsyncImage(
                            model = preview,
                            contentDescription = "Foto seleccionada",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!initialImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = initialImageUrl,
                            contentDescription = "Imagen actual",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { _: AsyncImagePainter.State.Success -> }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Photo, contentDescription = null, tint = Color(0xFF9E9E9E))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pickImage.launch("image/*") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF007AFF), // fondo azul iOS
                            contentColor = Color.White          // texto en blanco
                        )
                    ) {
                        Text(if (imageUri == null) "Seleccionar foto" else "Cambiar foto")
                    }
                    if (imageUri != null) {
                        OutlinedButton(
                            onClick = { imageUri = null },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF007AFF) // borde/texto azul iOS
                            )
                        ) {
                            Text("Quitar")
                        }
                    }
                }

            }

            // 2) Nombre
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                shape = RoundedCornerShape(corner),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = fieldHeight)
            )

            // 3) Descripción
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descripción") },
                shape = RoundedCornerShape(corner),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 6
            )

            // 4) Categoría
            var catExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = !catExpanded }) {
                OutlinedTextField(
                    value = categoryLabel(category),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    shape = RoundedCornerShape(corner),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .heightIn(min = fieldHeight)
                )
                ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    SpotCategory.values().forEach { c ->
                        DropdownMenuItem(
                            text = { Text(categoryLabel(c)) },
                            onClick = { category = c; catExpanded = false }
                        )
                    }
                }
            }

            // 5) Tu voto (x3 tamaño)
            Column {
                Text("Tu voto", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                StarsRow(value = myRating, onSelect = { v -> myRating = v }, size = 48.dp)
            }

            // 6) Mejor fecha/época
            OutlinedTextField(
                value = bestDate,
                onValueChange = { bestDate = it },
                label = { Text("Mejor fecha/época") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                shape = RoundedCornerShape(corner),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = fieldHeight)
            )

            // 7) Acceso
            OutlinedTextField(
                value = acceso,
                onValueChange = { acceso = it },
                label = { Text("Acceso") },
                shape = RoundedCornerShape(corner),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                maxLines = 5
            )

            // 8) Latitud / Longitud
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = latitudeText,
                    onValueChange = { latitudeText = it },
                    label = { Text("Latitud") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(corner),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = fieldHeight)
                )
                OutlinedTextField(
                    value = longitudeText,
                    onValueChange = { longitudeText = it },
                    label = { Text("Longitud") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(corner),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = fieldHeight)
                )
            }

            // 9) Localidad (autocompletada, NO editable)
            Surface(
                shape = RoundedCornerShape(corner),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Localidad", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (locality.isBlank()) "—" else locality,
                        style = MaterialTheme.typography.bodyMedium,
                        color = T.textPrimary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StarsRow(value: Int, onSelect: (Int) -> Unit, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val idx = i + 1
            val on = idx <= value
            Text(
                text = if (on) "★" else "☆",
                color = if (on) T.starOn else T.starOff,
                fontSize = size.value.sp,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .clickable { onSelect(idx) }
            )
        }
    }
}

private fun categoryLabel(cat: SpotCategory): String = when (cat) {
    SpotCategory.FREESTYLE_CAMPO_ABIERTO -> "Freestyle campo abierto"
    SpotCategory.FREESTYLE_BANDO -> "Freestyle Bando"
    SpotCategory.CINEMATICO -> "Cinemático"
    SpotCategory.RACING -> "Racing"
    SpotCategory.OTROS -> "Otros"
}

private suspend fun reverseGeocodeLocality(
    ctx: Context,
    lat: Double,
    lon: Double
): String? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(ctx, Locale("es", "ES"))
        val list = geocoder.getFromLocation(lat, lon, 1)
        val a = list?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea
    } catch (_: Exception) {
        null
    }
}
