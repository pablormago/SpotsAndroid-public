package com.spotitfly.app.ui.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.data.SpotsRepository
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import java.util.Locale
import java.text.Normalizer
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Check
import com.spotitfly.app.data.model.SpotCategory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Add
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.spotitfly.app.data.chat.ChatsLocalCache
import com.spotitfly.app.avatarLocalFile
import com.spotitfly.app.saveDrawableToFile
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import android.widget.Toast


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsListScreen(
    onBack: () -> Unit,
    onOpenSpot: (com.spotitfly.app.data.model.Spot) -> Unit,
    onViewMap: (com.spotitfly.app.data.model.Spot) -> Unit,
    onAddSpot: () -> Unit,
    listState: LazyListState
) {

    val context = LocalContext.current
    val vm: SpotsListViewModel = viewModel(factory = SpotsListViewModelFactory(SpotsRepository()))
    LaunchedEffect(Unit) { vm.start(context) }
    val items by vm.items.collectAsState()

    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var shareSpot by remember { mutableStateOf<com.spotitfly.app.data.model.Spot?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showChatPicker by remember { mutableStateOf(false) }

    // Cache en memoria: spotId -> provincia resuelta por coordenadas
    var provincesBySpotId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }


    LaunchedEffect(items) {
        if (items.isEmpty()) return@LaunchedEffect

        val geocoder = Geocoder(context, Locale("es", "ES"))
        val current = provincesBySpotId.toMutableMap()

        for (item in items) {
            val spot = item.spot

            // Ya resuelta antes
            if (current.containsKey(spot.id)) continue

            val province = resolveProvinceForSpot(
                geocoder = geocoder,
                latitude = spot.latitude,
                longitude = spot.longitude
            )

            if (province != null) {
                current[spot.id] = province
            }
        }

        provincesBySpotId = current
    }

    // 🔍 Texto del buscador
    var searchText by remember { mutableStateOf("") }

    // 🎯 Filtro por categoría
    var selectedCategory by remember { mutableStateOf<SpotCategory?>(null) }
    var isCategoryMenuExpanded by remember { mutableStateOf(false) }

    // 🗺️ Filtro por provincia (España)
    var selectedProvince by remember { mutableStateOf<String?>(null) }
    var isProvinceMenuExpanded by remember { mutableStateOf(false) }

    // Provincias disponibles según los spots cargados (por id -> provincia)
    val provinceOptions by remember(items, provincesBySpotId) {
        derivedStateOf {
            items.mapNotNull { item ->
                provincesBySpotId[item.spot.id]
            }
                .distinct()
                .sorted()
        }
    }

    // 🔍 Lista filtrada según texto + categoría + provincia
    val filteredItems by remember(
        items,
        searchText,
        selectedCategory,
        selectedProvince,
        provincesBySpotId
    ) {
        derivedStateOf {
            val query = normalizeSearchText(searchText.trim())

            items.filter { item ->
                val spot = item.spot

                // 1) Filtro por categoría
                if (selectedCategory != null && spot.category != selectedCategory) {
                    return@filter false
                }

                // 2) Filtro por provincia (usando la cache por spotId)
                val spotProvince = provincesBySpotId[spot.id]
                if (selectedProvince != null && spotProvince != selectedProvince) {
                    return@filter false
                }

                // 3) Si no hay texto, sólo aplicamos categoría + provincia
                if (query.isEmpty()) {
                    return@filter true
                }

                // 4) Búsqueda sin acentos / case-insensitive (igual que antes)
                val name = normalizeSearchText(spot.name.orEmpty())
                val description = normalizeSearchText(spot.description.orEmpty())
                val localityText = normalizeSearchText(spot.locality.orEmpty())
                val categoryText = normalizeSearchText(
                    spot.category.name.replace('_', ' ')
                )

                name.contains(query) ||
                        description.contains(query) ||
                        localityText.contains(query) ||
                        categoryText.contains(query)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
            TopBarList(
                title = "Spots cercanos",
                onBack = onBack,
                onAddSpot = onAddSpot,
                isCategoryMenuExpanded = isCategoryMenuExpanded,
                onCategoryMenuExpandedChange = { isCategoryMenuExpanded = it },
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                isProvinceMenuExpanded = isProvinceMenuExpanded,
                onProvinceMenuExpandedChange = { isProvinceMenuExpanded = it },
                selectedProvince = selectedProvince,
                provinceOptions = provinceOptions,
                onProvinceSelected = { selectedProvince = it }
            )

            // 🔍 Buscador (más bajo de altura)
            Spacer(Modifier.height(4.dp))
            SpotsSearchBar(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = "Buscar por nombre, categoría o localidad",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                state = listState
            ) {
                items(filteredItems, key = { it.spot.id }) { it ->
                    SpotRow(
                        spot = it.spot,
                        distanceMeters = it.distanceMeters,
                        onClick = { s -> onOpenSpot(s) },
                        onViewMap = onViewMap,
                        onShare = { s ->
                            shareSpot = s
                            showShareSheet = true
                        }
                    )
                }
            }
        }
    }


    // Sheet de opciones de compartir
    if (showShareSheet && shareSpot != null) {
        val spot = shareSpot!!
        val deepLink = remember(spot.id) {
            buildSpotDeepLink(spot)
        }

        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = {
                showShareSheet = false
                shareSpot = null
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
                        shareSpot = null
                        Toast.makeText(context, "Spot copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    headlineContent = { Text("Copiar enlace") }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }


    // Sheet 2: picker de chats para enviar el deeplink
    if (showChatPicker && shareSpot != null) {
        val spot = shareSpot!!
        val deepLink = remember(spot.id) {
            buildSpotDeepLink(spot)
        }

        SpotShareChatPickerSheet(
            onDismiss = {
                showChatPicker = false
                shareSpot = null
            },
            onChatSelected = { chatId ->
                coroutineScope.launch {
                    try {
                        sendSpotDeepLinkToChat(chatId, deepLink)
                        Toast.makeText(context, "Spot compartido", Toast.LENGTH_SHORT).show()
                    } finally {
                        showChatPicker = false
                        shareSpot = null
                    }
                }
            }
        )
    }



}

@Composable
private fun TopBarList(
    title: String,
    onBack: () -> Unit,
    onAddSpot: () -> Unit,
    isCategoryMenuExpanded: Boolean,
    onCategoryMenuExpandedChange: (Boolean) -> Unit,
    selectedCategory: SpotCategory?,
    onCategorySelected: (SpotCategory?) -> Unit,
    isProvinceMenuExpanded: Boolean,
    onProvinceMenuExpandedChange: (Boolean) -> Unit,
    selectedProvince: String?,
    provinceOptions: List<String>,
    onProvinceSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Volver",
                tint = Color(0xFF007AFF) // Action Blue iOS
            )
        }

        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(Modifier.weight(1f))

        // Botón "+"
        IconButton(onClick = onAddSpot) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Añadir spot",
                tint = Color(0xFF007AFF)
            )
        }

        // Menú de categoría
        Box {
            IconButton(onClick = { onCategoryMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = "Filtrar por categoría",
                    tint = Color(0xFF007AFF)
                )
            }

            DropdownMenu(
                expanded = isCategoryMenuExpanded,
                onDismissRequest = { onCategoryMenuExpandedChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Todas las categorías") },
                    onClick = {
                        onCategorySelected(null)
                        onCategoryMenuExpandedChange(false)
                    },
                    leadingIcon = {
                        if (selectedCategory == null) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null
                            )
                        }
                    }
                )

                HorizontalDivider()

                SpotCategory.values().forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.displayName()) },
                        onClick = {
                            onCategorySelected(cat)
                            onCategoryMenuExpandedChange(false)
                        },
                        leadingIcon = {
                            if (selectedCategory == cat) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        }
                    )
                }
            }
        }

        // Menú de provincia
        Box {
            IconButton(onClick = { onProvinceMenuExpandedChange(true) }) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Filtrar por provincia",
                    tint = Color(0xFF007AFF)
                )
            }

            DropdownMenu(
                expanded = isProvinceMenuExpanded,
                onDismissRequest = { onProvinceMenuExpandedChange(false) }
            ) {
                if (provinceOptions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No hay provincias detectadas") },
                        onClick = { onProvinceMenuExpandedChange(false) }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Todas las provincias") },
                        onClick = {
                            onProvinceSelected(null)
                            onProvinceMenuExpandedChange(false)
                        },
                        leadingIcon = {
                            if (selectedProvince == null) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    HorizontalDivider()

                    provinceOptions.forEach { province ->
                        DropdownMenuItem(
                            text = { Text(province) },
                            onClick = {
                                onProvinceSelected(province)
                                onProvinceMenuExpandedChange(false)
                            },
                            leadingIcon = {
                                if (selectedProvince.equals(province, ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun normalizeSearchText(input: String): String {
    if (input.isEmpty()) return ""
    val lower = input.lowercase(Locale.getDefault())
    val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
    // Elimina marcas de acento/diacríticos
    return normalized.replace("\\p{M}+".toRegex(), "")
}

private fun SpotCategory.displayName(): String = when (this) {
    SpotCategory.FREESTYLE_CAMPO_ABIERTO -> "Freestyle campo abierto"
    SpotCategory.FREESTYLE_BANDO -> "Freestyle Bando"
    SpotCategory.CINEMATICO -> "Cinemático"
    SpotCategory.RACING -> "Racing"
    SpotCategory.OTROS -> "Otros"
}

private data class Province(val name: String, val normalized: String)

private val SPAIN_PROVINCES: List<Province> = listOf(
    "Álava",
    "Albacete",
    "Alicante",
    "Almería",
    "Asturias",
    "Ávila",
    "Badajoz",
    "Barcelona",
    "Burgos",
    "Cáceres",
    "Cádiz",
    "Cantabria",
    "Castellón",
    "Ciudad Real",
    "Córdoba",
    "Cuenca",
    "Girona",
    "Granada",
    "Guadalajara",
    "Gipuzkoa",
    "Huelva",
    "Huesca",
    "Illes Balears",
    "Jaén",
    "A Coruña",
    "La Rioja",
    "Las Palmas",
    "León",
    "Lleida",
    "Lugo",
    "Madrid",
    "Málaga",
    "Murcia",
    "Navarra",
    "Ourense",
    "Palencia",
    "Pontevedra",
    "Salamanca",
    "Santa Cruz de Tenerife",
    "Segovia",
    "Sevilla",
    "Soria",
    "Tarragona",
    "Teruel",
    "Toledo",
    "Valencia",
    "Valladolid",
    "Bizkaia",
    "Zamora",
    "Zaragoza",
    "Ceuta",
    "Melilla"
).map { name ->
    Province(
        name = name,
        normalized = normalizeSearchText(name)
    )
}

/**
 * Resuelve la provincia de un spot a partir de lat/lon usando Geocoder
 * y la cruza con la lista oficial de provincias de España.
 */
private suspend fun resolveProvinceForSpot(
    geocoder: Geocoder,
    latitude: Double,
    longitude: Double
): String? = withContext(Dispatchers.IO) {
    try {
        val results = geocoder.getFromLocation(latitude, longitude, 1)
        val address = results?.firstOrNull() ?: return@withContext null

        // adminArea / subAdminArea suelen traer provincia o CCAA
        val rawProvince = address.subAdminArea ?: address.adminArea ?: return@withContext null
        val normalizedRaw = normalizeSearchText(rawProvince)

        val match = SPAIN_PROVINCES.firstOrNull { province ->
            normalizedRaw.contains(province.normalized) || province.normalized.contains(normalizedRaw)
        }

        match?.name
    } catch (e: Exception) {
        null
    }
}
@Composable
private fun SpotsSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(36.dp) // más baja que el TextField estándar
            .background(Color(0xFFF2F2F7), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        // Placeholder
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
            )
        }

        // Texto editable
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotShareChatPickerSheet(
    onDismiss: () -> Unit,
    onChatSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isLoading by remember { mutableStateOf(true) }
    var chats by remember { mutableStateOf<List<ShareChatUiModel>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        loadError = null

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid

        if (uid == null) {
            loadError = "Usuario no autenticado"
            isLoading = false
            return@LaunchedEffect
        }

        try {
            val snap = db.collection("chats")
                .whereArrayContains("participants", uid)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val result = mutableListOf<ShareChatUiModel>()

            for (doc in snap.documents) {
                val id = doc.id
                val participantsAny = doc.get("participants") as? List<*>
                val participants = participantsAny?.mapNotNull { it as? String } ?: emptyList()
                val isSupport = doc.getBoolean("isSupport") ?: false
                val isGroup = doc.getBoolean("isGroup") ?: (participants.size > 2)

                val otherUserId: String? = if (!isSupport && !isGroup) {
                    participants.firstOrNull { it != uid }
                } else {
                    null
                }

                // Título base
                var title: String = when {
                    isSupport -> {
                        doc.getString("title")
                            ?: doc.getString("name")
                            ?: doc.getString("displayName")
                            ?: "Soporte"
                    }
                    isGroup -> {
                        doc.getString("title")
                            ?: doc.getString("name")
                            ?: doc.getString("displayName")
                            ?: "Grupo"
                    }
                    else -> {
                        doc.getString("title")
                            ?: doc.getString("name")
                            ?: doc.getString("displayName")
                            ?: "Chat"
                    }
                }

                // 1:1 → usar nombre del otro participante
                if (!isSupport && !isGroup && otherUserId != null) {
                    val userSnap = db.collection("users")
                        .document(otherUserId)
                        .get()
                        .await()

                    val displayName = userSnap.getString("displayName").orEmpty()
                    val username = userSnap.getString("username").orEmpty()

                    title = when {
                        displayName.isNotBlank() -> displayName
                        username.isNotBlank() -> username
                        else -> title
                    }
                }

                result.add(
                    ShareChatUiModel(
                        id = id,
                        title = title,
                        isGroup = isGroup,
                        isSupport = isSupport,
                        otherUserId = otherUserId
                    )
                )
            }

            chats = result
            isLoading = false
        } catch (e: Exception) {
            loadError = e.message ?: "Error al cargar chats"
            isLoading = false
        }
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)          // ⬅️ nuevo
                .navigationBarsPadding()      // ⬅️ igual que en ChatDetail
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Elegir chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                loadError != null -> {
                    Text(
                        text = loadError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                chats.isEmpty() -> {
                    Text(
                        text = "No hay chats disponibles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column {
                        chats.forEach { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChatSelected(chat.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ChatPickerAvatar(chat = chat)

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private data class ShareChatUiModel(
    val id: String,
    val title: String,
    val isGroup: Boolean,
    val isSupport: Boolean,
    val otherUserId: String?
)
@Composable
private fun ChatPickerAvatar(
    chat: ShareChatUiModel
) {
    val context = LocalContext.current

    // Mismo degradado que en ChatsHome / ChatDetail
    val ringBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFF00D4FF),
                Color(0xFF7C4DFF),
                Color(0xFF00D4FF)
            )
        )
    }

    val isGroup = chat.isGroup
    val otherUserId = chat.otherUserId

    val outerRingScale = 32f / 36f
    val innerContentScale = 26f / 30f

    val cacheKey = remember(isGroup, otherUserId, chat.id) {
        if (isGroup) chat.id else otherUserId ?: chat.id
    }

    val localFile = remember(cacheKey) {
        cacheKey?.let { avatarLocalFile(context, it) }
    }
    val hasLocal = localFile?.exists() == true

    val initialAvatarUrl = remember(chat.id, isGroup, otherUserId) {
        try {
            if (isGroup) {
                ChatsLocalCache.getGroupPhoto(context, chat.id)
            } else if (!otherUserId.isNullOrBlank()) {
                ChatsLocalCache.getUserAvatar(context, otherUserId)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    val avatarUrl = initialAvatarUrl

    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        // Círculo exterior degradado
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(brush = ringBrush)
        )

        // Círculo blanco intermedio
        Box(
            modifier = Modifier
                .fillMaxSize(outerRingScale)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            // Círculo interior con la foto / iniciales
            Box(
                modifier = Modifier
                    .fillMaxSize(innerContentScale)
                    .clip(CircleShape)
                    .background(Color(0xFFECECEC)),
                contentAlignment = Alignment.Center
            ) {
                val modelUrl = avatarUrl

                when {
                    hasLocal -> {
                        val bitmap = remember(localFile?.path) {
                            try {
                                BitmapFactory.decodeFile(localFile?.path ?: "")
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
                        } else if (!modelUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = modelUrl,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onSuccess = { state: AsyncImagePainter.State.Success ->
                                    if (localFile != null) {
                                        saveDrawableToFile(
                                            state.result.drawable,
                                            localFile
                                        )
                                    }
                                }
                            )
                        } else {
                            ChatPickerAvatarFallback(text = chat.title)
                        }
                    }

                    !modelUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = modelUrl,
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { state: AsyncImagePainter.State.Success ->
                                if (localFile != null) {
                                    saveDrawableToFile(
                                        state.result.drawable,
                                        localFile
                                    )
                                }
                            }
                        )
                    }

                    else -> {
                        ChatPickerAvatarFallback(text = chat.title)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPickerAvatarFallback(text: String) {
    val initial = remember(text) {
        text.trim().take(1).uppercase()
    }

    Text(
        text = initial,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF555555)
    )
}

suspend fun sendSpotDeepLinkToChat(
    chatId: String,
    deepLink: String
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val senderId = auth.currentUser?.uid ?: return
    val now = Timestamp.now()

    val trimmed = deepLink.trim()
    if (trimmed.isEmpty()) return

    val msgRef = db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document()

    val data = hashMapOf<String, Any?>(
        "id" to msgRef.id,
        "chatId" to chatId,
        "senderId" to senderId,
        "text" to trimmed,
        "type" to "text",
        "createdAtClient" to now,
        "createdAt" to FieldValue.serverTimestamp()
    )

    msgRef.set(data).await()
}
// -------- Deep link 1:1 con iOS para compartir spots --------

fun buildSpotDeepLink(
    spot: com.spotitfly.app.data.model.Spot
): String {
    val params = mutableListOf<String>()

    // nombre
    if (spot.name.isNotBlank()) {
        params += "n=${encodeSpotParam(spot.name)}"
    }

    // coordenadas
    params += "lat=${spot.latitude}"
    params += "lon=${spot.longitude}"

    // rating medio (rm)
    computeDeepLinkRating(spot)?.let { rm ->
        val formatted = String.format(java.util.Locale.US, "%.1f", rm)
        params += "rm=$formatted"
    }

    // imagen principal (img)
    if (!spot.imageUrl.isNullOrBlank()) {
        params += "img=${encodeSpotParam(spot.imageUrl)}"
    }

    // localidad / ubicación legible (loc)
    if (!spot.locality.isNullOrBlank()) {
        params += "loc=${encodeSpotParam(spot.locality)}"
    }

    val base = StringBuilder()
        .append("spots://spot/")
        .append(spot.id)

    if (params.isNotEmpty()) {
        base.append("?")
            .append(params.joinToString("&"))
    }

    return base.toString()
}

private fun encodeSpotParam(raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    return try {
        java.net.URLEncoder.encode(raw, "UTF-8")
            .replace("+", "%20")
    } catch (_: Exception) {
        raw.replace(" ", "%20")
    }
}


private fun computeDeepLinkRating(
    spot: com.spotitfly.app.data.model.Spot
): Double? {
    // 1) ratingMean si existe (nuevo sistema)
    val meanField = (spot.ratingMean as? Number)?.toDouble()
    if (meanField != null) return meanField

    // 2) averageRating si existe (campo consolidado en doc)
    val avgField = (spot.averageRating as? Number)?.toDouble()
    if (avgField != null) return avgField

    // 3) Media de 'ratings' (mapa de votos por usuario)
    computeRatingsMapMeanForDeepLink(spot)?.let { return it }

    // 4) Legacy 'rating'
    val legacy = (spot.rating as? Number)?.toDouble()
    return legacy
}

private fun computeRatingsMapMeanForDeepLink(
    spot: com.spotitfly.app.data.model.Spot
): Double? {
    val map = spot.ratings as? Map<*, *> ?: return null
    var sum = 0.0
    var count = 0
    for (v in map.values) {
        val d = when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
        if (d != null) {
            sum += d
            count++
        }
    }
    return if (count > 0) sum / count else null
}
