package com.example.model

enum class SetupStatus {
    TOUCH,
    BOUNCE,
    WATCH
}

data class Bar(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val ema20: Double = 0.0,
    val volMa20: Double = 0.0
)

data class SetupCandidate(
    val symbol: String = "",
    val exchange: String = "NSE",
    val company: String = "",
    val status: SetupStatus = SetupStatus.TOUCH,
    val cmp: Double = 0.0,
    val pctFromEma: Double = 0.0,
    val baseLength: Int = 4,
    val baseDepthPct: Double = 5.0,
    val breakoutDate: String = "",
    val breakoutGainPct: Double = 5.0,
    val volumeMult: Double = 2.5,
    val touchDate: String? = null,
    val weeksToPullback: Int? = null,
    val volDryupPct: Double? = null,
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val targetSwingHigh: Double = 0.0,
    val target3R: Double = 0.0,
    val riskPct: Double = 3.5,
    val rrRatio: Double = 3.0,
    val score: Int = 85,
    val subScores: Map<String, Int> = emptyMap(),
    val weeklyBars: List<Bar> = emptyList(),
    val baseStartIndex: Int = 0,
    val baseEndIndex: Int = 0,
    val breakoutIndex: Int = 0,
    val touchIndex: Int? = null
)

data class ScreenerParameters(
    val universe: String = "Nifty 500",
    val baseMaxDepthPct: Double = 10.0,
    val breakoutMinGainPct: Double = 4.0,
    val breakoutVolMult: Double = 2.0,
    val pullbackMaxWeeks: Int = 10,
    val pullbackTouchRatio: Double = 1.02,
    val pullbackCloseFloorRatio: Double = 0.99,
    val pullbackVolDryupRatio: Double = 0.70,
    val watchBufferPct: Double = 0.05,
    val stopBufferPct: Double = 0.01,
    val maxRiskPct: Double = 7.0,
    val minRR: Double = 2.0,
    val includeRunningWeek: Boolean = false,
    val minPrice: Double = 20.0,
    val minTradedValueCr: Double = 2.0
)

data class BacktestReplayItem(
    val symbol: String,
    val status: SetupStatus,
    val asOfDate: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val return4W: Double?,
    val return8W: Double?,
    val return12W: Double?,
    val hitStop: Boolean,
    val hitTarget: Boolean
)
