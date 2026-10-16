package com.careroute.ingestion;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ErCompassClient {
    private static final Pattern HOURS_MINUTES = Pattern.compile("(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m(?:in)?)?", Pattern.CASE_INSENSITIVE);
    private static final Map<String, double[]> TORONTO_COORDINATES = Map.of(
            "sunnybrook-health-sciences-centre", new double[]{43.7226, -79.3748},
            "university-health-network-toronto-western-hospital", new double[]{43.6537, -79.4055},
            "university-health-network-toronto-general-hospital", new double[]{43.6596, -79.3889},
            "trillium-health-partners-queensway-health-centre", new double[]{43.5932, -79.5441});

    private final RestClient restClient;
    private final String endpoint;

    public ErCompassClient(@Value("${careroute.ingestion.ercompass-url:https://ercompass.ca/api/current?province=ON&sort=wait}") String endpoint) {
        this.restClient = RestClient.create();
        this.endpoint = endpoint;
    }

    public List<LiveWait> fetchCurrent() {
        JsonNode root = restClient.get().uri(endpoint).retrieve().body(JsonNode.class);
        if (root == null) return List.of();

        JsonNode rows = locateRows(root);
        if (!rows.isArray()) return List.of();

        List<LiveWait> waits = new ArrayList<>();
        for (JsonNode row : rows) {
            String name = text(row, "name", "hospital", "hospital_name", "facility_name");
            String city = text(row, "city", "municipality");
            Integer waitMinutes = integer(row, "wait_minutes", "waitMinutes", "current_wait_minutes", "currentWaitMinutes", "wait");
            if (name == null || waitMinutes == null || waitMinutes < 0) continue;
            String id = slug(name);
            double[] fallbackCoordinates = TORONTO_COORDINATES.get(id);
            Double latitude = decimal(row, "latitude", "lat");
            Double longitude = decimal(row, "longitude", "lng", "lon");
            if (fallbackCoordinates != null) {
                if (latitude == null) latitude = fallbackCoordinates[0];
                if (longitude == null) longitude = fallbackCoordinates[1];
            }

            waits.add(new LiveWait(
                    id, name, city, text(row, "province"), text(row, "address"), latitude, longitude,
                    waitMinutes, instant(row, "observed_at", "as_of", "fetched_at", "updated_at", "last_updated", "timestamp"), endpoint));
        }
        return waits;
    }

    private JsonNode locateRows(JsonNode root) {
        if (root.isArray()) return root;
        for (String key : List.of("data", "hospitals", "results", "items", "current")) {
            JsonNode candidate = root.get(key);
            if (candidate != null && candidate.isArray()) return candidate;
        }
        return root;
    }

    private String text(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText().trim();
        }
        return null;
    }

    private Double decimal(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) continue;
            if (value.isNumber()) return value.asDouble();
            try { return Double.parseDouble(value.asText()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer integer(JsonNode row, String... keys) {
        for (String key : keys) {
            JsonNode value = row.get(key);
            if (value == null || value.isNull()) continue;
            if (value.isNumber()) return value.asInt();
            String raw = value.asText().trim();
            try { return Integer.parseInt(raw); } catch (NumberFormatException ignored) {}
            Matcher matcher = HOURS_MINUTES.matcher(raw);
            if (matcher.matches() && (matcher.group(1) != null || matcher.group(2) != null)) {
                int hours = matcher.group(1) == null ? 0 : Integer.parseInt(matcher.group(1));
                int minutes = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
                return hours * 60 + minutes;
            }
        }
        return null;
    }

    private Instant instant(JsonNode row, String... keys) {
        String raw = text(row, keys);
        if (raw == null) return Instant.now();
        try { return Instant.parse(raw); } catch (Exception ignored) { return Instant.now(); }
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record LiveWait(String id, String name, String city, String province, String address,
                           Double latitude, Double longitude, int waitMinutes, Instant observedAt,
                           String sourceUrl) {}
}
