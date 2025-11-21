package com.spotitfly.app.ui.spots

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import com.google.firebase.auth.FirebaseAuth
import com.spotitfly.app.data.model.SpotCategory
import com.spotitfly.app.data.SpotsRepository
import com.spotitfly.app.data.model.Spot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotCreateScreen(
    initialLat: Double,
    initialLng: Double,
    onCancel: () -> Unit,
    onCreated: (Spot) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var saving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        SpotFormScreen(
            existing = null,
            initialLat = initialLat,
            initialLng = initialLng,
            onBack = onCancel,
            onSave = { inp ->
                if (saving) return@SpotFormScreen
                saving = true
                scope.launch {
                    try {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                            ?: throw IllegalStateException("No autenticado")

                        val repo = SpotsRepository()
                        val created = repo.createSpot(
                            name = inp.name.trim(),
                            description = inp.description.trim(),
                            latitude = inp.latitude,
                            longitude = inp.longitude,
                            category = inp.category,
                            createdBy = uid,
                            localidad = inp.locality,
                            myRating = inp.myRating,
                            imageUri = inp.imageUri
                        )

                        snack.showSnackbar("Spot creado")
                        delay(250)
                        onCreated(created)
                    } catch (e: Exception) {
                        snack.showSnackbar(e.message ?: "Error al crear")
                    } finally {
                        saving = false
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
