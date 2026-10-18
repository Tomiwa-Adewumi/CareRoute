package com.careroute.recommendation;

import com.careroute.data.WaitTimeObservationEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class WaitTimeForecastService {

    public Forecast predictAt(List<WaitTimeObservationEntity> newestFirst, java.time.Instant arrival) {
        if (newestFirst.isEmpty()) return predict(newestFirst, 0);
        long minutes = Math.max(0, Duration.between(newestFirst.get(0).getObservedAt(), arrival).toMinutes());
        return predict(newestFirst, (int) Math.min(1440, minutes));
    }

    public Forecast predict(List<WaitTimeObservationEntity> newestFirst, int travelMinutes) {
        if (newestFirst.isEmpty()) return new Forecast(0, 0, 0, 0, "NO_DATA");

        int current = newestFirst.get(0).getWaitMinutes();
        if (newestFirst.size() < 3) return new Forecast(current, current, current, newestFirst.size(), "CURRENT_WAIT_FALLBACK");

        var oldest = newestFirst.get(newestFirst.size() - 1).getObservedAt();
        double meanX = 0;
        double meanY = 0;
        for (var observation : newestFirst) {
            meanX += Duration.between(oldest, observation.getObservedAt()).toSeconds() / 60.0;
            meanY += observation.getWaitMinutes();
        }
        meanX /= newestFirst.size();
        meanY /= newestFirst.size();

        double numerator = 0;
        double denominator = 0;
        for (var observation : newestFirst) {
            double x = Duration.between(oldest, observation.getObservedAt()).toSeconds() / 60.0;
            numerator += (x - meanX) * (observation.getWaitMinutes() - meanY);
            denominator += (x - meanX) * (x - meanX);
        }

        if (denominator == 0) return new Forecast(current, current, current, newestFirst.size(), "CURRENT_WAIT_FALLBACK");
        double slopePerMinute = numerator / denominator;
        double intercept = meanY - slopePerMinute * meanX;
        double squaredResiduals = 0;
        for (var observation : newestFirst) {
            double x = Duration.between(oldest, observation.getObservedAt()).toSeconds() / 60.0;
            double residual = observation.getWaitMinutes() - (intercept + slopePerMinute * x);
            squaredResiduals += residual * residual;
        }
        double residualRmse = Math.sqrt(squaredResiduals / newestFirst.size());
        double latestX = Duration.between(oldest, newestFirst.get(0).getObservedAt()).toSeconds() / 60.0;
        int rawPrediction = (int) Math.round(meanY + slopePerMinute * (latestX + travelMinutes - meanX));
        int boundedPrediction = Math.max(0, Math.min(720, Math.max(current - 60, Math.min(current + 60, rawPrediction))));
        int intervalRadius = Math.max(10, (int) Math.round(1.96 * residualRmse));
        int lower = Math.max(0, boundedPrediction - intervalRadius);
        int upper = Math.min(720, boundedPrediction + intervalRadius);
        return new Forecast(boundedPrediction, lower, upper, newestFirst.size(), "LINEAR_REGRESSION_V1");
    }

    public record Forecast(int predictedWaitMinutes, int predictionLowerMinutes, int predictionUpperMinutes,
                           int sampleCount, String model) {}
}
