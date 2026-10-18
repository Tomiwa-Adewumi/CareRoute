package com.careroute.ingestion;
import com.careroute.data.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class WaitTimeIngestionServiceTest {
    @Test void commitFailureIsReportedWithoutExposingExceptionDetails() {
        var feed = mock(ErCompassClient.class);
        var hospitals = mock(HospitalRepository.class);
        var observations = mock(WaitTimeObservationRepository.class);
        when(observations.findTopByOrderByObservedAtDesc()).thenReturn(Optional.empty());
        var health = new IngestionHealthService(observations);
        var transactions = mock(TransactionTemplate.class);
        when(feed.fetchCurrent()).thenReturn(List.of());
        when(transactions.execute(any())).thenThrow(new DataAccessResourceFailureException("private connection URL"));
        new WaitTimeIngestionService(feed, hospitals, observations, "Toronto", health, transactions, Clock.systemUTC()).ingest();
        assertThat(health.status().status()).isEqualTo("DEGRADED");
        assertThat(health.status().lastSuccess()).isNull();
        assertThat(health.status().lastError()).isEqualTo("DataAccessResourceFailureException");
        assertThat(health.status().consecutiveFailures()).isEqualTo(1);
    }
}
