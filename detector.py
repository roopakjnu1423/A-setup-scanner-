"""
Pure function detector for the A+ Weekly Stock Setup.
Implements exact rules from the sketch:
1. BASE: 3-5 week tight consolidation above rising 20 EMA
2. BREAKOUT: strong green candle closing above base high with >= 2x volume
3. FIRST PULLBACK: first touch of 20 EMA after breakout with volume dry-up
4. STATUS & TRADE MATH: WATCH, TOUCH, BOUNCE with Stop, Target, Risk %, RR, and A+ Score.
"""
from dataclasses import dataclass
from typing import Dict, List, Optional
import pandas as pd
import numpy as np

from config import ScreenerConfig, DEFAULT_CONFIG

@dataclass
class SetupResult:
    symbol: str
    exchange: str
    status: str                         # 'TOUCH', 'BOUNCE', or 'WATCH'
    cmp: float                          # Current Market Price (latest close)
    pct_from_ema: float                 # % distance from 20 EMA
    base_length: int                    # 3, 4, or 5 weeks
    base_depth_pct: float               # (base_high - base_low) / base_high
    breakout_date: str                  # Date of breakout week B
    breakout_gain_pct: float            # (Close - Open) / Open
    volume_mult: float                  # Breakout volume / prior 20-week avg
    touch_date: Optional[str]           # Date of pullback touch T
    weeks_to_pullback: Optional[int]    # T - B weeks
    vol_dryup_pct: Optional[float]      # Avg pullback vol / breakout vol
    entry_price: float
    stop_loss: float
    target_swing_high: float            # Highest high since breakout
    target_3r: float                    # Entry + 3 * (Entry - Stop)
    risk_pct: float                     # (Entry - Stop) / Entry
    rr_ratio: float                     # Risk-Reward ratio
    score: int                          # Overall A+ score (0-100)
    sub_scores: Dict[str, int]          # Component breakdown
    chart_data: Optional[Dict] = None   # Index markers for visualization

def score_setup(
    base_depth_pct: float,
    volume_mult: float,
    ema_slope_pct: float,
    vol_dryup_pct: float,
    risk_pct: float
) -> tuple[int, Dict[str, int]]:
    """
    Compute 0-100 A+ score and sub-scores.
    """
    # 1. Base Tightness (0-25): <= 4% = 25 pts, <= 7% = 20 pts, <= 10% = 15 pts
    if base_depth_pct <= 0.04:
        s_base = 25
    elif base_depth_pct <= 0.07:
        s_base = 20
    else:
        s_base = max(10, int(25 - (base_depth_pct - 0.04) * 250))
    s_base = min(25, max(0, s_base))

    # 2. Volume Multiple (0-25): >= 4x = 25, 3x = 22, 2x = 15
    if volume_mult >= 4.0:
        s_vol = 25
    elif volume_mult >= 3.0:
        s_vol = 22
    elif volume_mult >= 2.0:
        s_vol = 15 + int((volume_mult - 2.0) * 7)
    else:
        s_vol = max(0, int(volume_mult * 7.5))
    s_vol = min(25, max(0, s_vol))

    # 3. EMA Slope (0-20): slope over 4 weeks >= 4% = 20 pts, >= 2% = 15 pts, >0% = 10 pts
    if ema_slope_pct >= 0.04:
        s_ema = 20
    elif ema_slope_pct >= 0.02:
        s_ema = 16
    elif ema_slope_pct > 0:
        s_ema = 12
    else:
        s_ema = 0
    s_ema = min(20, max(0, s_ema))

    # 4. Volume Dry-up (0-15): <= 35% = 15, <= 50% = 12, <= 70% = 8
    if vol_dryup_pct <= 0.35:
        s_dryup = 15
    elif vol_dryup_pct <= 0.50:
        s_dryup = 12
    elif vol_dryup_pct <= 0.70:
        s_dryup = 8
    else:
        s_dryup = 4
    s_dryup = min(15, max(0, s_dryup))

    # 5. Risk Size (0-15): risk <= 2.5% = 15, <= 4.5% = 12, <= 7% = 8
    if risk_pct <= 0.025:
        s_risk = 15
    elif risk_pct <= 0.045:
        s_risk = 12
    elif risk_pct <= 0.070:
        s_risk = 8
    else:
        s_risk = 3
    s_risk = min(15, max(0, s_risk))

    total = s_base + s_vol + s_ema + s_dryup + s_risk
    sub_scores = {
        "Base Tightness": s_base,
        "Volume Multiple": s_vol,
        "EMA Slope": s_ema,
        "Volume Dry-Up": s_dryup,
        "Risk Size": s_risk
    }
    return total, sub_scores

