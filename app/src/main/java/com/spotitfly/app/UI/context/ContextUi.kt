package com.spotitfly.app.ui.context

//import androidx.compose.foundation.Background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---- Colores consistentes con el app ----
private val Orange = Color(0xFFFF9800)
private val Blue = Color(0xFF2196F3)
private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val Purple = Color(0xFF9C27FF)

// ---- Datos mínimos para el paso 2 (UI) ----
data class NotamUi(
    val notamId: String? = null,
    val itemB: String? = null,
    val itemC: String? = null,
    val itemD: String? = null,
    val descriptionHtml: String? = null, // equivalente a DESCRIPTION / itemE
)

// ---- Cabecera de sección ----
@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// ---- Tarjeta genérica con HTML ----
@Composable
fun ContextItemCard(title: String, html: String?, strokeColor: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, strokeColor)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = strokeColor
            )
            if (!html.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                HTMLText(html = html, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---- Tarjeta NOTAM ----
@Composable
fun NotamItemCard(item: NotamUi) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Red)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                item.notamId ?: "(Sin ID)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Red
            )
            item.itemB?.let {
                NotamLine("📅", "DESDE: $it")
            }
            item.itemC?.let {
                NotamLine("📅", "HASTA: $it")
            }
            item.itemD?.takeIf { it.isNotBlank() }?.let {
                NotamLine("⏰", "HORARIO: $it")
            }
            val html = item.descriptionHtml
            if (!html.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("📝", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    HTMLText(
                        html = html,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun NotamLine(icon: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(icon, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ---- Aviso legal 1:1 con iOS ----
@Composable
fun LegalNoticeBox() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Orange)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "⚠️ Aviso",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Orange
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "La información de contexto aéreo puede no ser 100% precisa ni estar actualizada. " +
                        "Cada usuario es responsable de verificarla. SpotItFly no se hace responsable del uso que se haga de estos datos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- NOTAMs (siempre muestra cabecera + fallback) ----
@Composable
fun NotamsSection(notams: List<NotamUi>) {
    SectionHeader(title = "NOTAMs", color = Red)
    Spacer(Modifier.height(8.dp))

    if (notams.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                "Sin NOTAM en este punto",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            notams.forEach { NotamItemCard(it) }
        }
    }
}

// ---- Scaffold de secciones (paso 2: placeholders) ----
@Composable
fun ContextSectionsScaffold(
    restricciones: List<Pair<String, String?>> = emptyList(),
    infraestructuras: List<Pair<String, String?>> = emptyList(),
    medioambiente: List<Pair<String, String?>> = emptyList(),
    notams: List<NotamUi> = emptyList(),
    urbanas: List<Pair<String, String?>> = emptyList()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (restricciones.isNotEmpty()) {
            SectionHeader("Restricciones Aéreas", Orange)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                restricciones.forEach { (title, html) ->
                    ContextItemCard(title = title, html = html, strokeColor = Orange)
                }
            }
        }

        if (infraestructuras.isNotEmpty()) {
            SectionHeader("Infraestructuras", Blue)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                infraestructuras.forEach { (title, html) ->
                    ContextItemCard(title = title, html = html, strokeColor = Blue)
                }
            }
        }

        if (medioambiente.isNotEmpty()) {
            SectionHeader("Medioambiente", Green)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                medioambiente.forEach { (title, html) ->
                    ContextItemCard(title = title, html = html, strokeColor = Green)
                }
            }
        }

        // NOTAMs: SIEMPRE cabecera y fallback
        NotamsSection(notams)

        if (urbanas.isNotEmpty()) {
            SectionHeader("Zonas Urbanas", Purple)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                urbanas.forEach { (title, html) ->
                    ContextItemCard(title = title, html = html, strokeColor = Purple)
                }
            }
        }

        // Aviso legal (siempre al final)
        LegalNoticeBox()
    }
}
