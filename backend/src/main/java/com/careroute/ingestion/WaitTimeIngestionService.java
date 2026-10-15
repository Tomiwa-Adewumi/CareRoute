package com.careroute.ingestion;

import com.careroute.data.HospitalEntity;
import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationEntity;
import com.careroute.data.WaitTimeObservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class WaitTimeIngestionService {
    private final ErCompassClient client;
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final String city;

    public WaitTimeIngestionService(ErCompassClient client,
                                    HospitalRepository hospitals,
                                    WaitTimeObservationRepository observations,
                                    @Value("${careroute.ingestion.city:Toronto}") String city) {
        this.client = client;
        this.hospitals = hospitals;
        this.observations = observations;
        this.city = city;
    }

    @Scheduled(fixedDelayString = "${careroute.ingestion.interval-ms:600000}", initialDelayString = "${careroute.ingestion.initial-delay-ms:5000}")
    @Transactional
    public void ingest() {
        Instant collectedAt = Instant.now();
        for (ErCompassClient.LiveWait live : client.fetchCurrent()) {
            if (live.city() == null || !live.city().toLowerCase(Locale.ROOT).contains(city.toLowerCase(Locale.ROOT))) {
                continue;
            }

            HospitalEntity hospital = new HospitalEntity(
                    live.id(), live.name(), live.city(), live.province() == null ? "ON" : live.province(),
                    live.address(), live.latitude(), live.longitude(), true, false, true);
            hospital = hospitals.save(hospital);

            observations.save(new WaitTimeObservationEntity(
                    hospital, live.waitMinutes(), live.observedAt(), collectedAt, "ER_COMPASS", live.sourceUrl()));
        }
    }
}
