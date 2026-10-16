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

    public LiveHospitalService(HospitalRepository hospitals, WaitTimeObservationRepository observations,
                               WaitTimeForecastService forecastService) {
        this.hospitals = hospitals;
        this.observations = observations;
        this.forecastService = forecastService;
    }

    public List<Hospital> latest(double userLat, double userLng, boolean hasLocation) {
        List<Hospital> result = new ArrayList<>();
        for (HospitalEntity hospital : hospitals.findByActiveTrueAndProvinceOrderByNameAsc("ON")) {
            observations.findTopByHospital_IdOrderByObservedAtDesc(hospital.getId()).ifPresent(observation -> {
                int travelMinutes = hasLocation
                        ? estimateTravelMinutes(userLat, userLng, hospital.getLatitude(), hospital.getLongitude())
                        : 0;
                var forecast = forecastService.predict(
                        observations.findTop20ByHospital_IdOrderByObservedAtDesc(hospital.getId()), travelMinutes);
                result.add(new Hospital(
                        hospital.getId(),
                        hospital.getName(),
                        observation.getWaitMinutes(),
                        forecast.predictedWaitMinutes(),
                        forecast.sampleCount(),
                        forecast.model(),
                        travelMinutes,
                        observation.getObservedAt(),
                        hospital.isEmergencyDepartment(),
                        hospital.isPediatricEmergency()));
            });
        }
        return result;
    }

    private int estimateTravelMinutes(double userLat, double userLng, Double hospitalLat, Double hospitalLng) {
        if (hospitalLat == null || hospitalLng == null) return 0;
        double earthKm = 6371.0;
        double latDistance = Math.toRadians(hospitalLat - userLat);
        double lngDistance = Math.toRadians(hospitalLng - userLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(hospitalLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double distanceKm = earthKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.max(1, (int) Math.round((distanceKm / 30.0) * 60.0 + 5.0));
    }
}
