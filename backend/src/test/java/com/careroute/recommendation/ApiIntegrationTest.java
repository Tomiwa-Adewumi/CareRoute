package com.careroute.recommendation;
import com.careroute.data.*;
import com.careroute.ingestion.ErCompassClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:api-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
    "careroute.ingestion.initial-delay-ms=3600000"
})
class ApiIntegrationTest {
    @LocalServerPort int port;
    @Autowired HospitalRepository hospitals;
    @Autowired WaitTimeObservationRepository observations;
    @MockitoBean ErCompassClient feed;
    @Test void endpointSerializesAvailableAndUnavailableFacilities() throws Exception {
        var now = Instant.now();
        var h = new HospitalEntity("api-test", "API Test Hospital", "Toronto", "ON", null, null, null, true, false, true);
        h.updateAvailability(true, true, now);
        h = hospitals.save(h);
        observations.save(new WaitTimeObservationEntity(h, 40, now, now, "ER_COMPASS_OBSERVED", null));
        var client = HttpClient.newHttpClient();
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/recommendations")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("LATEST_WAIT_BASELINE", "\"status\":\"AVAILABLE\"", "\"travelMinutes\":null");
        var personalized = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/recommendations?lat=43.65&lng=-79.38")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(personalized.body()).contains("\"score\":null", "\"status\":\"UNAVAILABLE\"", "Travel estimate is unavailable");
    }
}
