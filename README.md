# A+ Setup Weekly Stock Screener (NSE / BSE)

An institutional-grade stock screener for Indian Equities (NSE and BSE) built to identify the **"A+ Setup"** on the weekly timeframe:

1. **Base**: Tight 3-5 week consolidation just above a rising 20-week EMA.
2. **Breakout**: Strong green weekly candle closing above the base high on >= 2x 20-week average volume.
3. **First Pullback**: Price touches the weekly 20 EMA for the first time since the breakout with dried-up volume.
4. **A+ Setup**: Low-risk, high risk-reward entry upon the first touch/bounce with a tight stop just below the 20 EMA.

---

## 🚀 Quickstart

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Run Unit Tests (Pytest)
```bash
pytest test_detector.py -v
```

### 3. Launch Web Application (Streamlit)
```bash
streamlit run app.py
```

### 4. CLI Scanner with Replay & Telegram Alerts
```bash
# Live scan of all ~5000 NSE + BSE stocks
python scan.py --universe "All (NSE + BSE ~5000)"

# Live scan of all ~2600 NSE Equities
python scan.py --universe "All NSE Equities (~2600)"

# Live scan of Nifty 500 (or custom limit)
python scan.py --universe "Nifty 500" --max 100

# Historical backtest / replay as of a specific date with 4, 8, and 12-week forward returns:
python scan.py --as_of 2024-03-01

# Send instant Telegram alerts for top setups (configure .env first):
python scan.py --notify
```

---

## 🏛️ Comprehensive Stock Universe (~5,000 Stocks)
The app includes the complete official catalog of Indian equities:
- **2,601 NSE Equities**: Directly parsed from official NSE archives (`EQUITY_L.csv`), including active `EQ`, `BE`, and `BZ` series.
- **2,400+ BSE Scrips**: Active BSE listed securities and scrip master codes (500000+ series).
- **Offline & Bundled Cache**: Stored in `data/nse_bse_5000.csv` and Android assets for instant access, offline fallback, and fast batch scanning.

---

## ⚙️ Configuration & Telegram (.env)
Create or edit `.env`:
```env
TELEGRAM_BOT_TOKEN="your_bot_token"
TELEGRAM_CHAT_ID="your_chat_id"
```

---

## 📐 Detection Rules & Trade Math

- **Weekly Resampling**: Daily OHLCV converted to Friday-ending bars (`W-FRI`).
- **20 EMA**: `Close.ewm(span=20, adjust=False)`.
- **Base Verification**:
  - Length: 3, 4, or 5 weeks ending at `B-1`.
  - Tightness: Base depth `(High - Low) / High <= 10%`.
  - EMA Support: All base closes >= 20 EMA; all base lows >= 0.98 × 20 EMA.
  - Base Exit: Last base close <= 1.10 × 20 EMA.
- **Breakout Week (B)**:
  - Close > Base High.
  - Green candle: `(Close - Open) / Open >= 4%`.
  - Close in top 30% of weekly range.
  - Volume >= 2.0x prior 20-week volume moving average.
- **First Pullback (T)**:
  - Low touches 20 EMA (`Low <= 1.02 × 20 EMA`).
  - No close below base low post-breakout.
  - Volume dry-up: average volume `(B+1..T) <= 70%` of breakout volume.
  - Must occur within 10 weeks of breakout (`T - B <= 10`).
- **Statuses**:
  - `TOUCH`: First touch of 20 EMA happened in the latest week.
  - `BOUNCE`: Latest week is `T+1` and closed above week `T`'s high.
  - `WATCH`: Breakout occurred, within 5% above 20 EMA, but has not yet touched.
- **Trade Math**:
  - Entry = Latest Close.
  - Stop Loss = 1% below `min(T Low, 20 EMA)`.
  - Target = 3R level (`Entry + 3 × (Entry - Stop)`).
  - Risk Filter: Keep only risk <= 7% and RR >= 2.0.
  - A+ Score: 0 to 100 ranking evaluating Base Tightness (25), Volume Multiple (25), EMA Slope (20), Volume Dry-up (15), and Risk Size (15).

---

## ⚖️ Disclaimer
*Educational screening tool, not investment advice.*
