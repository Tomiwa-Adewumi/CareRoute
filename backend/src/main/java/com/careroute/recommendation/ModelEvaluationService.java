package com.careroute.recommendation;

import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationEntity;
import com.careroute.data.WaitTimeObservationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ModelEvaluationService {
    private static final int MIN_REPORTABLE_PREDICTIONS = 10;
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final WaitTimeForecastService forecasts;
    private final ExponentialSmoothingForecastService smoothingForecasts;

    public ModelEvaluationService(HospitalRepository hospitals, WaitTimeObservationRepository observations,
                                  WaitTimeForecastService forecasts,
                                  ExponentialSmoothingForecastService smoothingForecasts) {
        this.hospitals = hospitals;
        this.observations = observations;
        this.forecasts = forecasts;
        this.smoothingForecasts = smoothingForecasts;
    }

    public Evaluation evaluate() {
        List<Double> regressionErrors = new ArrayList<>();
        List<Double> naiveErrors = new ArrayList<>();
        List<Double> movingAverageErrors = new ArrayList<>();
        List<Double> exponentialSmoothingErrors = new ArrayList<>();
        int covered = 0;
        int totalObservations = 0;
        int hospitalsWithForecast = 0;
        Instant newest = null;

        for (var hospital : hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")) {
            List<WaitTimeObservationEntity> series = new ArrayList<>(
                    observations.findTop1000ByHospital_IdOrderByObservedAtDesc(hospital.getId()));
            Collections.reverse(series);
            totalObservations += series.size();
            if (series.size() >= 3) hospitalsWithForecast++;
            if (!series.isEmpty() && (newest == null || series.get(series.size() - 1).getObservedAt().isAfter(newest))) {
                newest = series.get(series.size() - 1).getObservedAt();
            }

            // Rolling-origin evaluation: every target is predicted using only earlier observations.
            for (int targetIndex = 3; targetIndex < series.size(); targetIndex++) {
                int start = Math.max(0, targetIndex - 20);
                List<WaitTimeObservationEntity> history = new ArrayList<>(series.subList(start, targetIndex));
                Collections.reverse(history);
                var target = series.get(targetIndex);
                int horizon = Math.max(0, (int) Duration.between(
                        history.get(0).getObservedAt(), target.getObservedAt()).toMinutes());
                var forecast = forecasts.predict(history, horizon);
                if (!"LINEAR_REGRESSION_V1".equals(forecast.model())) continue;

                int actual = target.getWaitMinutes();
                regressionErrors.add((double) Math.abs(actual - forecast.predictedWaitMinutes()));
                naiveErrors.add((double) Math.abs(actual - history.get(0).getWaitMinutes()));
                double movingAverage = history.stream().limit(3)
                        .mapToInt(WaitTimeObservationEntity::getWaitMinutes).average().orElse(actual);
                movingAverageErrors.add(Math.abs(actual - movingAverage));
                var smoothing = smoothingForecasts.predict(history);
                exponentialSmoothingErrors.add((double) Math.abs(actual - smoothing.predictedWaitMinutes()));
                if (actual >= forecast.predictionLowerMinutes() && actual <= forecast.predictionUpperMinutes()) covered++;
            }
        }

        int predictions = regressionErrors.size();
        return new Evaluation(
                predictions >= MIN_REPORTABLE_PREDICTIONS ? "EVALUATED" : "COLLECTING_HISTORY",
                predictions,
                totalObservations,
                hospitalsWithForecast,
                newest,
                metrics(regressionErrors),
                metrics(naiveErrors),
                metrics(movingAverageErrors),
                metrics(exponentialSmoothingErrors),
                predictions == 0 ? null : round((covered * 100.0) / predictions),
                MIN_REPORTABLE_PREDICTIONS,
                "Rolling-origin backtest over at most 1000 recent observations per hospital; each prediction uses only earlier observations");
    }

    private Metrics metrics(List<Double> errors) {
        if (errors.isEmpty()) return null;
        double mae = errors.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rmse = Math.sqrt(errors.stream().mapToDouble(e -> e * e).average().orElse(0));
        List<Double> sorted = new ArrayList<>(errors);
        Collections.sort(sorted);
        double median = sorted.size() % 2 == 0
                ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
                : sorted.get(sorted.size() / 2);
        return new Metrics(round(mae), round(rmse), round(median));
    }

    private double round(double value) { return Math.round(value * 10.0) / 10.0; }

    public record Metrics(double maeMinutes, double rmseMinutes, double medianAbsoluteErrorMinutes) {}
    public record Evaluation(String status, int evaluatedPredictions, int storedObservations,
                             int hospitalsWithActiveForecast, Instant newestObservation,
                             Metrics linearRegression, Metrics latestWaitBaseline,
                             Metrics movingAverageBaseline, Metrics exponentialSmoothing,
                             Double intervalCoveragePercent,
                             int minimumPredictionsForReporting, String methodology) {}
}
