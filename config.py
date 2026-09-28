"""
Configuration module for A+ Weekly Stock Screener.
All parameters have defaults as specified in the rules.
"""
from dataclasses import dataclass
import os

@dataclass
class ScreenerConfig:
    # Setup Detection Rules
    scan_lookback_weeks: int = 16          # Scan breakout weeks B in last N weeks
    base_min_weeks: int = 3                # Minimum base length
    base_max_weeks: int = 5                # Maximum base length
    base_max_depth_pct: float = 0.10       # Max base depth (high - low) / high <= 10%
    base_low_ema_ratio: float = 0.98       # Base lows >= 0.98 * EMA20
    base_last_close_ema_ratio: float = 1.10# Last base close <= 1.10 * EMA20
    ema_slope_weeks: int = 4               # EMA20 at B > EMA20 four weeks earlier (B-4)
    
    # Breakout Rules
    breakout_min_gain_pct: float = 0.04    # (close - open) / open >= 4%
    breakout_top_range_pct: float = 0.30   # Close in top 30% of range (high - close)/(high - low) <= 0.30
    breakout_vol_mult: float = 2.0         # Volume >= 2.0x prior 20-week average volume
    
    # Pullback Rules
    pullback_max_weeks: int = 10           # T - B <= 10 weeks
    pullback_touch_ratio: float = 1.02     # Low <= EMA20 * 1.02
    pullback_close_floor_ratio: float = 0.99 # Reject if close(T) < EMA20 * 0.99
    pullback_vol_dryup_ratio: float = 0.70 # Avg volume (B+1..T) <= 70% of breakout volume
    watch_buffer_pct: float = 0.05         # WATCH status if no touch yet and close <= 1.05 * EMA20
    
    # Trade Math & Risk Filters
    stop_buffer_pct: float = 0.01          # 1% below min(T low, EMA20)
    target_r_multiple: float = 3.0         # 3R target level
    max_risk_pct: float = 0.07             # Keep only risk <= 7%
    min_rr: float = 2.0                    # Keep only RR >= 2.0
    
    # Universe Filters
    min_price: float = 20.0                # Price >= Rs 20
    min_traded_value_cr: float = 2.0       # 50-day avg traded value >= Rs 2 Cr (20,000,000)
    min_history_weeks: int = 52            # At least 52 weeks of history
    include_running_week: bool = False     # Toggle for running week vs completed weeks
    
    # Cache and Batches
    batch_size: int = 100
    cache_dir: str = "cache"
    sqlite_db: str = "cache/market_data.sqlite"
    
    # Telegram Notifications
    telegram_bot_token: str = os.getenv("TELEGRAM_BOT_TOKEN", "")
    telegram_chat_id: str = os.getenv("TELEGRAM_CHAT_ID", "")

DEFAULT_CONFIG = ScreenerConfig()
