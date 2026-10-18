package com.careroute.recommendation;
import com.careroute.data.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class LiveHospitalServiceTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final HospitalRepository hospitals = mock(HospitalRepository.class);
    private final WaitTimeObservationRepository observations = mock(WaitTimeObservationRepository.class);
    private final TravelTimeService travel = mock(TravelTimeService.class);
    private final LiveHospitalService service = new LiveHospitalService(hospitals, observations, travel, Clock.fixed(now, ZoneOffset.UTC));
    private HospitalEntity setup(boolean pediatric, Boolean open, boolean available, Instant observed) {
        var h = new HospitalEntity("h", "Hospital", "Toronto", "ON", null, 43.65, -79.38, true, pediatric, true);
        h.updateAvailability(open, available, now);
        when(hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")).thenReturn(List.of(h));
        when(observations.findTopByHospital_IdAndSourceInOrderByObservedAtDescCollectedAtDesc(eq("h"), anyList()))
                .thenReturn(observed == null ? Optional.empty() : Optional.of(new WaitTimeObservationEntity(h, 60, observed, observed, "ER_COMPASS_OBSERVED", null)));
        return h;
    }
    @Test void passesUserLocationToRoutingAndUsesLatestWaitBaseline() {
        setup(false, true, true, now.minusSeconds(600));
        when(travel.estimate(43.7, -79.4, 43.65, -79.38)).thenReturn(new TravelTimeService.TravelEstimate(12, 5.0, "OSRM_ROUTE"));
        var result = service.latest(43.7, -79.4, true).get(0);
        verify(travel).estimate(43.7, -79.4, 43.65, -79.38);
        assertThat(result.travelMinutes()).isEqualTo(12);
        assertThat(result.predictedWaitMinutes()).isEqualTo(60);
        assertThat(result.forecastModel()).isEqualTo("LATEST_WAIT_BASELINE");
        assertThat(result.predictionLowerMinutes()).isNull();
    }
    @Test void missingTravelCannotReceivePersonalizedScore() {
        setup(false, true, true, now);
        when(travel.estimate(anyDouble(), anyDouble(), any(), any())).thenReturn(new TravelTimeService.TravelEstimate(null, null, "UNAVAILABLE"));
        var result = service.latest(43.7, -79.4, true);
        assertThat(result.get(0).travelMinutes()).isNull();
        assertThat(new RecommendationService(Clock.fixed(now, ZoneOffset.UTC)).rank(result).get(0).score()).isNull();
    }
    @Test void noLocationExcludesTravelWithoutPretendingToKnowIt() {
        setup(false, true, true, now);
        var result = service.latest(0, 0, false).get(0);
        assertThat(result.unavailableReason()).isNull();
        assertThat(result.travelMinutes()).isNull();
        verifyNoInteractions(travel);
    }
    @Test void unavailableFacilitiesRemainVisibleWithoutRouting() {
        setup(false, true, false, now);
        assertThat(service.latest(43.7, -79.4, true).get(0).unavailableReason()).contains("unavailable");
        setup(true, true, true, now);
        assertThat(service.latest(43.7, -79.4, true).get(0).unavailableReason()).contains("Pediatric");
        setup(false, false, true, now);
        assertThat(service.latest(43.7, -79.4, true).get(0).unavailableReason()).contains("closed");
        setup(false, null, true, now);
        assertThat(service.latest(43.7, -79.4, true).get(0).unavailableReason()).contains("unknown");
        setup(false, true, true, now.minusSeconds(3600));
        assertThat(service.latest(43.7, -79.4, true).get(0).unavailableReason()).contains("30 minutes");
        setup(false, true, false, null);
        assertThat(service.latest(43.7, -79.4, true).get(0).waitMinutes()).isNull();
        verifyNoInteractions(travel);
    }
}
