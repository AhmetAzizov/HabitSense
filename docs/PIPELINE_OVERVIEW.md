# HabitSense — how data and AI fit together

Use this as a map before building UI. The UI will **only read/write Room** and call the same repositories/strategies tested in `StrategyPipelineInstrumentedTest`.

## Layers (bottom → top)

```
Room (habits, logs, mood, expenses, experiment_runs)
        ↓
DailyDataRepository  →  one DailyData per dateString (active habits, mood, expenses)
        ↓
┌───────────────────────────────────────────────────────────────┐
│  Consumers (today + future Insights)                          │
│  • HabitPredictionDatasetRepository → CSV / training rows     │
│  • StrategyFactory (BASELINE | COMPLEX) → analytics insights   │
│  • StrategyExecutor → timing/memory → experiment_runs         │
└───────────────────────────────────────────────────────────────┘
```

## DailyData rules (must not break in UI)

| Rule | Meaning |
|------|--------|
| `dateString` | Primary key for grouping (`YYYY-MM-DD`). |
| `activeHabitIds` | Habit exists only if `createdAtDateString <= date`. |
| `completionFor(habitId)` | `null` = not active that day; `false` = active but not done. |

## Six strategies (same inputs, different algorithms)

| Task | BASELINE | COMPLEX |
|------|----------|---------|
| Tomorrow habit completion | Moving average (7-day window) | TFLite on 42-dim features (`habit_predictor.tflite`) |
| Day “types” | Fixed rules (`BalancedDay`, …) | K-Means on 6-dim daily vector |
| Relationships | Spearman correlations | Apriori-style association rules |

**Prediction call shape (what Insights will do):**

- `history` = all days **before** target date (from `buildDailyDataRange` or trailing window).
- `targetDateString` = day you predict (usually “tomorrow” or a chosen eval date).
- `habitOrder` = `getHabitOrder()`.

## Training vs on-device

| Piece | Where |
|-------|--------|
| Labeled CSV | Phone export → Python `train_habit_predictor.py` |
| TFLite weights | `app/src/main/assets/habit_predictor.tflite` |
| K-Means / Apriori / moving average | Run on device when user opens Insights (no extra training file) |

## What androidTest proves today

| Level | Meaning |
|-------|--------|
| **Smoke** | Strategies run; TFLite loads; experiment row inserted. |
| **Correctness** | Dataset labels match Room; moving average matches hand calculation; heuristic rules match days that satisfy them; multi-day prediction eval beats random guessing on seeded enriched data. |

androidTest is **not** the thesis results chapter (no published baseline-vs-complex tables). It is a **safety net** that the finished app’s brain behaves consistently before you wire Compose screens.

## Typical real-user flow (what UI will mirror)

1. User logs habits/mood/expenses (Today tab → Room).
2. Insights loads `buildDailyDataRange(start, end)`.
3. User picks strategy mode (or app defaults): predict / cluster / correlate.
4. Optional: `StrategyExecutor` logs the run for thesis metrics later.

Synthetic seed + export button in `MainActivity` is a **dev shortcut** for step 1–2 without UI.
