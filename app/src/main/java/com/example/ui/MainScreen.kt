package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MarketRepository
import com.example.model.BacktestReplayItem
import com.example.model.ScreenerParameters
import com.example.model.SetupCandidate
import com.example.model.SetupStatus
import com.example.ui.components.format2
import com.example.ui.theme.BearRed
import com.example.ui.theme.BullGreen
import com.example.ui.theme.EmaGold
import com.example.ui.theme.SkyBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { MarketRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var params by remember { mutableStateOf(ScreenerParameters()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgressText by remember { mutableStateOf("") }
    var setups by remember { mutableStateOf<List<SetupCandidate>>(emptyList()) }
    var selectedCandidate by remember { mutableStateOf<SetupCandidate?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<SetupStatus?>(null) }
    var sortBy by remember { mutableStateOf("Score") } // Score, Risk, RR, CMP

    var showFilterDialog by remember { mutableStateOf(false) }
    var showReplayDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun triggerScan(currentParams: ScreenerParameters) {
        coroutineScope.launch {
            isScanning = true
            scanProgressText = "Initializing..."
            try {
                val results = repository.runScan(currentParams) { cur, tot, sym ->
                    scanProgressText = "$cur / $tot ($sym)"
                }
                setups = results
                snackbarHostState.showSnackbar("Scan completed: found ${results.size} setups in ${currentParams.universe}")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Scan error: ${e.localizedMessage}")
            } finally {
                isScanning = false
                scanProgressText = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        triggerScan(params)
    }

    // Filter and Sort results
    val displayedSetups = setups
        .filter { candidate ->
            (statusFilter == null || candidate.status == statusFilter) &&
            (searchQuery.isBlank() || candidate.symbol.contains(searchQuery, ignoreCase = true) || candidate.company.contains(searchQuery, ignoreCase = true))
        }
        .let { list ->
            when (sortBy) {
                "Risk" -> list.sortedBy { it.riskPct }
                "RR" -> list.sortedByDescending { it.rrRatio }
                "CMP" -> list.sortedByDescending { it.cmp }
                else -> list.sortedByDescending { it.score }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SkyBlue.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Logo",
                                tint = SkyBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "A+ Setup Screener",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "${params.universe} (${repository.totalUniverseCount} Stocks) • Weekly 20 EMA",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Filter Dialog Button
                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("filter_icon_button")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Rules", tint = Color.White)
                    }
                    // Replay Dialog Button
                    IconButton(
                        onClick = { showReplayDialog = true },
                        modifier = Modifier.testTag("replay_icon_button")
                    ) {
                        Icon(Icons.Default.History, contentDescription = "Replay", tint = Color.White)
                    }
                    // Telegram Alert Button
                    IconButton(
                        onClick = { showTelegramDialog = true },
                        modifier = Modifier.testTag("telegram_icon_button")
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = Color.White)
                    }
                    // Export CSV Button
                    IconButton(
                        onClick = {
                            val csvData = repository.exportToCsv(displayedSetups)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvData)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export A+ Setups CSV"))
                        },
                        modifier = Modifier.testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { triggerScan(params) },
                icon = {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                },
                text = { Text(if (isScanning) scanProgressText.ifBlank { "Scanning..." } else "Scan Stocks") },
                containerColor = SkyBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("scan_fab")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0B132B),
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Metrics Ribbon Item
            item {
                SummaryRibbon(setups = setups)
            }

            // Search & Filter Bar Item
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by symbol (e.g. TRENT, HAL)...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedBorderColor = SkyBlue,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("ALL (${setups.size})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SkyBlue)
                        )
                        FilterChip(
                            selected = statusFilter == SetupStatus.TOUCH,
                            onClick = { statusFilter = SetupStatus.TOUCH },
                            label = { Text("TOUCH (${setups.count { it.status == SetupStatus.TOUCH }})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF38BDF8))
                        )
                        FilterChip(
                            selected = statusFilter == SetupStatus.BOUNCE,
                            onClick = { statusFilter = SetupStatus.BOUNCE },
                            label = { Text("BOUNCE (${setups.count { it.status == SetupStatus.BOUNCE }})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BullGreen)
                        )
                        FilterChip(
                            selected = statusFilter == SetupStatus.WATCH,
                            onClick = { statusFilter = SetupStatus.WATCH },
                            label = { Text("WATCH (${setups.count { it.status == SetupStatus.WATCH }})", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmaGold)
                        )
                    }
                }
            }

            // Results Count Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUALIFYING SETUPS (${displayedSetups.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort: ", fontSize = 11.sp, color = Color(0xFF64748B))
                        listOf("Score", "Risk", "RR").forEach { s ->
                            Text(
                                text = s,
                                fontSize = 11.sp,
                                fontWeight = if (sortBy == s) FontWeight.Bold else FontWeight.Normal,
                                color = if (sortBy == s) SkyBlue else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .clickable { sortBy = s }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            // Setup Candidate Cards
            if (displayedSetups.isEmpty() && !isScanning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No setups matching current criteria", color = Color(0xFF94A3B8), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Try adjusting thresholds in Rules or changing Universe", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(displayedSetups) { candidate ->
                    SetupCard(
                        candidate = candidate,
                        onClick = { selectedCandidate = candidate },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Mandatory Disclaimer Footer
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Educational screening tool, not investment advice.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Setup Detail Sheet
    selectedCandidate?.let { candidate ->
        SetupDetailSheet(
            candidate = candidate,
            sheetState = sheetState,
            onDismiss = { selectedCandidate = null }
        )
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterSettingsDialog(
            initialParams = params,
            onApply = { newParams ->
                params = newParams
                showFilterDialog = false
                triggerScan(newParams)
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Historical Replay Dialog
    if (showReplayDialog) {
        val replayItems = setups.map { repository.computeHistoricalReplay(it) }
        ReplayModeDialog(
            replayItems = replayItems,
            onDismiss = { showReplayDialog = false }
        )
    }

    // Telegram Alert Dialog
    if (showTelegramDialog) {
        TelegramAlertDialog(
            onSendAlert = { token, chat ->
                coroutineScope.launch {
                    val alertMessage = buildString {
                        append("<b>🚨 A+ Weekly Setup Alert (NSE / BSE)</b>\n\n")
                        setups.take(5).forEach { s ->
                            append("• <b>${s.symbol}</b> (${s.status}) - Score: ${s.score}/100\n")
                            append("  CMP: ₹${s.cmp} | Stop: ₹${s.stopLoss} (${s.riskPct}% risk)\n")
                            append("  Target 3R: ₹${s.target3R} | Breakout Vol: ${s.volumeMult}x\n\n")
                        }
                        append("<i>Educational screening tool, not investment advice.</i>")
                    }
                    val ok = repository.sendTelegramAlert(token, chat, alertMessage)
                    showTelegramDialog = false
                    snackbarHostState.showSnackbar(
                        if (ok) "Telegram alert dispatched successfully!" else "Failed to send alert. Check token & chat ID."
                    )
                }
            },
            onDismiss = { showTelegramDialog = false }
        )
    }
}

@Composable
private fun SummaryRibbon(setups: List<SetupCandidate>) {
    val total = setups.size
    val touches = setups.count { it.status == SetupStatus.TOUCH }
    val bounces = setups.count { it.status == SetupStatus.BOUNCE }
    val watch = setups.count { it.status == SetupStatus.WATCH }
    val avgScore = if (total > 0) setups.map { it.score }.average().toInt() else 0

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RibbonItem("QUALIFIED", "$total", SkyBlue)
            RibbonItem("TOUCH @ EMA", "$touches", Color(0xFF38BDF8))
            RibbonItem("BOUNCE", "$bounces", BullGreen)
            RibbonItem("WATCH", "$watch", EmaGold)
            RibbonItem("AVG SCORE", "$avgScore", if (avgScore >= 80) BullGreen else EmaGold)
        }
    }
}

@Composable
private fun RibbonItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun SetupCard(
    candidate: SetupCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("setup_card_${candidate.symbol}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Symbol, Exchange & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.symbol,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = candidate.exchange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (candidate.score >= 80) BullGreen.copy(alpha = 0.15f) else EmaGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Score: ${candidate.score}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (candidate.score >= 80) BullGreen else EmaGold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = candidate.status)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = candidate.company.ifBlank { "Indian Stock" },
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Key Trade Metrics Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("CMP", "₹${candidate.cmp.format2()}", Color.White)
                MetricColumn("Distance", "${candidate.pctFromEma.format2()}%", EmaGold)
                MetricColumn("Base", "${candidate.baseLength}W (${candidate.baseDepthPct}%)", Color(0xFFCBD5E1))
                MetricColumn("Breakout", "${candidate.volumeMult}x vol", BullGreen)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trade Math Row: Entry, Stop, Target, Risk %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Text("Entry: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("₹${candidate.entryPrice.format2()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SkyBlue)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Stop: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("₹${candidate.stopLoss.format2()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BearRed)
                }

                Row {
                    Text("Risk: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("${candidate.riskPct.format2()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BullGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("RR: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Text("1:${candidate.rrRatio.format2()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BullGreen)
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
