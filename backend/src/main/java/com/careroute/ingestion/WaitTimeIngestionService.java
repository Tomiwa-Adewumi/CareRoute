package com.careroute.ingestion;

import com.careroute.data.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;

@Service
public class WaitTimeIngestionService {
    private final ErCompassClient client;
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final String city;
    private final IngestionHealthService health;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public WaitTimeIngestionService(ErCompassClient client, HospitalRepository hospitals,
            WaitTimeObservationRepository observations,
            @Value("${careroute.ingestion.city:Toronto}") String city,
            IngestionHealthService health, TransactionTemplate transactions, Clock clock) {
        this.client = client; this.hospitals = hospitals; this.observations = observations;
        this.city = city; this.health = health; this.transactions = transactions; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${careroute.ingestion.interval-ms:600000}", initialDelayString = "${careroute.ingestion.initial-delay-ms:5000}")
    public void ingest() {
        try {
            var rows = client.fetchCurrent(); // Network I/O must not hold a database transaction.
            Instant collected = clock.instant();
            Integer saved = transactions.execute(status -> {
                int count = 0;
                var seen = new HashSet<String>();
                for (var row : rows) {
                    if (row.city() == null || !row.city().equalsIgnoreCase(city)) continue;
                    seen.add(row.id());
                    var hospital = new HospitalEntity(row.id(), row.name(), row.city(),
                            row.province() == null ? "ON" : row.province(), row.address(), row.latitude(),
                            row.longitude(), row.emergency(), row.pediatric(), true);
                    boolean valid = row.waitMinutes() != null && row.observedAt() != null;
                    hospital.updateAvailability(row.openNow(), valid, collected);
                    hospital = hospitals.save(hospital);
                    if (valid && !observations.existsByHospital_IdAndObservedAtAndWaitMinutes(
                            row.id(), row.observedAt(), row.waitMinutes())) {
                        observations.save(new WaitTimeObservationEntity(hospital, row.waitMinutes(),
                                row.observedAt(), collected, row.observationSource(), row.sourceUrl()));
                        count++;
                    }
                }
                // A successful snapshot is authoritative about facilities no longer reporting.
                for (var hospital : hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")) {
                    if (!seen.contains(hospital.getId())) {
                        hospital.updateAvailability(hospital.getOpenNow(), false, hospital.getLastSeenAt());
                        hospitals.save(hospital);
                    }
                }
                return count;
            });
            health.success(rows.size(), saved == null ? 0 : saved); // Only after commit succeeds.
        } catch (RuntimeException exception) {
            health.failure(exception);
            // Avoid exception messages that may contain infrastructure URLs or credentials.
        }
    }
}
