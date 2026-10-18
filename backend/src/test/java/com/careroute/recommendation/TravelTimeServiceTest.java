package com.careroute.recommendation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
class TravelTimeServiceTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
    }
    @Test void missingCoordinatesAreUnknownRatherThanZero() {
        var service = new TravelTimeService("http://127.0.0.1:1", Clock.systemUTC());
        assertThat(service.estimate(43.6, -79.4, null, null).minutes()).isNull();
    }
    @Test void routeCacheExpiresAndPurgesLocationKeysWithoutAnotherRequest() throws Exception {
        var clock = new MutableClock();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"routes\":[{\"duration\":600,\"distance\":2000}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var service = new TravelTimeService("http://127.0.0.1:" + server.getAddress().getPort(), clock);
            assertThat(service.estimate(43.6, -79.4, 43.65, -79.38).minutes()).isEqualTo(10);
            service.estimate(43.6, -79.4, 43.65, -79.38);
            assertThat(calls.get()).isEqualTo(1);
            clock.now = clock.now.plusSeconds(301);
            service.evictExpired();
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(service, "cache")).isEmpty();
            service.estimate(43.6, -79.4, 43.65, -79.38);
            assertThat(calls.get()).isEqualTo(2);
            // Exercise the bound without 1,001 network calls.
            var estimate = new TravelTimeService.TravelEstimate(10, 2.0, "OSRM_ROUTE");
            for (int i = 0; i < 1001; i++) ReflectionTestUtils.invokeMethod(service, "store", "location-" + i, estimate, Duration.ofMinutes(5));
            assertThat((Map<?, ?>) ReflectionTestUtils.getField(service, "cache")).hasSize(1000);
        } finally { server.stop(0); }
    }
    @Test void routingOutageUsesExplicitFallback() {
        var service = new TravelTimeService("http://127.0.0.1:1", Clock.systemUTC());
        var estimate = service.estimate(43.65, -79.38, 43.7, -79.4);
        assertThat(estimate.minutes()).isPositive();
        assertThat(estimate.source()).isEqualTo("HAVERSINE_FALLBACK");
    }
}
