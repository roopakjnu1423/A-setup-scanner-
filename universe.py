"""
Universe module for stock symbols across NSE and BSE.
Fetches official lists with browser headers and provides local CSV fallbacks.
"""
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
                            "company": company
                        })
                fetched = True
    except Exception as e:
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
                    "company": company
                })

    return stocks

def get_all_nse_eq(fallback_path: str = "data/nifty500.csv") -> List[Dict[str, str]]:
    """Fetch all NSE EQ series stocks."""
    stocks = []
    try:
        resp = requests.get(NSE_ALL_URL, headers=NSE_HEADERS, timeout=10)
        if resp.status_code == 200:
            df = pd.read_csv(io.StringIO(resp.text))
            if "SYMBOL" in df.columns and " SERIES" in df.columns:
                eq_df = df[df[" SERIES"].str.strip() == "EQ"]
                for _, row in eq_df.iterrows():
                    sym = str(row["SYMBOL"]).strip()
                    company = str(row.get("NAME OF COMPANY", sym)).strip()
                    stocks.append({
                        "symbol": sym,
                        "exchange": "NSE",
                        "ticker": f"{sym}.NS",
                        "company": company
                    })
                return stocks
    except Exception:
        pass
    return get_nifty500_symbols(fallback_path)

def get_bse_only(existing_symbols: List[str] = None) -> List[Dict[str, str]]:
    """
    Returns prominent BSE symbols that might not trade on NSE,
    preferring NSE if available.
    """
    existing = set(existing_symbols or [])
    bse_candidates = [
        {"symbol": "BOMDYEING", "exchange": "BSE", "ticker": "BOMDYEING.BO", "company": "Bombay Dyeing"},
        {"symbol": "SENSEX", "exchange": "BSE", "ticker": "SENSEX.BO", "company": "BSE Sensex Basket"},
        {"symbol": "500112", "exchange": "BSE", "ticker": "500112.BO", "company": "State Bank of Bikaner"},
    ]
    return [s for s in bse_candidates if s["symbol"] not in existing]

def load_universe(preset: str = "Nifty 500", include_bse: bool = False) -> List[Dict[str, str]]:
    """
    Unified entry point for loading the desired stock universe.
    """
    if preset == "All NSE Equity":
        universe = get_all_nse_eq()
    else:
        universe = get_nifty500_symbols()

    if include_bse:
        existing = [u["symbol"] for u in universe]
        universe.extend(get_bse_only(existing))

    return universe
