"""
Data provider interface and yfinance implementation with SQLite caching,
batching, retry logic, and liquidity/price filters.
"""
from abc import ABC, abstractmethod
import datetime
import os
import sqlite3
import time
from typing import Callable, Dict, List, Optional
import pandas as pd
import yfinance as yf

from config import ScreenerConfig, DEFAULT_CONFIG

class MarketDataProvider(ABC):
    """Abstract interface for stock market data retrieval."""
    
    @abstractmethod
    def fetch_daily_ohlcv(
        self,
        tickers: List[str],
        progress_callback: Optional[Callable[[int, int, str], None]] = None
    ) -> Dict[str, pd.DataFrame]:
        """Fetch daily OHLCV dataframe for each ticker."""
        pass

class YFinanceDataProvider(MarketDataProvider):
    """
    Yahoo Finance data fetcher with local SQLite caching,
    exponential backoff, and batch downloading for ~5000 stocks.
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
                if not df.empty and len(df) >= 50:
                    df.set_index("date", inplace=True)
                    latest = df.index.max()
                    # If cached within 2 days, treat as fresh
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
                    records.append((
                        ticker,
                        d_str,
                        float(row["Open"]),
                        float(row["High"]),
                        float(row["Low"]),
                        float(row["Close"]),
                        float(row["Volume"])
                    ))
                conn.executemany("""
                    INSERT OR REPLACE INTO daily_ohlcv (ticker, date, open, high, low, close, volume)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, records)
        except Exception:
            pass

    def fetch_daily_ohlcv(
        self,
        tickers: List[str],
        progress_callback: Optional[Callable[[int, int, str], None]] = None
    ) -> Dict[str, pd.DataFrame]:
        results: Dict[str, pd.DataFrame] = {}
        missing: List[str] = []

        total_tickers = len(tickers)
        for i, ticker in enumerate(tickers):
            cached = self._load_cached(ticker)
            if cached is not None and len(cached) >= 50:
                results[ticker] = cached
            else:
                missing.append(ticker)

        if progress_callback:
            progress_callback(len(results), total_tickers, "Loaded cached datasets")

        if not missing:
            return results

        # Process missing tickers in batches
        processed_count = len(results)
        batch_size = max(10, min(self.config.batch_size, 100))

        for i in range(0, len(missing), batch_size):
            batch = missing[i:i + batch_size]
            batch_str = " ".join(batch)
            attempts = 3
            data = None

            if progress_callback:
                progress_callback(
                    processed_count,
                    total_tickers,
                    f"Fetching batch {i // batch_size + 1}/{(len(missing) + batch_size - 1) // batch_size}"
                )

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
                except Exception:
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
                            # Handle both MultiIndex and standard format from yf
                            if isinstance(data.columns, pd.MultiIndex):
                                sub_df = pd.DataFrame({
                                    "Open": data["Open"][t],
                                    "High": data["High"][t],
                                    "Low": data["Low"][t],
                                    "Close": data["Close"][t],
                                    "Volume": data["Volume"][t]
                                }).dropna()
                            else:
                                sub_df = data.dropna()
                            if not sub_df.empty:
                                results[t] = sub_df
                                self._save_cache(t, sub_df)
                        except Exception:
                            continue

            processed_count += len(batch)

        if progress_callback:
            progress_callback(total_tickers, total_tickers, "Market data fetch complete")

        return results

    def passes_filters(self, df: pd.DataFrame) -> bool:
        """
        Check if stock passes baseline filters:
        - Price >= Rs 20
        - 50-day average daily traded value >= Rs 2 Cr (20,000,000)
        - >= 52 weeks (~250 trading days) of history (minimum 100 bars for screening)
        """
        if len(df) < 100:
            return False

        last_close = df["Close"].iloc[-1]
        if last_close < self.config.min_price:
            return False

        recent_50 = df.iloc[-50:]
        daily_traded_value = recent_50["Close"] * recent_50["Volume"]
        avg_value = daily_traded_value.mean()
        min_required_value = self.config.min_traded_value_cr * 10_000_000

        if avg_value < min_required_value:
            return False

        return True
