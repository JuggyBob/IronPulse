package com.example.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Immutable
data class ChartPoint(
    val timestamp: Long,
    val value: Double,
    val label: String,
    val detailText: String
)

@Composable
fun ProgressionLineChart(
    points: List<ChartPoint>,
    yAxisUnit: String,
    lineColor: Color = ElectricCyan,
    modifier: Modifier = Modifier,
    referenceLineValue: Double? = null,
    referenceLineLabel: String? = null
) {
    if (points.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No progression data for selected filter",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Log workout sets or adjust your added-weight tag to see chart curves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    var selectedPointIndex by remember(points) { mutableStateOf<Int?>(null) }
    val selectedPoint = selectedPointIndex?.let { points.getOrNull(it) }

    val sortedPoints = remember(points) { points.sortedBy { it.timestamp } }
    val minVal = remember(sortedPoints, referenceLineValue) {
        val ptsMin = sortedPoints.minOf { it.value }
        if (referenceLineValue != null) min(ptsMin, referenceLineValue) else ptsMin
    }
    val maxVal = remember(sortedPoints, referenceLineValue) {
        val ptsMax = sortedPoints.maxOf { it.value }
        if (referenceLineValue != null) max(ptsMax, referenceLineValue) else ptsMax
    }
    val valueRange = if (maxVal == minVal) 1.0 else maxVal - minVal

    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint().apply {
            color = TextMuted.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }
    val refPaint = remember(density) {
        Paint().apply {
            color = MintGreen.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            isAntiAlias = true
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Selected tooltip header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPoint != null) {
                    Column {
                        Text(
                            text = "${"%.1f".format(selectedPoint.value)} $yAxisUnit",
                            style = MaterialTheme.typography.titleLarge,
                            color = lineColor
                        )
                        Text(
                            text = "${dateFormat.format(Date(selectedPoint.timestamp))} • ${selectedPoint.detailText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                } else {
                    val latest = sortedPoints.last()
                    val first = sortedPoints.first()
                    val delta = latest.value - first.value
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${"%.1f".format(latest.value)} $yAxisUnit",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            val isPositive = delta >= 0
                            Text(
                                text = "${if (isPositive) "+" else ""}${"%.1f".format(delta)} $yAxisUnit",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isPositive) MintGreen else Color(0xFFF85149)
                            )
                        }
                        Text(
                            text = "Latest: ${dateFormat.format(Date(latest.timestamp))} • Tap graph nodes to inspect",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sortedPoints) {
                            detectTapGestures { tapOffset ->
                                val width = size.width
                                val paddingLeft = 40f
                                val paddingRight = 24f
                                val usableWidth = width - paddingLeft - paddingRight

                                if (sortedPoints.isNotEmpty()) {
                                    val step = if (sortedPoints.size > 1) usableWidth / (sortedPoints.size - 1) else 0f
                                    var closestIndex = 0
                                    var minDistance = Float.MAX_VALUE

                                    sortedPoints.forEachIndexed { index, _ ->
                                        val pointX = if (sortedPoints.size == 1) paddingLeft + usableWidth / 2f else paddingLeft + index * step
                                        val distance = kotlin.math.abs(tapOffset.x - pointX)
                                        if (distance < minDistance) {
                                            minDistance = distance
                                            closestIndex = index
                                        }
                                    }
                                    selectedPointIndex = if (minDistance < 60f) closestIndex else null
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val paddingLeft = 44f
                    val paddingRight = 20f
                    val paddingTop = 24f
                    val paddingBottom = 32f

                    val usableWidth = width - paddingLeft - paddingRight
                    val usableHeight = height - paddingTop - paddingBottom

                    // Draw 3 horizontal grid lines (min, mid, max)
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val fraction = i.toFloat() / gridSteps
                        val y = paddingTop + (1f - fraction) * usableHeight
                        val gridValue = minVal + fraction * valueRange

                        drawLine(
                            color = DarkBorder.copy(alpha = 0.6f),
                            start = Offset(paddingLeft, y),
                            end = Offset(width - paddingRight, y),
                            strokeWidth = 1f
                        )

                        // Label
                        drawContext.canvas.nativeCanvas.drawText(
                            "%.0f".format(gridValue),
                            4f,
                            y + 4f,
                            textPaint
                        )
                    }

                    // Optional Reference line (e.g. Target Weight)
                    referenceLineValue?.let { refVal ->
                        val refFraction = ((refVal - minVal) / valueRange).toFloat().coerceIn(0f, 1f)
                        val refY = paddingTop + (1f - refFraction) * usableHeight
                        drawLine(
                            color = MintGreen.copy(alpha = 0.8f),
                            start = Offset(paddingLeft, refY),
                            end = Offset(width - paddingRight, refY),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                        referenceLineLabel?.let { label ->
                            drawContext.canvas.nativeCanvas.drawText(
                                label,
                                width - paddingRight - 80f,
                                refY - 6f,
                                refPaint
                            )
                        }
                    }

                    if (sortedPoints.isEmpty()) return@Canvas

                    // Calculate point offsets
                    val offsets = sortedPoints.mapIndexed { index, point ->
                        val x = if (sortedPoints.size == 1) {
                            paddingLeft + usableWidth / 2f
                        } else {
                            paddingLeft + (index.toFloat() / (sortedPoints.size - 1)) * usableWidth
                        }
                        val yFraction = ((point.value - minVal) / valueRange).toFloat().coerceIn(0f, 1f)
                        val y = paddingTop + (1f - yFraction) * usableHeight
                        Offset(x, y)
                    }

                    // Build path
                    if (offsets.size > 1) {
                        val path = Path().apply {
                            moveTo(offsets[0].x, offsets[0].y)
                            for (i in 0 until offsets.size - 1) {
                                val p0 = offsets[i]
                                val p1 = offsets[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }

                        // Gradient fill under the curve
                        val fillPath = Path().apply {
                            addPath(path)
                            lineTo(offsets.last().x, height - paddingBottom)
                            lineTo(offsets.first().x, height - paddingBottom)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.35f),
                                    lineColor.copy(alpha = 0.0f)
                                ),
                                startY = paddingTop,
                                endY = height - paddingBottom
                            )
                        )

                        // Draw stroke
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Draw circles at data points
                    offsets.forEachIndexed { index, offset ->
                        val isSelected = selectedPointIndex == index
                        if (isSelected) {
                            drawCircle(
                                color = lineColor.copy(alpha = 0.25f),
                                radius = 12.dp.toPx(),
                                center = offset
                            )
                        }
                        drawCircle(
                            color = DarkSurface,
                            radius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx(),
                            center = offset
                        )
                        drawCircle(
                            color = if (isSelected) Color.White else lineColor,
                            radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
                            center = offset
                        )

                        // Date label on bottom axis for selected or edge points
                        if (sortedPoints.size <= 5 || index == 0 || index == sortedPoints.lastIndex || isSelected) {
                            val dateStr = dateFormat.format(Date(sortedPoints[index].timestamp))
                            drawContext.canvas.nativeCanvas.drawText(
                                dateStr,
                                offset.x - 20f,
                                height - 8f,
                                textPaint
                            )
                        }
                    }
                }
            }
        }
    }
}
