"""
Streamlit Web Application for A+ Weekly Stock Screener.
Includes interactive Plotly charts, threshold sidebar, sortable table, and CSV export.
"""
import streamlit as st
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from config import ScreenerConfig
from universe import load_universe
from data import YFinanceDataProvider
from indicators import resample_to_weekly, compute_indicators
from detector import detect_setup, SetupResult

st.set_page_config(
    page_title="A+ Weekly Setup Screener",
    page_icon="📈",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Header
st.title("📈 A+ Weekly Setup Screener (NSE / BSE)")
st.caption("Based on 3-5 week tight base + volume breakout + first 20 EMA pullback with low risk & high RR.")

# Sidebar Configuration
st.sidebar.header("⚙️ Universe & Parameters")
universe_choice = st.sidebar.selectbox("Universe", ["Nifty 500", "All NSE Equity", "BSE"], index=0)
status_filter = st.sidebar.multiselect("Filter Status", ["TOUCH", "BOUNCE", "WATCH"], default=["TOUCH", "BOUNCE", "WATCH"])

st.sidebar.subheader("Detection Thresholds")
base_max_depth = st.sidebar.slider("Max Base Depth (%)", 5, 20, 10) / 100.0
breakout_gain = st.sidebar.slider("Min Breakout Gain (%)", 2, 10, 4) / 100.0
breakout_vol_mult = st.sidebar.slider("Min Breakout Vol Mult (x)", 1.5, 5.0, 2.0, 0.1)
pullback_touch = st.sidebar.slider("Pullback Touch Ratio", 1.00, 1.05, 1.02, 0.01)
vol_dryup = st.sidebar.slider("Max Pullback Vol Dry-Up (%)", 40, 100, 70) / 100.0
max_risk = st.sidebar.slider("Max Allowed Risk (%)", 3, 12, 7) / 100.0
min_rr = st.sidebar.slider("Min Risk:Reward", 1.5, 5.0, 2.0, 0.5)
include_running = st.sidebar.checkbox("Include Running Week", value=False)
max_symbols = st.sidebar.number_input("Max Symbols to Scan (0 for All)", min_value=0, max_value=2000, value=50, step=25)

config = ScreenerConfig(
    base_max_depth_pct=base_max_depth,
    breakout_min_gain_pct=breakout_gain,
    breakout_vol_mult=breakout_vol_mult,
    pullback_touch_ratio=pullback_touch,
    pullback_vol_dryup_ratio=vol_dryup,
    max_risk_pct=max_risk,
    min_rr=min_rr,
    include_running_week=include_running
)

# Scan Button
if st.sidebar.button("🚀 Run Live Scan", type="primary") or "scan_results" not in st.session_state:
    with st.spinner("Fetching data and running A+ detection..."):
        universe = load_universe(preset=universe_choice)
        if max_symbols > 0:
            universe = universe[:max_symbols]

        tickers = [s["ticker"] for s in universe]
        symbol_map = {s["ticker"]: s for s in universe}

        provider = YFinanceDataProvider(config)
        data_dict = provider.fetch_daily_ohlcv(tickers)

        results = []
        enriched_map = {}

        for ticker, daily_df in data_dict.items():
            sym_info = symbol_map.get(ticker, {"symbol": ticker, "exchange": "NSE"})
            if not provider.passes_filters(daily_df):
                continue

            weekly = resample_to_weekly(daily_df, include_running_week=config.include_running_week)
            if len(weekly) < 25:
                continue

            enriched = compute_indicators(weekly)
            setup = detect_setup(enriched, symbol=sym_info["symbol"], exchange=sym_info["exchange"], config=config)
            if setup:
                results.append(setup)
                enriched_map[setup.symbol] = (enriched, setup)

        results.sort(key=lambda x: x.score, reverse=True)
        st.session_state["scan_results"] = results
        st.session_state["enriched_map"] = enriched_map

results = st.session_state.get("scan_results", [])
enriched_map = st.session_state.get("enriched_map", {})

filtered_results = [r for r in results if r.status in status_filter]

# Metrics Summary Bar
c1, c2, c3, c4 = st.columns(4)
c1.metric("Total Setups Found", len(filtered_results))
c2.metric("Touches @ 20 EMA", len([r for r in filtered_results if r.status == "TOUCH"]))
c3.metric("Confirmed Bounces", len([r for r in filtered_results if r.status == "BOUNCE"]))
c4.metric("On Watchlist", len([r for r in filtered_results if r.status == "WATCH"]))

if not filtered_results:
    st.info("No stocks currently match the chosen setup filters. Adjust parameters in the sidebar to widen criteria.")
else:
    table_rows = []
    for r in filtered_results:
        table_rows.append({
            "Symbol": r.symbol,
            "Exchange": r.exchange,
            "Status": r.status,
            "CMP": r.cmp,
            "% From EMA": f"{r.pct_from_ema}%",
            "Base": f"{r.base_length}W ({r.base_depth_pct}%)",
            "Breakout Vol": f"{r.volume_mult}x",
            "Entry": r.entry_price,
            "Stop Loss": r.stop_loss,
            "Target (3R)": r.target_3r,
            "Risk %": f"{r.risk_pct}%",
            "RR": f"1:{r.rr_ratio}",
            "Score": r.score
        })
    df_display = pd.DataFrame(table_rows)

    st.subheader("📋 Qualifying Stock Setups")
    st.dataframe(df_display, use_container_width=True)

    # CSV Download Button
    csv_bytes = df_display.to_csv(index=False).encode("utf-8")
    st.download_button(
        label="📥 Export Setups to CSV",
        data=csv_bytes,
        file_name="aplus_weekly_setups.csv",
        mime="text/csv"
    )

    # Interactive Chart Section
    st.markdown("---")
    st.subheader("📊 Chart Inspection & Setup Annotations")
    selected_symbol = st.selectbox("Select Stock to Inspect:", [r.symbol for r in filtered_results])

    if selected_symbol and selected_symbol in enriched_map:
        weekly_df, setup_data = enriched_map[selected_symbol]
        c_markers = setup_data.chart_data

        # Plotly Candlestick & Volume Subplots
        fig = make_subplots(
            rows=2, cols=1,
            shared_xaxes=True,
            vertical_spacing=0.04,
            row_heights=[0.75, 0.25],
            specs=[[{"secondary_y": False}], [{"secondary_y": False}]]
        )

        # Candlestick
        fig.add_trace(
            go.Candlestick(
                x=weekly_df.index,
                open=weekly_df["Open"],
                high=weekly_df["High"],
                low=weekly_df["Low"],
                close=weekly_df["Close"],
                name="Price"
            ),
            row=1, col=1
        )

        # EMA20 line
        fig.add_trace(
            go.Scatter(
                x=weekly_df.index,
                y=weekly_df["EMA20"],
                mode="lines",
                line=dict(color="#FFD700", width=2.5),
                name="20-Week EMA"
            ),
            row=1, col=1
        )

        # Volume bars
        colors = ["#26a69a" if c >= o else "#ef5350" for c, o in zip(weekly_df["Close"], weekly_df["Open"])]
        fig.add_trace(
            go.Bar(
                x=weekly_df.index,
                y=weekly_df["Volume"],
                marker_color=colors,
                name="Volume"
            ),
            row=2, col=1
        )

        # Vol MA 20
        fig.add_trace(
            go.Scatter(
                x=weekly_df.index,
                y=weekly_df["VolMA20"],
                mode="lines",
                line=dict(color="#29b6f6", width=1.5, dash="dash"),
                name="20-Wk Avg Vol"
            ),
            row=2, col=1
        )

        # Add visual zones if markers present
        if c_markers:
            b_start_dt = weekly_df.index[c_markers["base_start_idx"]]
            b_end_dt = weekly_df.index[c_markers["base_end_idx"]]
            brk_dt = weekly_df.index[c_markers["breakout_idx"]]

            # Highlight Base
            fig.add_vrect(
                x0=b_start_dt, x1=b_end_dt,
                fillcolor="#FFB74D", opacity=0.18,
                layer="below", line_width=1, line_dash="dot",
                annotation_text=f"Base ({setup_data.base_length}W)",
                annotation_position="top left",
                row=1, col=1
            )

            # Annotate Breakout
            fig.add_annotation(
                x=brk_dt, y=weekly_df["High"].iloc[c_markers["breakout_idx"]],
                text=f"Breakout ({setup_data.volume_mult}x Vol)",
                showarrow=True, arrowhead=2, arrowcolor="#00E676",
                row=1, col=1
            )

            # Annotate Touch if exists
            if c_markers.get("touch_idx"):
                t_dt = weekly_df.index[c_markers["touch_idx"]]
                fig.add_annotation(
                    x=t_dt, y=weekly_df["Low"].iloc[c_markers["touch_idx"]],
                    text="First Pullback @ 20 EMA",
                    showarrow=True, arrowhead=2, arrowcolor="#FF5252",
                    ay=35, row=1, col=1
                )

        # Trade Levels: Entry, Stop, Target
        fig.add_hline(y=setup_data.entry_price, line=dict(color="#448AFF", dash="dash"), annotation_text=f"Entry: ₹{setup_data.entry_price}", row=1, col=1)
        fig.add_hline(y=setup_data.stop_loss, line=dict(color="#FF1744", dash="dot"), annotation_text=f"Stop: ₹{setup_data.stop_loss}", row=1, col=1)
        fig.add_hline(y=setup_data.target_3r, line=dict(color="#00E676", dash="dot"), annotation_text=f"Target (3R): ₹{setup_data.target_3r}", row=1, col=1)

        fig.update_layout(
            height=650,
            xaxis_rangeslider_visible=False,
            template="plotly_dark",
            margin=dict(l=20, r=20, t=30, b=20)
        )

        st.plotly_chart(fig, use_container_width=True)

        # Setup scorecard
        st.markdown(f"### 🏆 Setup Scorecard: **{setup_data.score} / 100**")
        sc1, sc2, sc3, sc4, sc5 = st.columns(5)
        sc1.metric("Base Tightness", f"{setup_data.sub_scores['Base Tightness']} / 25")
        sc2.metric("Volume Multiple", f"{setup_data.sub_scores['Volume Multiple']} / 25")
        sc3.metric("EMA Slope", f"{setup_data.sub_scores['EMA Slope']} / 20")
        sc4.metric("Volume Dry-Up", f"{setup_data.sub_scores['Volume Dry-Up']} / 15")
        sc5.metric("Risk Size", f"{setup_data.sub_scores['Risk Size']} / 15")

# Footer
st.markdown("---")
st.markdown(
    "<div style='text-align: center; color: #888888; font-size: 0.9em; padding: 15px;'>"
    "Educational screening tool, not investment advice."
    "</div>",
    unsafe_allow_html=True
)
