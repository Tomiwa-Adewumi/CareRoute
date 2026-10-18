package com.careroute.recommendation;
import com.careroute.data.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class ModelEvaluationServiceTest {
    private final HospitalRepository hospitals = mock(HospitalRepository.class);
    private final WaitTimeObservationRepository observations = mock(WaitTimeObservationRepository.class);
    private final HospitalEntity h = new HospitalEntity("h", "Hospital", "Toronto", "ON", null, 43.65, -79.38, true, false, true);
    private final ModelEvaluationService evaluator = new ModelEvaluationService(hospitals, observations, new WaitTimeForecastService(), new ExponentialSmoothingForecastService());
    private void setup(int count, boolean lateCollection) {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        var series = new ArrayList<WaitTimeObservationEntity>();
        for (int i = 0; i < count; i++) {
            var at = start.plusSeconds(i * 600L);
            series.add(new WaitTimeObservationEntity(h, 40 + i * 10, at, lateCollection ? start.plusSeconds(86400) : at, "ER_COMPASS_OBSERVED", null));
        }
        Collections.reverse(series);
        when(hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")).thenReturn(List.of(h));
        when(observations.findTop1000ByHospital_IdAndSourceOrderByObservedAtDesc("h", "ER_COMPASS_OBSERVED")).thenReturn(series);
    }
    @Test void withholdsMetricsBelowReportingThreshold() {
        setup(4, false);
        var result = evaluator.evaluate();
        assertThat(result.evaluatedPredictions()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("COLLECTING_HISTORY");
        assertThat(result.linearRegression()).isNull();
        assertThat(result.latestWaitBaseline()).isNull();
        assertThat(result.intervalCoveragePercent()).isNull();
    }
    @Test void evaluatesIdenticalTargetsUsingOnlyVerifiedTimestampHistory() {
        setup(14, false);
        var result = evaluator.evaluate();
        assertThat(result.evaluatedPredictions()).isEqualTo(11);
        assertThat(result.linearRegression().maeMinutes()).isZero();
        assertThat(result.latestWaitBaseline().maeMinutes()).isEqualTo(10);
        verify(observations).findTop1000ByHospital_IdAndSourceOrderByObservedAtDesc("h", "ER_COMPASS_OBSERVED");
        verifyNoMoreInteractions(observations);
    }
    @Test void excludesHistoryThatWasNotAvailableByTargetTime() {
        setup(14, true);
        assertThat(evaluator.evaluate().evaluatedPredictions()).isZero();
    }
}
