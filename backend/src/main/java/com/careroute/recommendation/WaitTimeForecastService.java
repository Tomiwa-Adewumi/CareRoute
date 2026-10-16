package com.careroute.recommendation;

import com.careroute.data.WaitTimeObservationEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class WaitTimeForecastService {

    public Forecast predict(List<WaitTimeObservationEntity> newestFirst, int travelMinutes) {
        if (newestFirst.isEmpty()) return new Forecast(0, 0, "NO_DATA");

        int current = newestFirst.get(0).getWaitMinutes();
        if (newestFirst.size() < 3) return new Forecast(current, newestFirst.size(), "CURRENT_WAIT_FALLBACK");

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

        if (denominator == 0) return new Forecast(current, newestFirst.size(), "CURRENT_WAIT_FALLBACK");
        double slopePerMinute = numerator / denominator;
        double latestX = Duration.between(oldest, newestFirst.get(0).getObservedAt()).toSeconds() / 60.0;
        int rawPrediction = (int) Math.round(meanY + slopePerMinute * (latestX + travelMinutes - meanX));
        int boundedPrediction = Math.max(0, Math.min(720, Math.max(current - 60, Math.min(current + 60, rawPrediction))));
        return new Forecast(boundedPrediction, newestFirst.size(), "LINEAR_REGRESSION_V1");
    }

    public record Forecast(int predictedWaitMinutes, int sampleCount, String model) {}
}
