package com.careroute.recommendation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class TravelTimeService {
    private static final Logger log = LoggerFactory.getLogger(TravelTimeService.class);
    private final RestClient restClient;
    private final String routingUrl;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>();
    private final Clock clock;

    public TravelTimeService(@Value("${careroute.routing.url:https://router.project-osrm.org}") String routingUrl, Clock clock) {
        this.clock = clock;
        this.routingUrl = routingUrl.replaceAll("/$", "");
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(1));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public TravelEstimate estimate(double fromLat, double fromLng, Double toLat, Double toLng) {
        evictExpired();
        if (toLat == null || toLng == null || !Double.isFinite(toLat) || !Double.isFinite(toLng)
                || Math.abs(toLat) > 90 || Math.abs(toLng) > 180)
            return new TravelEstimate(null, null, "UNAVAILABLE");
        String key = String.format(Locale.ROOT, "%.4f,%.4f:%.4f,%.4f", fromLat, fromLng, toLat, toLng);
        CacheEntry cached;
        synchronized (cache) { cached = cache.get(key); }
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.estimate();

        try {
            String routeUrl = String.format(Locale.ROOT,
                    "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false&alternatives=false&steps=false",
                    routingUrl, fromLng, fromLat, toLng, toLat);
            var response = restClient.get()
                    .uri(URI.create(routeUrl))
                    .retrieve().body(RouteResponse.class);
            if (response != null && response.routes() != null && !response.routes().isEmpty()) {
                Route route = response.routes().get(0);
                if (!Double.isFinite(route.duration()) || !Double.isFinite(route.distance())
                        || route.duration() < 0 || route.distance() < 0) throw new IllegalArgumentException("Invalid route result");
                var estimate = new TravelEstimate(
                        Math.max(1, (int) Math.ceil(route.duration() / 60.0)),
                        Math.round(route.distance() / 100.0) / 10.0,
                        "OSRM_ROUTE");
                store(key, estimate, Duration.ofMinutes(5));
                return estimate;
            }
        } catch (RuntimeException exception) {
            log.warn("Road routing unavailable; using geographic fallback ({})", exception.getClass().getSimpleName());
        }

        double distanceKm = haversineKm(fromLat, fromLng, toLat, toLng);
        var fallback = new TravelEstimate(Math.max(1, (int) Math.round((distanceKm / 30.0) * 60.0 + 5.0)),
                Math.round(distanceKm * 10.0) / 10.0, "HAVERSINE_FALLBACK");
        store(key, fallback, Duration.ofSeconds(30));
        return fallback;
    }

    @Scheduled(fixedDelay = 60000)
    void evictExpired() {
        synchronized (cache) {
            cache.values().removeIf(entry -> !entry.expiresAt().isAfter(clock.instant()));
        }
    }

    private void store(String key, TravelEstimate estimate, Duration ttl) {
        synchronized (cache) {
            if (!cache.containsKey(key) && cache.size() >= 1000) cache.remove(cache.keySet().iterator().next());
            cache.put(key, new CacheEntry(estimate, clock.instant().plus(ttl)));
        }
    }

    static double haversineKm(double fromLat, double fromLng, double toLat, double toLng) {
        double latDistance = Math.toRadians(toLat - fromLat);
        double lngDistance = Math.toRadians(toLng - fromLng);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public record TravelEstimate(Integer minutes, Double distanceKm, String source) {}
    private record CacheEntry(TravelEstimate estimate, Instant expiresAt) {}
    private record RouteResponse(java.util.List<Route> routes) {}
    private record Route(double duration, double distance) {}
}
