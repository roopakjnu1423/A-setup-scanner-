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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BacktestReplayItem
import com.example.ui.components.format2
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen

@Composable
fun ReplayModeDialog(
    replayItems: List<BacktestReplayItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("⏪ Historical Replay & Forward Returns", fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Simulated forward performance at 4, 8, and 12 weeks for detected setups",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (replayItems.isEmpty()) {
                    Text(
                        "No historical setups available. Run a scan first!",
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(replayItems) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.symbol,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                        StatusBadge(status = item.status)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Entry: ₹${item.entryPrice.format2()} | Stop: ₹${item.stopLoss.format2()}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        ReturnBadge("+4W", item.return4W)
                                        ReturnBadge("+8W", item.return8W)
                                        ReturnBadge("+12W", item.return12W)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Color(0xFF1E293B),
        modifier = modifier
    )
}

@Composable
private fun ReturnBadge(label: String, ret: Double?) {
    val text = if (ret != null) "${if (ret >= 0) "+" else ""}${ret.format2()}%" else "-"
    val color = if (ret != null && ret >= 0) BullGreen else BearRed

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
