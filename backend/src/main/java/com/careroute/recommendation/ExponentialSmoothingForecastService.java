package com.careroute.recommendation;

import com.careroute.data.WaitTimeObservationEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExponentialSmoothingForecastService {
    private static final double ALPHA = 0.5;

    public Forecast predict(List<WaitTimeObservationEntity> newestFirst) {
        if (newestFirst.isEmpty()) return new Forecast(0, 0, "NO_DATA");

        double level = newestFirst.get(newestFirst.size() - 1).getWaitMinutes();
        for (int index = newestFirst.size() - 2; index >= 0; index--) {
            level = ALPHA * newestFirst.get(index).getWaitMinutes() + (1.0 - ALPHA) * level;
        }
        return new Forecast((int) Math.round(level), newestFirst.size(), "EXPONENTIAL_SMOOTHING_V1");
    }

    public record Forecast(int predictedWaitMinutes, int sampleCount, String model) {}
}
