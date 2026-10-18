package com.careroute.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TravelTimeService {
    private final RestClient restClient = RestClient.create();
    private final String routingUrl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TravelTimeService(@Value("${careroute.routing.url:https://router.project-osrm.org}") String routingUrl) {
        this.routingUrl = routingUrl.replaceAll("/$", "");
    }

    public TravelEstimate estimate(double fromLat, double fromLng, Double toLat, Double toLng) {
        if (toLat == null || toLng == null) return new TravelEstimate(0, 0, "UNAVAILABLE");
        String key = "%.4f,%.4f:%.4f,%.4f".formatted(fromLat, fromLng, toLat, toLng);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.estimate();

        try {
            var response = restClient.get()
                    .uri(routingUrl + "/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=false&alternatives=false&steps=false",
                            fromLng, fromLat, toLng, toLat)
                    .retrieve().body(RouteResponse.class);
            if (response != null && response.routes() != null && !response.routes().isEmpty()) {
                Route route = response.routes().get(0);
                var estimate = new TravelEstimate(
                        Math.max(1, (int) Math.ceil(route.duration() / 60.0)),
                        Math.round(route.distance() / 100.0) / 10.0,
                        "OSRM_ROUTE");
                cache.put(key, new CacheEntry(estimate, Instant.now().plus(Duration.ofMinutes(5))));
                return estimate;
            }
        } catch (RuntimeException ignored) {
            // The deterministic fallback keeps recommendations available during routing outages.
        }

        double distanceKm = haversineKm(fromLat, fromLng, toLat, toLng);
        return new TravelEstimate(Math.max(1, (int) Math.round((distanceKm / 30.0) * 60.0 + 5.0)),
                Math.round(distanceKm * 10.0) / 10.0, "HAVERSINE_FALLBACK");
    }

    static double haversineKm(double fromLat, double fromLng, double toLat, double toLng) {
        double latDistance = Math.toRadians(toLat - fromLat);
        double lngDistance = Math.toRadians(toLng - fromLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record TravelEstimate(int minutes, double distanceKm, String source) {}
    private record CacheEntry(TravelEstimate estimate, Instant expiresAt) {}
    private record RouteResponse(java.util.List<Route> routes) {}
    private record Route(double duration, double distance) {}
}
