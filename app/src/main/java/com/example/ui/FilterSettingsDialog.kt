package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ScreenerParameters
import com.example.ui.theme.BullGreen
import com.example.ui.theme.SkyBlue
import kotlin.math.roundToInt

@Composable
fun FilterSettingsDialog(
    initialParams: ScreenerParameters,
    onApply: (ScreenerParameters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var universe by remember { mutableStateOf(initialParams.universe) }
    var baseDepth by remember { mutableDoubleStateOf(initialParams.baseMaxDepthPct) }
    var breakoutGain by remember { mutableDoubleStateOf(initialParams.breakoutMinGainPct) }
    var volMult by remember { mutableDoubleStateOf(initialParams.breakoutVolMult) }
    var maxWeeks by remember { mutableIntStateOf(initialParams.pullbackMaxWeeks) }
    var touchRatio by remember { mutableDoubleStateOf(initialParams.pullbackTouchRatio) }
    var volDryup by remember { mutableDoubleStateOf(initialParams.pullbackVolDryupRatio * 100.0) }
    var maxRisk by remember { mutableDoubleStateOf(initialParams.maxRiskPct) }
    var minRR by remember { mutableDoubleStateOf(initialParams.minRR) }
    var includeRunning by remember { mutableStateOf(initialParams.includeRunningWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⚙️ Setup Detection Rules",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                // Universe Selector
                Text("Stock Universe", fontSize = 13.sp, color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Nifty 500", "All NSE EQ", "BSE").forEach { u ->
                        FilterChip(
                            selected = universe == u,
                            onClick = { universe = u },
                            label = { Text(u, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Max Base Depth
                SliderSetting(
                    title = "Max Base Depth (%)",
                    value = "${baseDepth.roundToInt()}%",
                    current = baseDepth.toFloat(),
                    valueRange = 5f..20f,
                    onValueChange = { baseDepth = it.toDouble() }
                )

                // Min Breakout Gain
                SliderSetting(
                    title = "Min Breakout Gain (%)",
                    value = "${breakoutGain.roundToInt()}%",
                    current = breakoutGain.toFloat(),
                    valueRange = 2f..10f,
                    onValueChange = { breakoutGain = it.toDouble() }
                )

                // Breakout Volume Multiple
                SliderSetting(
                    title = "Breakout Volume Multiple",
                    value = "${(volMult * 10).roundToInt() / 10.0}x",
                    current = volMult.toFloat(),
                    valueRange = 1.5f..5.0f,
                    onValueChange = { volMult = it.toDouble() }
                )

                // Max Weeks to Pullback
                SliderSetting(
                    title = "Max Weeks to Pullback (T - B)",
                    value = "$maxWeeks weeks",
                    current = maxWeeks.toFloat(),
                    valueRange = 4f..16f,
                    onValueChange = { maxWeeks = it.roundToInt() }
                )

                // Pullback Touch Ratio
                SliderSetting(
                    title = "Pullback Touch Ratio (x 20 EMA)",
                    value = "${(touchRatio * 100).roundToInt() / 100.0}x",
                    current = touchRatio.toFloat(),
                    valueRange = 1.00f..1.05f,
                    onValueChange = { touchRatio = it.toDouble() }
                )

                // Pullback Volume Dry-Up
                SliderSetting(
                    title = "Max Pullback Vol Dry-Up (%)",
                    value = "${volDryup.roundToInt()}%",
                    current = volDryup.toFloat(),
                    valueRange = 40f..100f,
                    onValueChange = { volDryup = it.toDouble() }
                )

                // Max Allowed Risk
                SliderSetting(
                    title = "Max Allowed Risk %",
                    value = "${maxRisk.roundToInt()}%",
                    current = maxRisk.toFloat(),
                    valueRange = 3f..12f,
                    onValueChange = { maxRisk = it.toDouble() }
                )

                // Min Risk-Reward Ratio
                SliderSetting(
                    title = "Minimum Risk : Reward",
                    value = "1:${(minRR * 10).roundToInt() / 10.0}",
                    current = minRR.toFloat(),
                    valueRange = 1.5f..4.0f,
                    onValueChange = { minRR = it.toDouble() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Running Week Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Include Running Week", fontSize = 13.sp, color = Color.White)
                        Text("Default uses completed weekly bars", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    Switch(
                        checked = includeRunning,
                        onCheckedChange = { includeRunning = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        initialParams.copy(
                            universe = universe,
                            baseMaxDepthPct = baseDepth,
                            breakoutMinGainPct = breakoutGain,
                            breakoutVolMult = volMult,
                            pullbackMaxWeeks = maxWeeks,
                            pullbackTouchRatio = touchRatio,
                            pullbackVolDryupRatio = volDryup / 100.0,
                            maxRiskPct = maxRisk,
                            minRR = minRR,
                            includeRunningWeek = includeRunning
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                modifier = Modifier.testTag("apply_filters_button")
            ) {
                Text("Apply & Rescan")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF1E293B),
        modifier = modifier
    )
}

@Composable
private fun SliderSetting(
    title: String,
    value: String,
    current: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 12.sp, color = Color(0xFFCBD5E1))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BullGreen)
        }
        Slider(
            value = current,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}
