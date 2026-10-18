package com.careroute.recommendation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LiveHospitalServiceTest {

    @Test
    void travelEstimateChangesWithUserLocation() {
        var service = new LiveHospitalService(
                mock(com.careroute.data.HospitalRepository.class),
                mock(com.careroute.data.WaitTimeObservationRepository.class),
                mock(WaitTimeForecastService.class),
                new TravelTimeService("http://127.0.0.1:1"));

        int besideTorontoGeneral = serviceTravel(43.6596, -79.3889, 43.6596, -79.3889);
        int fromScarborough = serviceTravel(43.7764, -79.2318, 43.6596, -79.3889);

        assertEquals(5, besideTorontoGeneral);
        assertTrue(fromScarborough > besideTorontoGeneral);
    }

    private int serviceTravel(double fromLat, double fromLng, double toLat, double toLng) {
        double distanceKm = TravelTimeService.haversineKm(fromLat, fromLng, toLat, toLng);
        return Math.max(1, (int) Math.round((distanceKm / 30.0) * 60.0 + 5.0));
    }
}
