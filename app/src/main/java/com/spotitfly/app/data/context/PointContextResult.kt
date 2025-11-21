package com.spotitfly.app.data.context

import com.spotitfly.app.ui.context.NotamUi

/**
 * Datos ya listos para la UI (1:1 con ContextSectionsScaffold).
 */
data class PointContextResult(
    val locality: String? = null,
    val restricciones: List<Pair<String, String?>> = emptyList(),
    val infraestructuras: List<Pair<String, String?>> = emptyList(),
    val medioambiente: List<Pair<String, String?>> = emptyList(),
    val urbanas: List<Pair<String, String?>> = emptyList(),
    val notams: List<NotamUi> = emptyList()
)
