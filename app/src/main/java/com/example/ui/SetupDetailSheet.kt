package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SetupCandidate
import com.example.model.SetupStatus
import com.example.ui.components.CandlestickChart
import com.example.ui.components.format2
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.EmaGold
import com.example.ui.theme.SkyBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDetailSheet(
    candidate: SetupCandidate,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        modifier = modifier.testTag("setup_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = candidate.symbol,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF334155), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = candidate.exchange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    Text(
                        text = candidate.company.ifBlank { "Indian Equity" },
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                StatusBadge(status = candidate.status)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (candidate.score >= 80) BullGreen.copy(alpha = 0.2f) else EmaGold.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${candidate.score}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (candidate.score >= 80) BullGreen else EmaGold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (candidate.score >= 85) "A+ Institutional Grade Setup" else "High Probability Setup",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tight Base • Vol Breakout • EMA First Touch",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Candlestick Chart
            Text(
                text = "Weekly Chart & Setup Zones",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CandlestickChart(candidate = candidate)

            Spacer(modifier = Modifier.height(20.dp))

            // Trade Math Execution Plan
            Text(
                text = "Trade Execution Math",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TradeStatItem("Current CMP", "₹${candidate.cmp.format2()}", Color.White)
                        TradeStatItem("Entry Price", "₹${candidate.entryPrice.format2()}", SkyBlue)
                        TradeStatItem("Stop Loss", "₹${candidate.stopLoss.format2()}", BearRed)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TradeStatItem("Target (3R)", "₹${candidate.target3R.format2()}", BullGreen)
                        TradeStatItem("Max Risk", "${candidate.riskPct.format2()}%", if (candidate.riskPct <= 5.0) BullGreen else EmaGold)
                        TradeStatItem("Reward:Risk", "1:${candidate.rrRatio.format2()}", BullGreen)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sub-Scores Breakdown
            Text(
                text = "A+ Score Factor Breakdown",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ScoreBar(
                        title = "Base Tightness (${candidate.baseLength}W, ${candidate.baseDepthPct}% depth)",
                        score = candidate.subScores["Base Tightness"] ?: 20,
                        maxScore = 25
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreBar(
                        title = "Breakout Volume Multiple (${candidate.volumeMult}x)",
                        score = candidate.subScores["Volume Multiple"] ?: 20,
                        maxScore = 25
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreBar(
                        title = "Rising 20-EMA Slope",
                        score = candidate.subScores["EMA Slope"] ?: 16,
                        maxScore = 20
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreBar(
                        title = "Pullback Volume Dry-Up (${candidate.volDryupPct ?: 50.0}%)",
                        score = candidate.subScores["Volume Dry-Up"] ?: 12,
                        maxScore = 15
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreBar(
                        title = "Low Risk Size (${candidate.riskPct}%)",
                        score = candidate.subScores["Risk Size"] ?: 12,
                        maxScore = 15
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: SetupStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor, text) = when (status) {
        SetupStatus.TOUCH -> Triple(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF38BDF8), "TOUCH @ EMA")
        SetupStatus.BOUNCE -> Triple(BullGreen.copy(alpha = 0.2f), BullGreen, "BOUNCE CONFIRMED")
        SetupStatus.WATCH -> Triple(EmaGold.copy(alpha = 0.2f), EmaGold, "WATCHLIST")
    }

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor
        )
    }
}

@Composable
private fun TradeStatItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun ScoreBar(title: String, score: Int, maxScore: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontSize = 12.sp, color = Color(0xFFCBD5E1))
            Text(
                text = "$score / $maxScore",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BullGreen
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score.toFloat() / maxScore.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = BullGreen,
            trackColor = Color(0xFF334155),
        )
    }
}