def detect_setup(
    df: pd.DataFrame,
    symbol: str = "TICKER",
    exchange: str = "NSE",
    config: ScreenerConfig = DEFAULT_CONFIG
) -> Optional[SetupResult]:
    """
    Pure function to scan weekly OHLCV dataframe for A+ setup.
    Requirements:
    - At least 25 bars for EMA20 and 20-week volume average
    - No look-ahead
    """
    if len(df) < 25:
        return None

    # Ensure EMA20 and VolMA20 are present
    closes = df["Close"].values
    opens = df["Open"].values
    highs = df["High"].values
    lows = df["Low"].values
    volumes = df["Volume"].values
    ema20 = df["EMA20"].values

    # Prior 20-week volume average calculation
    vol_ma20 = df["VolMA20"].values

    dates = [d.strftime("%Y-%m-%d") if hasattr(d, "strftime") else str(d) for d in df.index]
    N = len(df) - 1  # latest bar index

    # We search for potential breakout weeks B in the last scan_lookback_weeks
    min_b = max(24, N - config.scan_lookback_weeks)

    best_setup = None

    # Scan from newest to oldest breakout candidate
    for B in range(N, min_b - 1, -1):
        # 1. Slope check: EMA20 at B > EMA20 four weeks earlier (B - config.ema_slope_weeks)
        if B < config.ema_slope_weeks:
            continue
        ema_b = ema20[B]
        ema_prev = ema20[B - config.ema_slope_weeks]
        if ema_b <= ema_prev:
            continue
        ema_slope_pct = (ema_b - ema_prev) / ema_prev

        # 2. Test base lengths 3, 4, 5 ending at B-1
        valid_base = None
        for base_len in [3, 4, 5]:
            base_start = B - base_len
            if base_start < 0:
                continue

            base_highs = highs[base_start:B]
            base_lows = lows[base_start:B]
            base_closes = closes[base_start:B]
            base_emas = ema20[base_start:B]

            max_b_high = np.max(base_highs)
            min_b_low = np.min(base_lows)

            # Base depth check: (max high - min low) / max high <= base_max_depth_pct
            depth = (max_b_high - min_b_low) / max_b_high
            if depth > config.base_max_depth_pct:
                continue

            # Base closes >= EMA20
            if np.any(base_closes < base_emas):
                continue

            # Base lows >= 0.98 * EMA20
            if np.any(base_lows < base_emas * config.base_low_ema_ratio):
                continue

            # Last base close <= 1.10 * EMA20
            last_base_close = base_closes[-1]
            last_base_ema = base_emas[-1]
            if last_base_close > last_base_ema * config.base_last_close_ema_ratio:
                continue

            valid_base = {
                "length": base_len,
                "start": base_start,
                "high": max_b_high,
                "low": min_b_low,
                "depth": depth
            }
            break  # Pick the first valid base length

        if not valid_base:
            continue

        # 3. Breakout week B checks
        close_b = closes[B]
        open_b = opens[B]
        high_b = highs[B]
        low_b = lows[B]
        vol_b = volumes[B]

        # Breakout close > base high
        if close_b <= valid_base["high"]:
            continue

        # Green candle (close > open)
        if close_b <= open_b:
            continue

        # (close - open) / open >= 4%
        gain_pct = (close_b - open_b) / open_b
        if gain_pct < config.breakout_min_gain_pct:
            continue

        # Close in top 30% of range: (high - close) / (high - low) <= 0.30
        range_b = high_b - low_b
        if range_b > 0:
            tail_pct = (high_b - close_b) / range_b
            if tail_pct > config.breakout_top_range_pct:
                continue

        # Breakout volume >= 2x prior 20-week average volume
        prior_vol_avg = vol_ma20[B - 1]
        if np.isnan(prior_vol_avg) or prior_vol_avg <= 0:
            continue
        vol_mult = vol_b / prior_vol_avg
        if vol_mult < config.breakout_vol_mult:
            continue

        # 4. Post-breakout checks from B+1 to N
        # Reject if ANY post-breakout close is below the base low
        post_breakout_closes = closes[B + 1: N + 1]
        if len(post_breakout_closes) > 0 and np.any(post_breakout_closes < valid_base["low"]):
            continue

        # Search for first pullback week T where low <= EMA20 * 1.02
        T = None
        for i in range(B + 1, N + 1):
            if lows[i] <= ema20[i] * config.pullback_touch_ratio:
                T = i
                break

        # Check for status
        status = None
        touch_date = None
        weeks_to_pullback = None
        vol_dryup = 0.5

        if T is None:
            # Check WATCH status: no touch yet, but current close within 5% above EMA20
            # Must not be below EMA20
            cmp = closes[N]
            ema_n = ema20[N]
            pct_from_ema = (cmp - ema_n) / ema_n
            if 0.0 <= pct_from_ema <= config.watch_buffer_pct:
                status = "WATCH"
                entry = cmp
                stop = ema_n * (1.0 - config.stop_buffer_pct)
            else:
                continue
        else:
            # T exists. Check constraints on T
            # Reject if close(T) < EMA20 * 0.99
            if closes[T] < ema20[T] * config.pullback_close_floor_ratio:
                continue

            # Reject if T - B > 10 weeks
            weeks_to_pullback = T - B
            if weeks_to_pullback > config.pullback_max_weeks:
                continue

            # Avg volume B+1..T <= 70% of breakout volume
            pullback_vols = volumes[B + 1: T + 1]
            avg_pb_vol = np.mean(pullback_vols)
            vol_dryup = avg_pb_vol / vol_b
            if vol_dryup > config.pullback_vol_dryup_ratio:
                continue

            touch_date = dates[T]

            # Check if TOUCH or BOUNCE
            if T == N:
                status = "TOUCH"
                entry = closes[N]
                stop = min(lows[T], ema20[T]) * (1.0 - config.stop_buffer_pct)
            elif N == T + 1:
                # BOUNCE: latest week = T+1 and closes above T high
                if closes[N] > highs[T]:
                    status = "BOUNCE"
                    entry = closes[N]
                    stop = min(lows[T], ema20[T]) * (1.0 - config.stop_buffer_pct)
                else:
                    continue
            else:
                # Anything older than T+1 is stale
                continue

        if status is None:
            continue

        # 5. Trade Math and Risk / RR checks
        cmp = closes[N]
        pct_from_ema = (cmp - ema20[N]) / ema20[N]
        risk_pct = (entry - stop) / entry

        # Filter: Risk <= 7%
        if risk_pct > config.max_risk_pct or risk_pct <= 0:
            continue

        # Target: highest high since B, plus 3R
        highest_since_b = np.max(highs[B: N + 1])
        target_3r = entry + config.target_r_multiple * (entry - stop)
        
        # Risk-Reward: using target 3R gives 3.0, but to swing high target:
        rr_to_swing_high = (highest_since_b - entry) / (entry - stop)
        # Target level must provide at least min_rr
        if config.target_r_multiple < config.min_rr:
            continue

        score, sub_scores = score_setup(
            base_depth_pct=valid_base["depth"],
            volume_mult=vol_mult,
            ema_slope_pct=ema_slope_pct,
            vol_dryup_pct=vol_dryup,
            risk_pct=risk_pct
        )

        chart_markers = {
            "base_start_idx": valid_base["start"],
            "base_end_idx": B - 1,
            "breakout_idx": B,
            "touch_idx": T,
            "latest_idx": N
        }

        setup = SetupResult(
            symbol=symbol,
            exchange=exchange,
            status=status,
            cmp=round(float(cmp), 2),
            pct_from_ema=round(float(pct_from_ema * 100), 2),
            base_length=valid_base["length"],
            base_depth_pct=round(float(valid_base["depth"] * 100), 2),
            breakout_date=dates[B],
            breakout_gain_pct=round(float(gain_pct * 100), 2),
            volume_mult=round(float(vol_mult), 2),
            touch_date=touch_date,
            weeks_to_pullback=weeks_to_pullback,
            vol_dryup_pct=round(float(vol_dryup * 100), 2) if vol_dryup else None,
            entry_price=round(float(entry), 2),
            stop_loss=round(float(stop), 2),
            target_swing_high=round(float(highest_since_b), 2),
            target_3r=round(float(target_3r), 2),
            risk_pct=round(float(risk_pct * 100), 2),
            rr_ratio=round(float(config.target_r_multiple), 1),
            score=score,
            sub_scores=sub_scores,
            chart_data=chart_markers
        )

        # If multiple valid setups, keep the one with the highest score
        if best_setup is None or setup.score > best_setup.score:
            best_setup = setup

    return best_setup
