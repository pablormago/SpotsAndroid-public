package com.spotitfly.app.ui.spots

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotEditScreen(
    existing: Spot,
    onCancel: () -> Unit,
    onSaved: (Spot) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
        SpotFormScreen(
            existing = existing,
            onBack = onCancel,
            onSave = { inp ->
                scope.launch {
                    try {
                        val repo = SpotsRepository()
                        repo.updateSpot(
                            id = existing.id,
                            name = inp.name.trim(),
                            description = inp.description.trim(),
                            category = inp.category,
                            latitude = inp.latitude,
                            longitude = inp.longitude,
                            localidad = inp.locality,
                            myRating = inp.myRating,
                            imageUri = inp.imageUri
                        )


                        snack.showSnackbar("Spot actualizado")
                        delay(250)
                        onSaved(
                            existing.copy(
                                name = inp.name.trim(),
                                description = inp.description.trim(),
                                latitude = inp.latitude,
                                longitude = inp.longitude,
                                category = inp.category,
                                acceso = inp.acceso,
                                locality = inp.locality,
                                bestDate = inp.bestDate
                            )
                        )
                    } catch (e: Exception) {
                        snack.showSnackbar(e.message ?: "Error al guardar")
                    }
                }
            }
        )

        SnackbarHost(
            hostState = snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        )
    }
}
