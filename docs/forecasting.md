# Forecasting and comparison

## Production default

`LATEST_WAIT_BASELINE` uses the latest reported wait as a constant estimate. The comparison adds known travel and a freshness penalty of 0.2 minutes per minute of data age. Facilities with observations or feed presence older than 30 minutes are unavailable, rather than receiving a capped penalty indefinitely.

Unknown travel is null. It excludes a facility from personalized comparison; without location, travel is explicitly omitted for all facilities. Closed, pediatric, unknown-type and unavailable facilities have no score.

## Regression challenger

For each observation, x is minutes since the oldest observation and y is reported wait. Ordinary least squares fits:

```text
slope = Σ((x - mean(x)) × (y - mean(y))) / Σ((x - mean(x))²)
prediction = mean(y) + slope × (arrivalX - mean(x))
```

`predictAt(history, arrival)` accepts the actual arrival instant. If a request is made 20 minutes after the last observation and travel takes 10 minutes, the forecast horizon from that observation is 30 minutes.

The experimental model needs at least three observations and nonzero timestamp variance. Predictions are bounded to 0–720 minutes and within 60 minutes of the latest wait. Fewer samples use a current-wait fallback. It does not control production comparisons.

## Uncertainty

The challenger returns a residual-based heuristic range with a minimum 10-minute radius. This is not a calibrated 95% prediction interval: it does not account for all parameter, horizon, source or clinical uncertainty. The evaluator measures empirical coverage; that statistic is withheld with other errors below ten eligible predictions.

## Promotion policy

Keep the latest-wait baseline until a challenger demonstrates reproducible improvement on representative, genuinely timestamped observations across hospitals and arrival horizons. Assess ranking stability/regret separately before changing production behavior. Aggregate next-observation error and a ten-sample reporting threshold are insufficient to justify promotion.

No model predicts an individual patient's wait or establishes clinical suitability. Triage, staffing, queue composition, time-of-day effects and live traffic are not modeled.
