# Reproducing evaluation

## Deterministic synthetic check

Run `mvn -Dtest=ModelEvaluationServiceTest test` from `backend/`.
The fixture contains one hospital with waits 40, 50, 60, 70 minutes at
2026-01-01 00:00, 00:10, 00:20, 00:30 UTC. The first three observations train
the model; the fourth is held out. Expected regression MAE: 0 minutes;
latest-wait baseline MAE: 10 minutes. There is one out-of-sample prediction,
so status remains `COLLECTING_HISTORY`. This checks implementation behavior,
not real-world predictive quality.

## Live report

With the backend running and collecting observations:

```bash
python3 scripts/evaluation_report.py http://localhost:8080 > evaluation-report.json
```

The report records capture time, endpoint, and the exact returned metrics.
The endpoint evaluates at most 1,000 recent observations per hospital with
up to 20 preceding observations per prediction. It predicts the next
observed wait, not a patient's clinical wait. `storedObservations` counts
observations in that bounded window. Results vary as new data arrives.
Keep the JSON together with the code commit and a database snapshot when
reproducing a historical run. Do not present the synthetic fixture as a
production accuracy result. Hospital/time-of-day/horizon breakdowns remain
future work; the current endpoint reports aggregate errors and coverage.
