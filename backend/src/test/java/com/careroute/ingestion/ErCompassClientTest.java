package com.careroute.ingestion;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class ErCompassClientTest {
    @Test void preservesUnavailableAndPediatricFacilitiesAndTimestampProvenance() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = """
                {"hospitals":[
                  {"hospital":"Child Hospital","city":"Toronto","kind":"er","pediatric":true,"open_now":false,"wait_minutes":null,"fetched_at":"2026-01-01T00:00:00Z"},
                  {"hospital":"Adult Hospital","city":"Toronto","kind":"er","pediatric":false,"open_now":true,"wait_minutes":40,"as_of":"2026-01-01T00:00:00Z","fetched_at":"2026-01-01T00:02:00Z"},
                  {"hospital":"Unknown Timestamp","city":"Toronto","kind":"er","open_now":true,"wait_minutes":20},
                  {"hospital":"Invalid Coordinates","city":"Toronto","kind":"er","open_now":true,"wait_minutes":25,"latitude":100,"longitude":200,"fetched_at":"2026-01-01T00:00:00Z"}
                ]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var rows = new ErCompassClient("http://127.0.0.1:" + server.getAddress().getPort()).fetchCurrent();
            assertThat(rows).hasSize(4);
            assertThat(rows.get(0).pediatric()).isTrue();
            assertThat(rows.get(0).openNow()).isFalse();
            assertThat(rows.get(0).waitMinutes()).isNull();
            assertThat(rows.get(0).observationSource()).isEqualTo("ER_COMPASS_FETCH_TIME");
            assertThat(rows.get(1).observationSource()).isEqualTo("ER_COMPASS_OBSERVED");
            assertThat(rows.get(1).observedAt().toString()).isEqualTo("2026-01-01T00:00:00Z");
            assertThat(rows.get(2).waitMinutes()).isNull();
            assertThat(rows.get(2).observedAt()).isNull();
            assertThat(rows.get(3).latitude()).isNull();
            assertThat(rows.get(3).longitude()).isNull();
        } finally { server.stop(0); }
    }
    @Test void rejectsUnexpectedShapeInsteadOfReportingSuccess() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"error\":\"upstream unavailable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> new ErCompassClient("http://127.0.0.1:" + server.getAddress().getPort()).fetchCurrent()).isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }
}
