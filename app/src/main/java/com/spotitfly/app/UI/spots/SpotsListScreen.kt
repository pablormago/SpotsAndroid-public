package com.spotitfly.app.ui.spots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.spotitfly.app.ui.spots.SpotRow
import com.spotitfly.app.ui.spots.SpotsListViewModel
import com.spotitfly.app.ui.spots.SpotsListViewModelFactory
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues

@Composable
fun SpotsListScreen(
    onBack: () -> Unit,
    onOpenSpot: (com.spotitfly.app.data.model.Spot) -> Unit,
    onViewMap: (com.spotitfly.app.data.model.Spot) -> Unit
) {

    val context = LocalContext.current
    val vm: SpotsListViewModel = viewModel(factory = SpotsListViewModelFactory(SpotsRepository()))
    LaunchedEffect(Unit) { vm.start(context) }
    val items by vm.items.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(WindowInsets.safeDrawing.asPaddingValues())
    ) {
        TopBarList(title = "Spots cercanos", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            items(items, key = { it.spot.id }) { it ->
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
private fun TopBarList(title: String, onBack: () -> Unit) {
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
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
    }
}
