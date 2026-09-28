"""
CLI Scanner for A+ Weekly Stock Setup.
Supports:
- Live scanning of Nifty 500 / NSE / BSE
- Historical replay via --as_of DATE with 4, 8, 12-week forward returns
- Telegram notifications via --notify (configured via .env)
"""
import argparse
import datetime
import os
import requests
import sys
import pandas as pd
from tabulate import tabulate

from config import ScreenerConfig, DEFAULT_CONFIG
from universe import load_universe
from data import YFinanceDataProvider
from indicators import resample_to_weekly, compute_indicators
from detector import detect_setup, SetupResult

def send_telegram_alert(message: str, bot_token: str, chat_id: str):
    """Send formatted alert message via Telegram Bot API."""
    if not bot_token or not chat_id:
        print("[!] Telegram credentials missing in environment (.env)")
        return
    url = f"https://api.telegram.org/bot{bot_token}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "HTML"
    }
    try:
        resp = requests.post(url, json=payload, timeout=5)
        if resp.status_code == 200:
            print("[+] Telegram alert sent successfully!")
        else:
            print(f"[!] Failed to send Telegram alert: {resp.text}")
    except Exception as e:
        print(f"[!] Telegram alert error: {e}")

def run_scanner(
    universe_type: str = "Nifty 500",
    as_of_date: str = None,
    notify: bool = False,
    max_symbols: int = None
):
    config = DEFAULT_CONFIG
    print(f"==================================================")
    print(f"  A+ Weekly Setup Screener (NSE / BSE)")
    print(f"  Universe: {universe_type}")
    if as_of_date:
        print(f"  History Replay As-Of: {as_of_date}")
    print(f"==================================================")

    universe = load_universe(preset=universe_type)
    if max_symbols:
        universe = universe[:max_symbols]

    tickers = [s["ticker"] for s in universe]
    symbol_map = {s["ticker"]: s for s in universe}

    print(f"[*] Fetching market data for {len(tickers)} symbols...")
    provider = YFinanceDataProvider(config)
    data_dict = provider.fetch_daily_ohlcv(tickers)

    print(f"[*] Processing weekly bars and scanning setups...")
    detected_setups: list[SetupResult] = []
    forward_results = []

    as_of_dt = pd.to_datetime(as_of_date) if as_of_date else None

    for ticker, daily_df in data_dict.items():
        sym_info = symbol_map.get(ticker, {"symbol": ticker, "exchange": "NSE"})
        
        # Check liquidity filter on daily data
        if not provider.passes_filters(daily_df):
            continue

        weekly_full = resample_to_weekly(daily_df, include_running_week=config.include_running_week)
        if len(weekly_full) < 25:
            continue

        if as_of_dt:
            # Replay slice up to as_of_date
            weekly_slice = weekly_full[weekly_full.index <= as_of_dt]
        else:
            weekly_slice = weekly_full

        if len(weekly_slice) < 25:
            continue

        enriched = compute_indicators(weekly_slice)
        setup = detect_setup(enriched, symbol=sym_info["symbol"], exchange=sym_info["exchange"], config=config)

        if setup:
            detected_setups.append(setup)

            # Calculate forward returns if historical replay
            if as_of_dt:
                as_of_idx = weekly_full.index.get_indexer([weekly_slice.index[-1]], method="pad")[0]
                entry = setup.entry_price
                
                fwd_4 = None
                fwd_8 = None
                fwd_12 = None
                
                if as_of_idx + 4 < len(weekly_full):
                    c4 = weekly_full["Close"].iloc[as_of_idx + 4]
                    fwd_4 = round(((c4 - entry) / entry) * 100, 2)
                if as_of_idx + 8 < len(weekly_full):
                    c8 = weekly_full["Close"].iloc[as_of_idx + 8]
                    fwd_8 = round(((c8 - entry) / entry) * 100, 2)
                if as_of_idx + 12 < len(weekly_full):
                    c12 = weekly_full["Close"].iloc[as_of_idx + 12]
                    fwd_12 = round(((c12 - entry) / entry) * 100, 2)

                forward_results.append({
                    "Symbol": setup.symbol,
                    "Status": setup.status,
                    "Entry": setup.entry_price,
                    "Stop": setup.stop_loss,
                    "Risk %": setup.risk_pct,
                    "A+ Score": setup.score,
                    "+4W %": fwd_4 if fwd_4 is not None else "-",
                    "+8W %": fwd_8 if fwd_8 is not None else "-",
                    "+12W %": fwd_12 if fwd_12 is not None else "-"
                })

    detected_setups.sort(key=lambda x: x.score, reverse=True)

    if not detected_setups:
        print("\n[-] No stocks currently qualify for the A+ Setup under current filters.")
        return

    print(f"\n[+] Found {len(detected_setups)} Qualifying Setups:\n")

    table_data = []
    for s in detected_setups:
        table_data.append([
            s.symbol,
            s.exchange,
            s.status,
            s.cmp,
            f"{s.pct_from_ema}%",
            f"{s.base_length}W ({s.base_depth_pct}%)",
            f"{s.volume_mult}x",
            s.entry_price,
            s.stop_loss,
            s.target_3r,
            f"{s.risk_pct}%",
            f"1:{s.rr_ratio}",
            s.score
        ])

    headers = ["Symbol", "Exch", "Status", "CMP", "% EMA", "Base", "VolMult", "Entry", "Stop", "Target(3R)", "Risk%", "RR", "Score"]
    print(tabulate(table_data, headers=headers, tablefmt="fancy_grid"))

    if forward_results:
        print("\n[+] Historical Forward Return Performance:")
        print(tabulate(forward_results, headers="keys", tablefmt="fancy_grid"))

    if notify and detected_setups:
        msg_lines = ["<b>🚨 A+ Weekly Setup Alerts</b>\n"]
        for s in detected_setups[:5]:
            msg_lines.append(
                f"<b>{s.symbol}</b> ({s.exchange}) - <b>{s.status}</b> | Score: {s.score}/100\n"
                f"• CMP: ₹{s.cmp} | Stop: ₹{s.stop_loss} ({s.risk_pct}% risk)\n"
                f"• Target 3R: ₹{s.target_3r} | Breakout Vol: {s.volume_mult}x\n"
            )
        msg_lines.append("\n<i>Educational screening tool, not investment advice.</i>")
        send_telegram_alert("\n".join(msg_lines), config.telegram_bot_token, config.telegram_chat_id)

def main():
    parser = argparse.ArgumentParser(description="A+ Setup Stock Screener CLI")
    parser.add_argument("--universe", type=str, default="Nifty 500", choices=["Nifty 500", "All NSE Equity", "BSE"], help="Universe to scan")
    parser.add_argument("--as_of", type=str, default=None, help="Replay as of specific date (YYYY-MM-DD)")
    parser.add_argument("--notify", action="store_true", help="Send Telegram alert with top findings")
    parser.add_argument("--max", type=int, default=None, help="Limit number of tickers to scan")
    args = parser.parse_args()

    run_scanner(
        universe_type=args.universe,
        as_of_date=args.as_of,
        notify=args.notify,
        max_symbols=args.max
    )

if __name__ == "__main__":
    main()
