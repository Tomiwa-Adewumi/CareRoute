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
                mock(WaitTimeForecastService.class));

        int besideTorontoGeneral = service.estimateTravelMinutes(43.6596, -79.3889, 43.6596, -79.3889);
        int fromScarborough = service.estimateTravelMinutes(43.7764, -79.2318, 43.6596, -79.3889);

        assertEquals(5, besideTorontoGeneral);
        assertTrue(fromScarborough > besideTorontoGeneral);
    }
}
