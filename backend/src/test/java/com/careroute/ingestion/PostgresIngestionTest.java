package com.careroute.ingestion;

import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL}",
    "spring.datasource.username=${TEST_DATABASE_USERNAME:postgres}",
    "spring.datasource.password=${TEST_DATABASE_PASSWORD:postgres}",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
    "careroute.ingestion.initial-delay-ms=3600000"
})
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostgresIngestionTest {
    @Autowired WaitTimeIngestionService ingestion;
    @Autowired WaitTimeObservationRepository observations;
    @Autowired HospitalRepository hospitals;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ErCompassClient client;

    @BeforeEach void clear() { observations.deleteAll(); hospitals.deleteAll(); }

    @Test void migratedSchemaDeduplicatesRepeatedFeedAndKeepsNewHistory() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        when(client.fetchCurrent()).thenReturn(List.of(row(at, 40)));
        ingestion.ingest(); ingestion.ingest();
        assertEquals(1, observations.count());
        when(client.fetchCurrent()).thenReturn(List.of(row(at.plusSeconds(600), 50)));
        ingestion.ingest();
        assertEquals(2, observations.count());
        assertEquals(50, observations.findTopByHospital_IdOrderByObservedAtDesc("test-hospital").orElseThrow().getWaitMinutes());
        assertEquals(2, jdbc.queryForObject("select count(*) from flyway_schema_history where success = true and type = 'SQL'", Integer.class));
    }

    @Test void databaseRejectsDuplicateIdentityEvenWithoutApplicationCheck() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        when(client.fetchCurrent()).thenReturn(List.of(row(at, 40)));
        ingestion.ingest();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            jdbc.update("insert into wait_time_observations select ?, hospital_id, wait_minutes, observed_at, collected_at, source, source_url from wait_time_observations limit 1", java.util.UUID.randomUUID()));
    }
    private ErCompassClient.LiveWait row(Instant at, int wait) {
        return new ErCompassClient.LiveWait("test-hospital", "Test Hospital", "Toronto", "ON", null,
                43.65, -79.38, wait, at, "https://example.com/fixture");
    }
}
