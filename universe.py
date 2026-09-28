"""
Universe module for stock symbols across NSE and BSE (~5000 stocks).
Fetches official lists with browser headers and provides local CSV fallbacks.
"""
import csv
import io
import os
import requests
import pandas as pd
from typing import List, Dict

NSE_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}

NIFTY_500_URL = "https://archives.nseindia.com/content/indices/ind_nifty500list.csv"
NSE_ALL_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv"
FULL_5000_CSV = "data/nse_bse_5000.csv"

def get_full_5000_universe(filepath: str = FULL_5000_CSV) -> List[Dict[str, str]]:
    """
    Returns the comprehensive ~5000 stock universe combining all NSE equities
    and BSE listed stocks.
    """
    if os.path.exists(filepath):
        stocks = []
        with open(filepath, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                stocks.append({
                    "symbol": row["Symbol"].strip(),
                    "exchange": row.get("Exchange", "NSE").strip(),
                    "ticker": row.get("Ticker", f"{row['Symbol'].strip()}.NS").strip(),
                    "company": row.get("Company", row["Symbol"].strip()).strip(),
                    "series": row.get("Series", "EQ").strip()
                })
        if len(stocks) >= 100:
            return stocks

    # Fallback to fetching all NSE equities and expanding
    return get_all_nse_eq()

def get_nifty500_symbols(fallback_path: str = "data/nifty500.csv") -> List[Dict[str, str]]:
    """
    Fetch Nifty 500 stock universe.
    Returns list of dicts with 'symbol', 'exchange', 'ticker', 'company'.
    """
    stocks = []
    fetched = False
    try:
        resp = requests.get(NIFTY_500_URL, headers=NSE_HEADERS, timeout=8)
        if resp.status_code == 200:
            df = pd.read_csv(io.StringIO(resp.text))
            if "Symbol" in df.columns:
                for _, row in df.iterrows():
                    sym = str(row["Symbol"]).strip()
                    company = str(row.get("Company Name", sym)).strip()
                    if sym and not sym.startswith("#"):
                        stocks.append({
                            "symbol": sym,
                            "exchange": "NSE",
                            "ticker": f"{sym}.NS",
                            "company": company,
                            "series": "EQ"
                        })
                fetched = True
    except Exception:
        pass

    if not fetched:
        if os.path.exists(fallback_path):
            df = pd.read_csv(fallback_path)
            for _, row in df.iterrows():
                sym = str(row["Symbol"]).strip()
                company = str(row.get("Company Name", sym)).strip()
                stocks.append({
                    "symbol": sym,
                    "exchange": "NSE",
                    "ticker": f"{sym}.NS",
                    "company": company,
                    "series": "EQ"
                })

    return stocks

def get_all_nse_eq() -> List[Dict[str, str]]:
    """Fetch all NSE traded stocks (~2,600 stocks from official archive)."""
    stocks = []
    try:
        resp = requests.get(NSE_ALL_URL, headers=NSE_HEADERS, timeout=10)
        if resp.status_code == 200:
            reader = csv.DictReader(resp.text.splitlines())
            for row in reader:
                sym = row.get("SYMBOL", "").strip()
                company = row.get("NAME OF COMPANY", sym).strip()
                series = row.get(" SERIES", "EQ").strip()
                if sym:
                    stocks.append({
                        "symbol": sym,
                        "exchange": "NSE",
                        "ticker": f"{sym}.NS",
                        "company": company,
                        "series": series
                    })
            if stocks:
                return stocks
    except Exception:
        pass

    # Fallback to local full CSV filtered by NSE
    if os.path.exists(FULL_5000_CSV):
        all_stocks = get_full_5000_universe()
        return [s for s in all_stocks if s["exchange"] == "NSE"]

    return get_nifty500_symbols()

def load_universe(preset: str = "All (NSE + BSE ~5000)", include_bse: bool = True) -> List[Dict[str, str]]:
    """
    Unified entry point for loading the desired stock universe.
    Presets:
    - 'All (NSE + BSE ~5000)': Full combined universe of ~5,000 stocks
    - 'All NSE Equities (~2600)': All ~2600 NSE equities
    - 'Nifty 500': Nifty 500 constituents
    - 'BSE Only': Stocks listed on BSE
    """
    if "5000" in preset or "All (NSE + BSE" in preset:
        return get_full_5000_universe()
    elif "All NSE" in preset:
        return get_all_nse_eq()
    elif "BSE" in preset:
        all_s = get_full_5000_universe()
        return [s for s in all_s if s["exchange"] == "BSE"]
    else:
        return get_nifty500_symbols()
