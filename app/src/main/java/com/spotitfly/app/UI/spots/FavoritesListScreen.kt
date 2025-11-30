package com.spotitfly.app.ui.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import com.spotitfly.app.ui.favorites.FavoritesViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues

@Composable
fun FavoritesListScreen(
    onBack: () -> Unit,
    onOpenSpot: (Spot) -> Unit,
    onViewMap: (Spot) -> Unit,
    listState: LazyListState
) {
    val context = LocalContext.current

    // Misma VM de spots que la lista general
    val spotsVm: SpotsListViewModel =
        viewModel(factory = SpotsListViewModelFactory(SpotsRepository()))

    // VM global de favoritos
    val favoritesVm: FavoritesViewModel = viewModel()

    LaunchedEffect(Unit) {
        spotsVm.start(context)
    }

    val items by spotsVm.items.collectAsState()
    val favoriteIds by favoritesVm.favoriteIds.collectAsState()

    // Filtrar solo los spots que están en favoritos (1:1 iOS)
    val favoriteItems = remember(items, favoriteIds) {
        items.filter { favoriteIds.contains(it.spot.id) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        FavoritesTopBar(onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            state = listState
        ) {
            items(favoriteItems, key = { it.spot.id }) { it ->
                SpotRow(
                    spot = it.spot,
                    distanceMeters = it.distanceMeters,
                    onClick = { s -> onOpenSpot(s) },
                    onViewMap = onViewMap
                )
            }
        }
    }
}

@Composable
private fun FavoritesTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
        }
        Spacer(Modifier.width(8.dp))
        Text("Favoritos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
    }
}
