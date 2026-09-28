"""
Technical indicators and resampling module.
Converts daily OHLCV into Friday-ending weekly bars and computes EMA20 & volume averages.
"""
import pandas as pd
import numpy as np

def resample_to_weekly(df: pd.DataFrame, include_running_week: bool = False) -> pd.DataFrame:
    """
    Resample daily OHLCV dataframe to weekly bars ending on Friday ('W-FRI').
    Open: first, High: max, Low: min, Close: last, Volume: sum.
    Handles completed weeks only or includes current running week.
    """
    if df.empty:
        return pd.DataFrame()

    # Ensure index is datetime
    if not isinstance(df.index, pd.DatetimeIndex):
        df.index = pd.to_datetime(df.index)

    df_sorted = df.sort_index()

    weekly = df_sorted.resample("W-FRI").agg({
        "Open": "first",
        "High": "max",
        "Low": "min",
        "Close": "last",
        "Volume": "sum"
    }).dropna()

    if not include_running_week and len(weekly) > 0:
        # Check if the last bar is an incomplete / running week
        last_daily_date = df_sorted.index[-1]
        last_weekly_date = weekly.index[-1]
        # Friday is weekday 4 in Python (Monday is 0)
        # If last daily date is before the weekly end date or not Friday close, exclude it
        if last_daily_date < last_weekly_date and last_daily_date.weekday() != 4:
            weekly = weekly.iloc[:-1]

    return weekly

def calculate_ema(series: pd.Series, span: int = 20) -> pd.Series:
    """
    Exponential Moving Average matching pandas ewm(span=20, adjust=False).
    """
    return series.ewm(span=span, adjust=False).mean()

def compute_indicators(weekly_df: pd.DataFrame) -> pd.DataFrame:
    """
    Enriches weekly dataframe with EMA20 and 20-period moving average volume.
    No forward-looking leak.
    """
    df = weekly_df.copy()
    if df.empty:
        return df

    df["EMA20"] = calculate_ema(df["Close"], span=20)
    df["VolMA20"] = df["Volume"].rolling(window=20, min_periods=5).mean()
    return df
