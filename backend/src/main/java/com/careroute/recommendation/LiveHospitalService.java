package com.careroute.recommendation;

import com.careroute.data.*;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class LiveHospitalService {
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final TravelTimeService travelTimeService;
    private final Clock clock;
    public LiveHospitalService(HospitalRepository hospitals, WaitTimeObservationRepository observations,
                               TravelTimeService travelTimeService, Clock clock) {
        this.hospitals = hospitals; this.observations = observations;
        this.travelTimeService = travelTimeService; this.clock = clock;
    }
    public List<Hospital> latest(double userLat, double userLng, boolean hasLocation) {
        List<Hospital> result = new ArrayList<>();
        for (HospitalEntity hospital : hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")) {
            var observation = observations.findTopByHospital_IdAndSourceInOrderByObservedAtDescCollectedAtDesc(hospital.getId(),
                    List.of("ER_COMPASS_OBSERVED", "ER_COMPASS_FETCH_TIME")).orElse(null);
            String reason = null;
            if (!hospital.isEmergencyDepartment()) reason = "Facility type is not verified as an emergency department";
            else if (hospital.isPediatricEmergency()) reason = "Pediatric facility: outside this adult comparison";
            else if (!Boolean.TRUE.equals(hospital.getOpenNow())) reason = "Facility is closed or opening status is unknown";
            else if (!hospital.isWaitAvailable() || observation == null) reason = "Current wait is unavailable";
            else if (hospital.getLastSeenAt() == null || Duration.between(hospital.getLastSeenAt(), clock.instant()).compareTo(Duration.ofMinutes(30)) > 0
                    || Duration.between(observation.getObservedAt(), clock.instant()).compareTo(Duration.ofMinutes(30)) > 0)
                reason = "Data is more than 30 minutes old";
            else if (observation.getObservedAt().isAfter(clock.instant().plusSeconds(60))) reason = "Invalid observation timestamp";
            var travel = hasLocation && reason == null
                    ? travelTimeService.estimate(userLat, userLng, hospital.getLatitude(), hospital.getLongitude())
                    : new TravelTimeService.TravelEstimate(null, null, hasLocation ? "UNAVAILABLE" : "LOCATION_NOT_PROVIDED");
            if (reason == null && hasLocation && travel.minutes() == null) reason = "Travel estimate is unavailable";
            Integer wait = observation == null ? null : observation.getWaitMinutes();
            result.add(new Hospital(hospital.getId(), hospital.getName(), wait, wait, null, null,
                    observation == null ? 0 : 1, "LATEST_WAIT_BASELINE", travel.minutes(), travel.distanceKm(), travel.source(),
                    hospital.getLatitude(), hospital.getLongitude(), observation == null ? null : observation.getObservedAt(),
                    hospital.isEmergencyDepartment(), hospital.isPediatricEmergency(), reason,
                    observation == null ? "UNKNOWN" : observation.getSource()));
        }
        return result;
    }
}
