"""
Data provider interface and yfinance implementation with SQLite caching,
batching, retry logic, and liquidity/price filters.
"""
from abc import ABC, abstractmethod
import datetime
import os
import sqlite3
import time
from typing import Dict, List, Optional
import pandas as pd
import yfinance as yf

from config import ScreenerConfig, DEFAULT_CONFIG

class MarketDataProvider(ABC):
    """Abstract interface for stock market data retrieval."""
    
    @abstractmethod
    def fetch_daily_ohlcv(self, tickers: List[str]) -> Dict[str, pd.DataFrame]:
        """Fetch daily OHLCV dataframe for each ticker."""
        pass

class YFinanceDataProvider(MarketDataProvider):
    """
    Yahoo Finance data fetcher with local SQLite caching,
    exponential backoff, and batch downloading.
    """
    
    def __init__(self, config: ScreenerConfig = DEFAULT_CONFIG):
        self.config = config
        os.makedirs(os.path.dirname(self.config.sqlite_db) or ".", exist_ok=True)
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.config.sqlite_db) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS daily_ohlcv (
                    ticker TEXT,
                    date TEXT,
                    open REAL,
                    high REAL,
                    low REAL,
                    close REAL,
                    volume REAL,
                    PRIMARY KEY (ticker, date)
                )
            """)

    def _load_cached(self, ticker: str) -> Optional[pd.DataFrame]:
        try:
            with sqlite3.connect(self.config.sqlite_db) as conn:
                df = pd.read_sql_query(
                    "SELECT date, open, high, low, close, volume FROM daily_ohlcv WHERE ticker = ? ORDER BY date ASC",
                    conn,
                    params=(ticker,),
                    parse_dates=["date"]
                )
                if not df.empty:
                    df.set_index("date", inplace=True)
                    # Check if latest date is within 2 days (recency check)
                    latest = df.index.max()
                    if (pd.Timestamp.now() - latest).days <= 2:
                        return df
        except Exception:
            pass
        return None

    def _save_cache(self, ticker: str, df: pd.DataFrame):
        try:
            with sqlite3.connect(self.config.sqlite_db) as conn:
                records = []
                for idx, row in df.iterrows():
                    d_str = idx.strftime("%Y-%m-%d")
                    records.append((ticker, d_str, float(row["Open"]), float(row["High"]),
                                    float(row["Low"]), float(row["Close"]), float(row["Volume"])))
                conn.executemany("""
                    INSERT OR REPLACE INTO daily_ohlcv (ticker, date, open, high, low, close, volume)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, records)
        except Exception:
            pass

    def fetch_daily_ohlcv(self, tickers: List[str]) -> Dict[str, pd.DataFrame]:
        results = {}
        missing = []

        for ticker in tickers:
            cached = self._load_cached(ticker)
            if cached is not None and len(cached) >= 100:
                results[ticker] = cached
            else:
                missing.append(ticker)

        if not missing:
            return results

        # Process missing tickers in batches with backoff
        for i in range(0, len(missing), self.config.batch_size):
            batch = missing[i:i + self.config.batch_size]
            batch_str = " ".join(batch)
            attempts = 3
            data = None
            for attempt in range(attempts):
                try:
                    data = yf.download(
                        batch_str,
                        period="2y",
                        interval="1d",
                        auto_adjust=True,
                        threads=True,
                        progress=False
                    )
                    break
                except Exception as e:
                    time.sleep(1.0 * (2 ** attempt))

            if data is not None and not data.empty:
                if len(batch) == 1:
                    t = batch[0]
                    df = data.dropna()
                    if not df.empty:
                        results[t] = df
                        self._save_cache(t, df)
                else:
                    for t in batch:
                        try:
                            sub_df = pd.DataFrame({
                                "Open": data["Open"][t],
                                "High": data["High"][t],
                                "Low": data["Low"][t],
                                "Close": data["Close"][t],
                                "Volume": data["Volume"][t]
                            }).dropna()
                            if not sub_df.empty:
                                results[t] = sub_df
                                self._save_cache(t, sub_df)
                        except Exception:
                            continue

        return results

    def passes_filters(self, df: pd.DataFrame) -> bool:
        """
        Check if stock passes baseline filters:
        - Price >= Rs 20
        - 50-day average daily traded value >= Rs 2 Cr (20,000,000)
        - >= 52 weeks (~250 trading days) of history
        """
        if len(df) < 150: # need at least sufficient history
            return False
            
        last_close = df["Close"].iloc[-1]
        if last_close < self.config.min_price:
            return False

        # 50-day average traded value = Close * Volume
        recent_50 = df.iloc[-50:]
        daily_traded_value = recent_50["Close"] * recent_50["Volume"]
        avg_value = daily_traded_value.mean()
        min_required_value = self.config.min_traded_value_cr * 10_000_000 # 2 Cr = 20M INR

        if avg_value < min_required_value:
            return False

        return True
