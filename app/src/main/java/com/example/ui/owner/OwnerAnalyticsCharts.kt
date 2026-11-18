package com.example.ui.owner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.OwnerAnalytics
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.OwnerAmber400
import com.example.ui.theme.SignalTeal400

@Composable
fun DailyActivityBarChart(
    analytics: OwnerAnalytics,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val maxVal = (analytics.dailyActivity.maxOfOrNull { it.messageCount } ?: 0).coerceAtLeast(1)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nachrichtenaktivität (7 Tage)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Max: $maxVal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val items = analytics.dailyActivity
                if (items.isNotEmpty()) {
                    val slotW = size.width / items.size
                    val barW = (slotW * 0.48f).coerceAtMost(42.dp.toPx())
                    val maxBarH = size.height - 8.dp.toPx()

                    items.forEachIndexed { index, metric ->
                        val left = index * slotW + (slotW - barW) / 2f
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 4.dp.toPx()),
                            size = Size(barW, maxBarH),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                        val ratio = metric.messageCount.toFloat() / maxVal.toFloat()
                        val filledH = (maxBarH * ratio).coerceAtLeast(if (metric.messageCount > 0) 8.dp.toPx() else 0f)
                        if (filledH > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, 4.dp.toPx() + (maxBarH - filledH)),
                                size = Size(barW, filledH),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                analytics.dailyActivity.forEach { metric ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${metric.messageCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = metric.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBreakdownCard(
    analytics: OwnerAnalytics,
    modifier: Modifier = Modifier
) {
    val total = (analytics.textMessages + analytics.voiceMessages + analytics.fileMessages + analytics.callEvents)
        .coerceAtLeast(1)

    val segments = listOf(
        Triple("Text", analytics.textMessages, SignalTeal400),
        Triple("Sprache", analytics.voiceMessages, OwnerAmber400),
        Triple("Dateien", analytics.fileMessages, Color(0xFF38BDF8)),
        Triple("Anrufe", analytics.callEvents, EmeraldOnline)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Nutzung nach Inhaltstyp",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(99.dp))
            ) {
                val sum = segments.sumOf { it.second }
                if (sum == 0) {
                    drawRect(color = Color.Gray.copy(alpha = 0.25f))
                } else {
                    var cursorX = 0f
                    segments.forEach { (_, count, color) ->
                        if (count > 0) {
                            val w = (count.toFloat() / total.toFloat()) * size.width
                            drawRect(
                                color = color,
                                topLeft = Offset(cursorX, 0f),
                                size = Size(w, size.height)
                            )
                            cursorX += w
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                segments.forEach { (label, count, color) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
