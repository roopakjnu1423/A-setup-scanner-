"""
Unit tests for the A+ Setup Detector using synthetic weekly market data.
Verifies sketch detection and rejections for:
1. Loose base (> 10% depth)
2. Low volume breakout (< 2x)
3. Falling EMA (EMA at B <= EMA 4 weeks prior)
4. Stale / second pullback after initial bounce
"""
import pandas as pd
import numpy as np
import pytest

from config import ScreenerConfig
from detector import detect_setup
from indicators import calculate_ema

def create_synthetic_series(
    base_depth_pct: float = 0.05,
    breakout_vol_mult: float = 3.0,
    breakout_gain_pct: float = 0.06,
    rising_ema: bool = True,
    pullback_weeks: int = 2,
    second_touch: bool = False
) -> pd.DataFrame:
    """
    Construct a synthetic 35-week weekly OHLCV series mimicking the sketch.
    Weeks 0..19: uptrend to build rising 20 EMA
    Weeks 20..23: 4-week base
    Week 24: Breakout
    Weeks 25..25+pullback_weeks: Pullback to 20 EMA
    """
    n_weeks = 25 + pullback_weeks + (3 if second_touch else 0)
    dates = pd.date_range(start="2024-01-05", periods=n_weeks, freq="W-FRI")

    closes = np.zeros(n_weeks)
    opens = np.zeros(n_weeks)
    highs = np.zeros(n_weeks)
    lows = np.zeros(n_weeks)
    volumes = np.ones(n_weeks) * 100_000.0

    # Build early trend so EMA20 rises
    price = 100.0
    for i in range(20):
        if rising_ema:
            price += 1.5
        else:
            price -= 0.5
        closes[i] = price
        opens[i] = price - 0.5
        highs[i] = price + 1.0
        lows[i] = price - 1.0

    # Base at weeks 20, 21, 22, 23 (4-week base)
    base_price = closes[19]
    for i in range(20, 24):
        closes[i] = base_price + 0.5
        opens[i] = base_price
        # Adjust high/low according to base_depth_pct
        highs[i] = base_price * (1.0 + base_depth_pct / 2.0)
        lows[i] = base_price * (1.0 - base_depth_pct / 2.0)
        volumes[i] = 100_000.0

    # Breakout at week 24
    b_open = base_price
    b_close = b_open * (1.0 + breakout_gain_pct)
    b_high = b_close * 1.002
    b_low = b_open * 0.998
    opens[24] = b_open
    closes[24] = b_close
    highs[24] = b_high
    lows[24] = b_low
    volumes[24] = 100_000.0 * breakout_vol_mult

    # Pullback weeks
    curr_price = b_close
    for k in range(1, pullback_weeks + 1):
        idx = 24 + k
        curr_price = curr_price * 0.975
        opens[idx] = curr_price + 1.0
        closes[idx] = curr_price
        highs[idx] = curr_price + 2.0
        lows[idx] = curr_price - 1.0
        volumes[idx] = 40_000.0  # Dry-up volume (40% of normal, << breakout)

    # Let's ensure the last pullback bar touches the EMA
    df_temp = pd.DataFrame({"Close": closes})
    ema = calculate_ema(df_temp["Close"], span=20).values

    touch_idx = 24 + pullback_weeks
    # Position low at touch_idx exactly touching the EMA20
    lows[touch_idx] = ema[touch_idx] * 1.005
    closes[touch_idx] = ema[touch_idx] * 1.01
    opens[touch_idx] = ema[touch_idx] * 1.03
    highs[touch_idx] = ema[touch_idx] * 1.04

    df = pd.DataFrame({
        "Open": opens,
        "High": highs,
        "Low": lows,
        "Close": closes,
        "Volume": volumes
    }, index=dates)

    # Add indicators
    df["EMA20"] = calculate_ema(df["Close"], span=20)
    df["VolMA20"] = df["Volume"].rolling(20, min_periods=5).mean()

    # Re-align base lows and closes to strictly satisfy EMA conditions
    for i in range(20, 24):
        if df["Close"].iloc[i] < df["EMA20"].iloc[i]:
            df.loc[dates[i], "Close"] = df["EMA20"].iloc[i] * 1.02
        if df.loc[dates[i], "Low"] < df["EMA20"].iloc[i] * 0.99:
            df.loc[dates[i], "Low"] = df["EMA20"].iloc[i] * 0.995

    return df

def test_perfect_sketch_setup():
    """Verify that a synthetic series perfectly matching the sketch is detected as TOUCH."""
    df = create_synthetic_series(
        base_depth_pct=0.05,
        breakout_vol_mult=3.0,
        breakout_gain_pct=0.06,
        rising_ema=True,
        pullback_weeks=2
    )
    result = detect_setup(df, symbol="RELIANCE", exchange="NSE")
    assert result is not None
    assert result.status in ["TOUCH", "WATCH"]
    assert result.volume_mult >= 2.0
    assert result.base_depth_pct <= 10.0
    assert result.score > 60

def test_loose_base_rejected():
    """Verify that a base with depth > 10% is rejected."""
    df = create_synthetic_series(base_depth_pct=0.18)
    config = ScreenerConfig(base_max_depth_pct=0.10)
    result = detect_setup(df, symbol="TEST", exchange="NSE", config=config)
    assert result is None

def test_low_volume_breakout_rejected():
    """Verify that a breakout candle with < 2x volume is rejected."""
    df = create_synthetic_series(breakout_vol_mult=1.3)
    config = ScreenerConfig(breakout_vol_mult=2.0)
    result = detect_setup(df, symbol="TEST", exchange="NSE", config=config)
    assert result is None

def test_falling_ema_rejected():
    """Verify that setups with a non-rising 20 EMA are rejected."""
    df = create_synthetic_series(rising_ema=False)
    result = detect_setup(df, symbol="TEST", exchange="NSE")
    assert result is None

def test_stale_second_pullback_rejected():
    """Verify that stale setups (older than T+1) are rejected."""
    df = create_synthetic_series(pullback_weeks=2)
    # Add 3 more arbitrary weeks after the touch
    extra_dates = pd.date_range(start=df.index[-1] + pd.Timedelta(days=7), periods=3, freq="W-FRI")
    extra_df = pd.DataFrame({
        "Open": [df["Close"].iloc[-1] + 1] * 3,
        "High": [df["Close"].iloc[-1] + 2] * 3,
        "Low": [df["Close"].iloc[-1] - 1] * 3,
        "Close": [df["Close"].iloc[-1] + 1] * 3,
        "Volume": [50_000.0] * 3,
    }, index=extra_dates)
    full_df = pd.concat([df, extra_df])
    full_df["EMA20"] = calculate_ema(full_df["Close"], span=20)
    full_df["VolMA20"] = full_df["Volume"].rolling(20, min_periods=5).mean()

    result = detect_setup(full_df, symbol="TEST", exchange="NSE")
    # Must be None because T is older than T+1
    assert result is None
