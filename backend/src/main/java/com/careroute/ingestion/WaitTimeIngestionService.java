package com.careroute.ingestion;

import com.careroute.data.HospitalEntity;
import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationEntity;
import com.careroute.data.WaitTimeObservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class WaitTimeIngestionService {
    private static final Logger log = LoggerFactory.getLogger(WaitTimeIngestionService.class);
    private final ErCompassClient client;
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final String city;
    private final int backfillStepMinutes;
    private final IngestionHealthService health;

    public WaitTimeIngestionService(ErCompassClient client,
                                    HospitalRepository hospitals,
                                    WaitTimeObservationRepository observations,
                                    @Value("${careroute.ingestion.city:Toronto}") String city,
                                    @Value("${careroute.ingestion.backfill-step-minutes:15}") int backfillStepMinutes,
                                    IngestionHealthService health) {
        this.client = client;
        this.hospitals = hospitals;
        this.observations = observations;
        this.city = city;
        this.backfillStepMinutes = backfillStepMinutes;
        this.health = health;
    }

    @Scheduled(fixedDelayString = "${careroute.ingestion.interval-ms:600000}", initialDelayString = "${careroute.ingestion.initial-delay-ms:5000}")
    @Transactional
    public void ingest() {
        Instant collectedAt = Instant.now();
        java.util.List<ErCompassClient.LiveWait> liveWaits;
        try {
            liveWaits = client.fetchCurrent();
        } catch (RuntimeException exception) {
            health.failure(exception);
            throw exception;
        }
        int saved = 0;
        for (ErCompassClient.LiveWait live : liveWaits) {
            if (live.city() == null || !live.city().toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
                continue;
            }

            HospitalEntity hospital = new HospitalEntity(
                    live.id(), live.name(), live.city(), live.province() == null ? "ON" : live.province(),
                    live.address(), live.latitude(), live.longitude(), true, false, true);
            hospital = hospitals.save(hospital);

            saved += backfillRecentHistory(hospital, live, collectedAt);

            if (!observations.existsByHospital_IdAndObservedAtAndWaitMinutes(
                    hospital.getId(), live.observedAt(), live.waitMinutes())) {
                observations.save(new WaitTimeObservationEntity(
                        hospital, live.waitMinutes(), live.observedAt(), collectedAt, "ER_COMPASS", live.sourceUrl()));
                saved++;
            }
        }
        health.success(liveWaits.size(), saved);
        log.info("CareRoute ingestion saved {} Toronto observations from {} available rows", saved, liveWaits.size());
    }

    private int backfillRecentHistory(HospitalEntity hospital, ErCompassClient.LiveWait live, Instant collectedAt) {
        String source = "ER_COMPASS_SPARK_BACKFILL";
        if (live.recentWaits().isEmpty() || observations.existsByHospital_IdAndSource(hospital.getId(), source)) {
            return 0;
        }

        int saved = 0;
        int finalIndex = live.recentWaits().size() - 1;
        Instant anchor = live.observedAt().truncatedTo(ChronoUnit.MINUTES);
        for (int index = 0; index <= finalIndex; index++) {
            int waitMinutes = live.recentWaits().get(index);
            Instant observedAt = anchor.minus((long) (finalIndex - index) * backfillStepMinutes, ChronoUnit.MINUTES);
            if (!observations.existsByHospital_IdAndObservedAtAndWaitMinutes(hospital.getId(), observedAt, waitMinutes)) {
                observations.save(new WaitTimeObservationEntity(
                        hospital, waitMinutes, observedAt, collectedAt, source, live.sourceUrl()));
                saved++;
            }
        }
        log.info("Backfilled {} recent observations for {} from the public ER Compass sparkline", saved, hospital.getId());
        return saved;
    }
}
