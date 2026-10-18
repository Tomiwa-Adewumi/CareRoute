package com.careroute.recommendation;

import com.careroute.data.HospitalEntity;
import com.careroute.data.HospitalRepository;
import com.careroute.data.WaitTimeObservationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LiveHospitalService {
    private final HospitalRepository hospitals;
    private final WaitTimeObservationRepository observations;
    private final WaitTimeForecastService forecastService;
    private final TravelTimeService travelTimeService;

    public LiveHospitalService(HospitalRepository hospitals, WaitTimeObservationRepository observations,
                               WaitTimeForecastService forecastService, TravelTimeService travelTimeService) {
        this.hospitals = hospitals;
        this.observations = observations;
        this.forecastService = forecastService;
        this.travelTimeService = travelTimeService;
    }

    public List<Hospital> latest(double userLat, double userLng, boolean hasLocation) {
        List<Hospital> result = new ArrayList<>();
        for (HospitalEntity hospital : hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")) {
            observations.findTopByHospital_IdOrderByObservedAtDesc(hospital.getId()).ifPresent(observation -> {
                var travel = hasLocation
                        ? travelTimeService.estimate(userLat, userLng, hospital.getLatitude(), hospital.getLongitude())
                        : new TravelTimeService.TravelEstimate(0, 0, "LOCATION_NOT_PROVIDED");
                var forecast = forecastService.predict(
                        observations.findTop20ByHospital_IdOrderByObservedAtDesc(hospital.getId()), travel.minutes());
                result.add(new Hospital(
                        hospital.getId(),
                        hospital.getName(),
                        observation.getWaitMinutes(),
                        forecast.predictedWaitMinutes(),
                        forecast.predictionLowerMinutes(),
                        forecast.predictionUpperMinutes(),
                        forecast.sampleCount(),
                        forecast.model(),
                        travel.minutes(),
                        travel.distanceKm(),
                        travel.source(),
                        hospital.getLatitude(),
                        hospital.getLongitude(),
                        observation.getObservedAt(),
                        hospital.isEmergencyDepartment(),
                        hospital.isPediatricEmergency()));
            });
        }
        return result;
    }

}
