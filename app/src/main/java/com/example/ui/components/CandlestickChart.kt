package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Bar
import com.example.model.SetupCandidate
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.EmaGold
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChart(
    candidate: SetupCandidate,
    modifier: Modifier = Modifier
) {
    val bars = candidate.weeklyBars
    if (bars.isEmpty()) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val activeBar = selectedIndex?.let { bars.getOrNull(it) } ?: bars.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        // Top Legend & Inspector Bar
        if (activeBar != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeBar.date,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "O: ${activeBar.open.format2()}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "H: ${activeBar.high.format2()}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "L: ${activeBar.low.format2()}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "C: ${activeBar.close.format2()}",
                    color = if (activeBar.close >= activeBar.open) BullGreen else BearRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "EMA: ${activeBar.ema20.format2()}",
                    color = EmaGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Chart Drawing Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bars) {
                        detectTapGestures { offset ->
                            val n = bars.size
                            val step = size.width / n.toFloat()
                            val idx = (offset.x / step).toInt().coerceIn(0, n - 1)
                            selectedIndex = idx
                        }
                    }
            ) {
                val canvasW = size.width
                val canvasH = size.height

                val priceAreaH = canvasH * 0.76f
                val volumeAreaH = canvasH * 0.22f
                val volumeAreaTop = canvasH * 0.78f

                // Find global price min and max
                var minP = Double.MAX_VALUE
                var maxP = Double.MIN_VALUE
                var maxV = 0.0

                for (b in bars) {
                    if (b.low < minP) minP = b.low
                    if (b.high > maxP) maxP = b.high
                    if (b.ema20 > 0 && b.ema20 < minP) minP = b.ema20
                    if (b.ema20 > maxP) maxP = b.ema20
                    if (b.volume > maxV) maxV = b.volume
                }

                // Add margins to price range
                val priceRange = max(1.0, maxP - minP)
                val paddedMin = minP - priceRange * 0.05
                val paddedMax = maxP + priceRange * 0.05
                val totalPaddedRange = paddedMax - paddedMin

                fun priceToY(price: Double): Float {
                    val norm = (price - paddedMin) / totalPaddedRange
                    return (priceAreaH - (norm * priceAreaH)).toFloat()
                }

                fun volToY(vol: Double): Float {
                    val norm = if (maxV > 0) (vol / maxV).toFloat() else 0f
                    return volumeAreaTop + volumeAreaH - (norm * volumeAreaH)
                }

                val nBars = bars.size
                val stepX = canvasW / nBars.toFloat()
                val candleBodyW = max(3f, stepX * 0.65f)

                // 1. Draw Grid lines
                val gridLevels = 4
                for (g in 0..gridLevels) {
                    val y = (priceAreaH / gridLevels) * g
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, y),
                        end = Offset(canvasW, y),
                        strokeWidth = 1f
                    )
                }

                // 2. Base Zone Shading
                val bStart = candidate.baseStartIndex.coerceIn(0, nBars - 1)
                val bEnd = candidate.baseEndIndex.coerceIn(0, nBars - 1)
                val baseLeft = bStart * stepX
                val baseRight = (bEnd + 1) * stepX

                drawRoundRect(
                    color = Color(0x22F59E0B),
                    topLeft = Offset(baseLeft, 0f),
                    size = Size(baseRight - baseLeft, priceAreaH),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                // 3. Draw EMA20 Line
                val emaPath = Path()
                var emaStarted = false
                for (i in bars.indices) {
                    val ema = bars[i].ema20
                    if (ema > 0) {
                        val x = i * stepX + stepX / 2f
                        val y = priceToY(ema)
                        if (!emaStarted) {
                            emaPath.moveTo(x, y)
                            emaStarted = true
                        } else {
                            emaPath.lineTo(x, y)
                        }
                    }
                }
                drawPath(
                    path = emaPath,
                    color = EmaGold,
                    style = Stroke(width = 3.5f)
                )

                // 4. Draw Candlesticks & Volume
                for (i in bars.indices) {
                    val b = bars[i]
                    val xCenter = i * stepX + stepX / 2f
                    val isGreen = b.close >= b.open
                    val barColor = if (isGreen) BullGreen else BearRed

                    val yHigh = priceToY(b.high)
                    val yLow = priceToY(b.low)
                    val yOpen = priceToY(b.open)
                    val yClose = priceToY(b.close)

                    // Wick
                    drawLine(
                        color = barColor,
                        start = Offset(xCenter, yHigh),
                        end = Offset(xCenter, yLow),
                        strokeWidth = 1.5f
                    )

                    // Candle Body
                    val bodyTop = min(yOpen, yClose)
                    val bodyH = max(2f, kotlin.math.abs(yOpen - yClose))
                    drawRect(
                        color = barColor,
                        topLeft = Offset(xCenter - candleBodyW / 2f, bodyTop),
                        size = Size(candleBodyW, bodyH)
                    )

                    // Volume Bar
                    val volY = volToY(b.volume)
                    val volH = (volumeAreaTop + volumeAreaH) - volY
                    drawRect(
                        color = barColor.copy(alpha = 0.55f),
                        topLeft = Offset(xCenter - candleBodyW / 2f, volY),
                        size = Size(candleBodyW, volH)
                    )
                }

                // 5. Annotations: Breakout Candle
                val brkIdx = candidate.breakoutIndex.coerceIn(0, nBars - 1)
                val brkX = brkIdx * stepX + stepX / 2f
                val brkY = priceToY(bars[brkIdx].high)
                drawCircle(
                    color = BullGreen,
                    radius = 5f,
                    center = Offset(brkX, brkY - 8f)
                )

                // 6. Annotations: Touch Candle
                candidate.touchIndex?.let { tIdx ->
                    val touchX = tIdx * stepX + stepX / 2f
                    val touchY = priceToY(bars[tIdx].low)
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 6f,
                        center = Offset(touchX, touchY + 8f)
                    )
                }

                // 7. Trade Price Levels
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                val dotEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

                // Entry Line (Blue)
                val yEntry = priceToY(candidate.entryPrice)
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(0f, yEntry),
                    end = Offset(canvasW, yEntry),
                    strokeWidth = 1.8f,
                    pathEffect = dashEffect
                )

                // Stop Loss Line (Red)
                val yStop = priceToY(candidate.stopLoss)
                drawLine(
                    color = BearRed,
                    start = Offset(0f, yStop),
                    end = Offset(canvasW, yStop),
                    strokeWidth = 2.0f,
                    pathEffect = dotEffect
                )

                // Target 3R Line (Green)
                val yTarget = priceToY(candidate.target3R)
                drawLine(
                    color = BullGreen,
                    start = Offset(0f, yTarget),
                    end = Offset(canvasW, yTarget),
                    strokeWidth = 2.0f,
                    pathEffect = dotEffect
                )

                // 8. Crosshair for selected bar
                selectedIndex?.let { sel ->
                    val selX = sel * stepX + stepX / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, canvasH),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                }
            }
        }

        // Bottom Legend / Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF22C55E).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🎯 Target: ₹${candidate.target3R.format2()}",
                    color = BullGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "📍 Entry: ₹${candidate.entryPrice.format2()}",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🛑 Stop: ₹${candidate.stopLoss.format2()}",
                    color = BearRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun Double.format2(): String = String.format(java.util.Locale.US, "%.2f", this)
