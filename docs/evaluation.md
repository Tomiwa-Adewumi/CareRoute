# Reproducing evaluation

Run `mvn -Dtest=ModelEvaluationServiceTest test` from `backend/`.

The deterministic fixtures check:

- Four observations produce one eligible prediction, but errors and coverage remain null because the reporting threshold is ten.
- Fourteen observations, ten minutes apart with waits increasing by ten minutes, produce eleven targets, regression MAE 0 and latest-wait MAE 10.
- History collected after a target cannot be used to predict that target.

These synthetic checks establish implementation behavior, not real-world accuracy.

## Primary protocol

For each active Ontario facility, the evaluator reads at most 1,000 observations whose source is `ER_COMPASS_OBSERVED`. Legacy `ER_COMPASS`, `ER_COMPASS_SPARK_BACKFILL`, and `ER_COMPASS_FETCH_TIME` rows are excluded. No timestamp is reconstructed from a sparkline.

Targets are visited in timestamp order. Training uses at most twenty preceding rows with strictly earlier observation timestamps and collection times no later than the target. At least three eligible history rows are required. All compared methods use identical targets. The forecast issue time is the latest collection time in the training window; targets must be 5–60 minutes after that issue time. Regression predicts at the target's explicit timestamp, including any age of the latest observed value. Conflicting revisions sharing an observation timestamp are excluded entirely; near-duplicate targets outside the lead-time window are excluded too.

The endpoint returns aggregate MAE, RMSE, median absolute error and empirical heuristic-range coverage only after ten eligible targets. Counts remain visible beforehand. `storedObservations` counts eligible observations in the bounded query, not all database rows. `EVALUATED` means the reporting threshold was met; it is not a validation certificate.

This evaluates the next observed reported wait. It does not directly evaluate patient outcomes or hospital rankings. Hospital, time-of-day and fixed-arrival-horizon breakdowns and prospective ranking evaluation are required before model promotion.

## Capture a report

```bash
python3 scripts/evaluation_report.py http://localhost:8080 > evaluation-report.json
```

Keep the report, code commit, configuration and a database snapshot together for historical reproduction. Current reports may remain in `COLLECTING_HISTORY` if the source lacks verified observation timestamps. The older 47-target backfilled snapshot is not comparable with this stricter protocol and must not be described as validated production accuracy.
