package com.careroute.ingestion;

import com.careroute.data.WaitTimeObservationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class IngestionHealthService {
    private final WaitTimeObservationRepository observations;
    private volatile Instant lastAttempt;
    private volatile Instant lastSuccess;
    private volatile String lastError;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile int rowsSeen;
    private volatile int rowsSaved;

    public IngestionHealthService(WaitTimeObservationRepository observations) { this.observations = observations; }

    void success(int seen, int saved) {
        lastAttempt = Instant.now();
        lastSuccess = lastAttempt;
        lastError = null;
        consecutiveFailures.set(0);
        rowsSeen = seen;
        rowsSaved = saved;
    }

    void failure(Exception exception) {
        lastAttempt = Instant.now();
        lastError = exception.getClass().getSimpleName();
        consecutiveFailures.incrementAndGet();
    }

    public Status status() {
        Instant newest = observations.findTopByOrderByObservedAtDesc().map(o -> o.getObservedAt()).orElse(null);
        long ageMinutes = newest == null ? -1 : Math.max(0, Duration.between(newest, Instant.now()).toMinutes());
        String state = consecutiveFailures.get() > 0 ? "DEGRADED" : newest == null ? "NO_DATA" : ageMinutes > 30 ? "STALE" : "HEALTHY";
        return new Status(state, lastAttempt, lastSuccess, newest, ageMinutes, consecutiveFailures.get(),
                rowsSeen, rowsSaved, observations.count(), lastError);
    }

    public record Status(String status, Instant lastAttempt, Instant lastSuccess, Instant newestObservation,
                         long newestObservationAgeMinutes, int consecutiveFailures, int lastRowsSeen,
                         int lastRowsSaved, long storedObservations, String lastError) {}
}
