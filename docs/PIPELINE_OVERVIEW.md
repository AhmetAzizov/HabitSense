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
| `activeHabitIds` | Habit exists only if `HabitEntity.createdAtDateString <= date`. |
| `completionFor(habitId)` | `null` = not active that day; `false` = active but not done. |

`DailyDataRepository` builds `activeHabitIds` per day from habit creation dates; it does not embed a global habit ordering.

## Habit IDs vs active habits

| API | Role |
|-----|------|
| `getHabitIds()` | All habit IDs currently in Room (`habitDao.getAll()`). Used to loop “which habits exist in the app.” Order is not part of the ML contract. |
| `DailyData.activeHabitIds` | Per-day subset that existed on that `dateString`. Used for misses vs not-created-yet and for active-aware rates/features. |

Feature vectors come from **method-specific builders** (`HabitPredictionFeatureBuilder`, `ClusteringFeatureBuilder`), not from a single all-habits vector on `DailyData`. Schemas live in `FeatureSchema.kt` as `HabitPredictionFeatureSchema` (42-dim) and `ClusteringFeatureSchema` (6-dim).

## Six strategies (same inputs, different algorithms)

| Task | BASELINE | COMPLEX |
|------|----------|---------|
| Tomorrow habit completion | Moving average (7-day window) | TFLite on 42-dim features (`habit_predictor.tflite`) |
| Day “types” | Fixed rules (`BalancedDay`, …) | K-Means on 6-dim daily vector |
| Relationships | Spearman correlations | Apriori-style association rules |

**Strategy call shape (what Insights will do):**

1. `dailyRange` = `buildDailyDataRange(start, end)` (or trailing window).
2. `habitIds` = `getHabitIds()`.
3. **Predict:** `history` = days strictly before `targetDateString`; `habitPredictor.predict(history, targetDateString, habitIds)`.
4. **Cluster:** `dayClusterer.cluster(dailyRange, habitIds)` (clusterers use fixed daily features; `habitIds` is for API consistency).
5. **Correlate:** `correlationAnalyzer.analyze(dailyRange, habitIds)` (inactive days skipped via `completionFor`).

**Experiment logging:** wrap a strategy call in `StrategyExecutor.executeAndLog(..., featureSchemaVersion = …)` — use `HabitPredictionFeatureSchema.VERSION` for TFLite prediction runs, or the schema that matches the task.

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
| **Correctness** | Dataset labels match Room; moving average matches hand calculation; heuristic rules match days that satisfy them; multi-day prediction eval beats random guessing on seeded enriched data; a habit created later stays inactive on earlier dates even if a log exists. |

androidTest is **not** the thesis results chapter (no published baseline-vs-complex tables). It is a **safety net** that the finished app’s brain behaves consistently before you wire Compose screens.

## Typical real-user flow (what UI will mirror)

1. User logs habits/mood/expenses (Today tab → Room).
2. Insights loads `buildDailyDataRange(start, end)` and `getHabitIds()`.
3. User picks strategy mode (or app defaults): predict / cluster / correlate.
4. Optional: `StrategyExecutor` logs the run (with the appropriate `featureSchemaVersion`) for thesis metrics later.

Synthetic seed + export button in `MainActivity` is a **dev shortcut** for step 1–2 without full product UI.
