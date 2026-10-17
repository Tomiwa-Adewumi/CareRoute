# Forecasting and evaluation

## The math

The forecasting problem is a one-feature supervised regression problem fit independently for each hospital. For observation `i`:

- `x_i` is the number of minutes since the oldest observation in the window.
- `y_i` is the reported emergency-department wait in minutes.
- The target is the expected wait at the user's estimated arrival time.

Ordinary least squares chooses the line that minimizes the sum of squared residuals:

```text
minimize  Σ(y_i - (β₀ + β₁x_i))²
  β₀,β₁   i
```

The implementation computes the slope and intercept in closed form:

```text
β₁ = Σ(x_i - x̄)(y_i - ȳ) / Σ(x_i - x̄)²
β₀ = ȳ - β₁x̄
```

If the latest observation is at `x_latest` and travel takes `t_travel` minutes, the arrival-time prediction is:

```text
ŷ_arrival = β₀ + β₁(x_latest + t_travel)
```

The recommendation objective is then:

```text
score(h) = ŷ_arrival(h) + t_travel(h) + 0.20 × min(data_age_minutes(h), 120)
```

Lower is better. The freshness term adds up to 24 penalty minutes, making stale observations less competitive without silently discarding them.

### Why this model?

Linear regression is an intentional baseline: it is fast, deterministic, works with small per-hospital windows, and exposes a slope that can be inspected. A more complex model is not automatically a better product decision—especially before enough durable history exists to evaluate it. This baseline creates a measurable benchmark for future time-series models.

Runtime per hospital is `O(n)` time over at most 20 observations and `O(1)` additional model space. Training happens during the recommendation request; there is no serialized model artifact in this version.

### Guardrails and failure modes

- Fewer than three samples → use the current reported wait and return `CURRENT_WAIT_FALLBACK`.
- Zero variance in observation timestamps → fall back instead of dividing by zero.
- Final predictions are limited to the valid `[0, 720]` minute range.
- Extrapolation is limited to `current wait ± 60 minutes` to reduce sensitivity to short-lived spikes.
- The model is per hospital, so one hospital's scale or trend cannot dominate another's fit.
- Geolocation affects the forecast horizon and travel component; coordinates are not persisted.

The current model does **not** capture hour-of-day, day-of-week, triage acuity, queue length, staffing, traffic routes, calibrated clinical uncertainty, or causal effects. Its residual-based interval communicates model spread but is not a clinically validated confidence interval. This remains an interpretable prototype forecast—not a clinical predictor.

## Evaluation

CareRoute implements a rolling-origin backtest. For each timestamp, the evaluator trains only on earlier observations and predicts the next reported wait, preventing future-data leakage. It compares the regression model with the latest reported wait and a three-observation moving average.

Reported measurements:

| Metric / check | Why it matters |
| --- | --- |
| MAE in minutes | Easy-to-interpret average forecast error |
| RMSE in minutes | Penalizes occasional large misses |
| Median absolute error | More robust to extreme wait-time spikes |
| Baseline comparison | Tests whether regression beats “use the latest wait” |


| Prediction-interval coverage | Quantifies whether displayed uncertainty is calibrated |

The live `/api/model/metrics` endpoint reports stored observations, out-of-sample prediction count, MAE, RMSE, median absolute error, baseline errors, and interval coverage. The UI remains in `COLLECTING_HISTORY` until at least 10 out-of-sample predictions exist. No accuracy number is claimed before that threshold, and the current free demo's in-memory database can reset on deployment.

## Model card

| Field | Value |
| --- | --- |
| Model | `LINEAR_REGRESSION_V1` |
| Type | Per-hospital ordinary least squares regression |
| Intended use | Estimate wait at arrival to support an explainable ED ranking |
| Inputs | Recent waits, timestamps, and travel-time forecast horizon |
| Output | Bounded wait prediction with a residual-based 95% interval |
| Not intended for | Diagnosis, triage, or clinical decision-making |
| Minimum history | Three observations; otherwise use the current-wait fallback |
| Evaluation | Leakage-safe rolling-origin backtest against two baselines |
| Known gaps | No route traffic, staffing, acuity, calibrated clinical uncertainty, or external validation |

The same machine-readable card is available from `GET /api/model/card`.

## ML engineering decisions demonstrated

- Turns a user decision into an explicit, inspectable optimization objective.
- Separates ingestion, persistence, forecasting, ranking, and presentation layers.
- Handles sparse data and degenerate features with named fallbacks.
- Prevents unbounded extrapolation at an operational decision boundary.
- Returns model metadata with every prediction for observability and UI transparency.
- Includes a deterministic unit test where waits `40 → 50 → 60` predict `70` ten minutes later.
- Documents leakage-safe evaluation before claiming model quality.


## Planned evaluation

Breakdowns by forecast horizon, hospital, and time of day are planned; the current endpoint reports aggregate metrics only. Evaluation is bounded to the latest 1,000 observations per hospital. `storedObservations` counts that evaluated window, not the entire database.
