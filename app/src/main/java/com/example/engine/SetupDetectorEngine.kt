package com.example.engine

import com.example.model.Bar
import com.example.model.ScreenerParameters
import com.example.model.SetupCandidate
import com.example.model.SetupStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object SetupDetectorEngine {

    /**
     * Compute 20-week EMA matching pandas ewm(span=20, adjust=False).
     * S_0 = Y_0
     * S_t = alpha * Y_t + (1 - alpha) * S_{t-1}, where alpha = 2 / (20 + 1)
     */
    fun computeIndicators(rawBars: List<Bar>): List<Bar> {
        if (rawBars.isEmpty()) return emptyList()

        val span = 20.0
        val alpha = 2.0 / (span + 1.0)
        val enriched = ArrayList<Bar>(rawBars.size)

        var currentEma = rawBars[0].close
        for (i in rawBars.indices) {
            val b = rawBars[i]
            currentEma = if (i == 0) b.close else (alpha * b.close + (1.0 - alpha) * currentEma)

            // Rolling 20 volume MA
            val windowStart = max(0, i - 19)
            var volSum = 0.0
            var count = 0
            for (w in windowStart..i) {
                volSum += rawBars[w].volume
                count++
            }
            val volMa = if (count > 0) volSum / count else b.volume

            enriched.add(
                b.copy(
                    ema20 = currentEma,
                    volMa20 = volMa
                )
            )
        }
        return enriched
    }

    fun detectSetup(
        bars: List<Bar>,
        symbol: String,
        exchange: String = "NSE",
        company: String = "",
        params: ScreenerParameters = ScreenerParameters()
    ): SetupCandidate? {
        if (bars.size < 25) return null

        val enrichedBars = if (bars[0].ema20 == 0.0) computeIndicators(bars) else bars
        val N = enrichedBars.size - 1
        val minB = max(24, N - 16)

        var bestCandidate: SetupCandidate? = null

        // Scan breakout week B candidate
        for (B in N downTo minB) {
            // 1. Rising EMA20 check: EMA20[B] > EMA20[B - 4]
            if (B < 4) continue
            val emaB = enrichedBars[B].ema20
            val emaB4 = enrichedBars[B - 4].ema20
            if (emaB <= emaB4) continue
            val emaSlopePct = (emaB - emaB4) / emaB4

            // 2. Test base lengths 3, 4, 5 ending at B - 1
            var validBaseLength: Int? = null
            var validBaseStart: Int = 0
            var baseHigh: Double = 0.0
            var baseLow: Double = Double.MAX_VALUE
            var baseDepth: Double = 0.0

            for (baseLen in listOf(3, 4, 5)) {
                val start = B - baseLen
                if (start < 0) continue

                var maxH = 0.0
                var minL = Double.MAX_VALUE
                var satisfiesConditions = true

                for (w in start until B) {
                    val bar = enrichedBars[w]
                    if (bar.high > maxH) maxH = bar.high
                    if (bar.low < minL) minL = bar.low

                    // Base closes >= EMA20
                    if (bar.close < bar.ema20) {
                        satisfiesConditions = false
                        break
                    }
                    // Base lows >= 0.98 * EMA20
                    if (bar.low < bar.ema20 * 0.98) {
                        satisfiesConditions = false
                        break
                    }
                }

                if (!satisfiesConditions) continue

                // Last base close <= 1.10 * EMA20
                val lastBaseBar = enrichedBars[B - 1]
                if (lastBaseBar.close > lastBaseBar.ema20 * 1.10) continue

                val depth = if (maxH > 0) (maxH - minL) / maxH else 1.0
                if (depth * 100.0 > params.baseMaxDepthPct) continue

                validBaseLength = baseLen
                validBaseStart = start
                baseHigh = maxH
                baseLow = minL
                baseDepth = depth
                break
            }

            if (validBaseLength == null) continue

            // 3. Breakout Candle (B) Verification
            val barB = enrichedBars[B]
            if (barB.close <= baseHigh) continue
            if (barB.close <= barB.open) continue // Green candle

            val gainPct = (barB.close - barB.open) / barB.open
            if (gainPct * 100.0 < params.breakoutMinGainPct) continue

            val rangeB = barB.high - barB.low
            if (rangeB > 0) {
                val upperTailPct = (barB.high - barB.close) / rangeB
                if (upperTailPct > 0.30) continue // Must close in top 30% of range
            }

            val priorVolAvg = enrichedBars[B - 1].volMa20
            if (priorVolAvg <= 0) continue
            val volMult = barB.volume / priorVolAvg
            if (volMult < params.breakoutVolMult) continue

            // 4. Post-breakout constraints from B+1 to N
            var baseLowViolated = false
            for (p in (B + 1)..N) {
                if (enrichedBars[p].close < baseLow) {
                    baseLowViolated = true
                    break
                }
            }
            if (baseLowViolated) continue

            // Search for first touch week T (low <= pullbackTouchRatio * EMA20)
            var T: Int? = null
            for (p in (B + 1)..N) {
                if (enrichedBars[p].low <= enrichedBars[p].ema20 * params.pullbackTouchRatio) {
                    T = p
                    break
                }
            }

            var status: SetupStatus? = null
            var touchDate: String? = null
            var weeksToPullback: Int? = null
            var volDryup = 0.5
            var entryPrice = enrichedBars[N].close
            var stopLoss = 0.0

            if (T == null) {
                // WATCH: no touch yet, close within watchBufferPct (5%) above EMA20
                val cmp = enrichedBars[N].close
                val emaN = enrichedBars[N].ema20
                val pctFromEma = (cmp - emaN) / emaN
                if (pctFromEma in 0.0..params.watchBufferPct) {
                    status = SetupStatus.WATCH
                    stopLoss = emaN * (1.0 - params.stopBufferPct)
                } else {
                    continue
                }
            } else {
                // Constraints on T
                if (enrichedBars[T].close < enrichedBars[T].ema20 * params.pullbackCloseFloorRatio) {
                    continue
                }

                weeksToPullback = T - B
                if (weeksToPullback > params.pullbackMaxWeeks) continue

                // Volume dry-up check
                var pbVolSum = 0.0
                for (p in (B + 1)..T) {
                    pbVolSum += enrichedBars[p].volume
                }
                val avgPbVol = pbVolSum / (T - B)
                volDryup = avgPbVol / barB.volume
                if (volDryup > params.pullbackVolDryupRatio) continue

                touchDate = enrichedBars[T].date

                // Check status
                if (T == N) {
                    status = SetupStatus.TOUCH
                    stopLoss = min(enrichedBars[T].low, enrichedBars[T].ema20) * (1.0 - params.stopBufferPct)
                } else if (N == T + 1 && enrichedBars[N].close > enrichedBars[T].high) {
                    status = SetupStatus.BOUNCE
                    stopLoss = min(enrichedBars[T].low, enrichedBars[T].ema20) * (1.0 - params.stopBufferPct)
                } else {
                    // Stale
                    continue
                }
            }

            if (status == null) continue

            // 5. Trade Math & Risk Check
            val cmp = enrichedBars[N].close
            val pctFromEma = ((cmp - enrichedBars[N].ema20) / enrichedBars[N].ema20) * 100.0
            val riskPct = ((entryPrice - stopLoss) / entryPrice) * 100.0

            if (riskPct > params.maxRiskPct || riskPct <= 0) continue

            // Targets
            var highestSinceB = 0.0
            for (p in B..N) {
                if (enrichedBars[p].high > highestSinceB) highestSinceB = enrichedBars[p].high
            }
            val target3R = entryPrice + 3.0 * (entryPrice - stopLoss)
            val rrRatio = 3.0

            if (rrRatio < params.minRR) continue

            // Score setup
            val (score, subScores) = calculateScore(
                baseDepthPct = baseDepth,
                volumeMult = volMult,
                emaSlopePct = emaSlopePct,
                volDryupRatio = volDryup,
                riskPct = riskPct / 100.0
            )

            val candidate = SetupCandidate(
                symbol = symbol,
                exchange = exchange,
                company = company,
                status = status,
                cmp = (cmp * 100).roundToInt() / 100.0,
                pctFromEma = (pctFromEma * 100).roundToInt() / 100.0,
                baseLength = validBaseLength,
                baseDepthPct = ((baseDepth * 100.0) * 100).roundToInt() / 100.0,
                breakoutDate = enrichedBars[B].date,
                breakoutGainPct = ((gainPct * 100.0) * 100).roundToInt() / 100.0,
                volumeMult = (volMult * 100).roundToInt() / 100.0,
                touchDate = touchDate,
                weeksToPullback = weeksToPullback,
                volDryupPct = ((volDryup * 100.0) * 100).roundToInt() / 100.0,
                entryPrice = (entryPrice * 100).roundToInt() / 100.0,
                stopLoss = (stopLoss * 100).roundToInt() / 100.0,
                targetSwingHigh = (highestSinceB * 100).roundToInt() / 100.0,
                target3R = (target3R * 100).roundToInt() / 100.0,
                riskPct = (riskPct * 100).roundToInt() / 100.0,
                rrRatio = rrRatio,
                score = score,
                subScores = subScores,
                weeklyBars = enrichedBars,
                baseStartIndex = validBaseStart,
                baseEndIndex = B - 1,
                breakoutIndex = B,
                touchIndex = T
            )

            if (bestCandidate == null || candidate.score > bestCandidate.score) {
                bestCandidate = candidate
            }
        }

        return bestCandidate
    }

    private fun calculateScore(
        baseDepthPct: Double,
        volumeMult: Double,
        emaSlopePct: Double,
        volDryupRatio: Double,
        riskPct: Double
    ): Pair<Int, Map<String, Int>> {
        val sBase = when {
            baseDepthPct <= 0.04 -> 25
            baseDepthPct <= 0.07 -> 20
            else -> max(10, (25 - (baseDepthPct - 0.04) * 250).toInt()).coerceIn(0, 25)
        }

        val sVol = when {
            volumeMult >= 4.0 -> 25
            volumeMult >= 3.0 -> 22
            volumeMult >= 2.0 -> (15 + (volumeMult - 2.0) * 7).toInt().coerceIn(0, 25)
            else -> (volumeMult * 7.5).toInt().coerceIn(0, 25)
        }

        val sEma = when {
            emaSlopePct >= 0.04 -> 20
            emaSlopePct >= 0.02 -> 16
            emaSlopePct > 0 -> 12
            else -> 0
        }

        val sDryup = when {
            volDryupRatio <= 0.35 -> 15
            volDryupRatio <= 0.50 -> 12
            volDryupRatio <= 0.70 -> 8
            else -> 4
        }

        val sRisk = when {
            riskPct <= 0.025 -> 15
            riskPct <= 0.045 -> 12
            riskPct <= 0.070 -> 8
            else -> 3
        }

        val total = sBase + sVol + sEma + sDryup + sRisk
        val subMap = mapOf(
            "Base Tightness" to sBase,
            "Volume Multiple" to sVol,
            "EMA Slope" to sEma,
            "Volume Dry-Up" to sDryup,
            "Risk Size" to sRisk
        )
        return Pair(total, subMap)
    }
}
