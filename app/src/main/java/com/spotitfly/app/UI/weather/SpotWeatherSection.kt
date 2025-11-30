package com.spotitfly.app.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotitfly.app.ui.design.SpotDetailTokens as T

/**
 * Presentacional: muestra la pill y, si está expandido, la tira de 15 días.
 * - daily: lista de elementos ya resueltos (placeholder en Paso A).
 * - onDayTap: callback que recibe el índice del día para que el padre abra el sheet horario.
 */
@Composable
fun SpotWeatherSection(
    daily: List<WeatherDailyUi>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDayTap: (Int) -> Unit
) {
    Column {
        // ---- PILLA ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFE9E9EE),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onToggleExpanded() }
            ) {
                Text(
                    text = daily.firstOrNull()?.pillText ?: "—°",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(T.gapM))
            Text("Tiempo 15 días", style = MaterialTheme.typography.titleSmall)
        }

        if (!expanded) return

        Spacer(Modifier.height(T.gapS))

        // ---- TIRA 15 DÍAS ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = T.padPage)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            daily.forEachIndexed { idx, d ->
                DayCard(
                    label = d.label,
                    temp = d.tempText,
                    selected = d.selected,
                    onClick = { onDayTap(idx) },
                    modifier = Modifier
                        .width(92.dp)
                        .height(68.dp)
                )
                if (idx != daily.lastIndex) Spacer(Modifier.width(T.gapS))
            }
        }
    }
}

@Composable
private fun DayCard(
    label: String,
    temp: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (selected) 2.dp else 1.dp,
        color = if (selected) Color(0xFFF9FBFF) else Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF222222), maxLines = 1)
            Text(temp, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6A6A6A), maxLines = 1)
        }
    }
}

/** ViewModel/UI model para no acoplar al repositorio en el Paso A */
data class WeatherDailyUi(
    val label: String,     // p.ej: "JUE 6 NOV"
    val tempText: String,  // p.ej: "19° / 9°"
    val pillText: String,  // p.ej: "JUE 6 NOV • 19° • ↑ 21 km/h"
    val selected: Boolean = false
)
