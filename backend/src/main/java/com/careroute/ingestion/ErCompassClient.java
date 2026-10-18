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
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(8));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.endpoint = endpoint;
    }

    public List<LiveWait> fetchCurrent() {
        JsonNode root = restClient.get().uri(endpoint).retrieve().body(JsonNode.class);
        if (root == null) throw new IllegalStateException("Empty feed response");

        JsonNode rows = locateRows(root);
        if (!rows.isArray()) throw new IllegalStateException("Unexpected feed format");

        List<LiveWait> waits = new ArrayList<>();
        for (JsonNode row : rows) {
            String name = text(row, "name", "hospital", "hospital_name", "facility_name");
            String city = text(row, "city", "municipality");
            Integer waitMinutes = integer(row, "wait_minutes", "waitMinutes", "current_wait_minutes", "currentWaitMinutes", "wait");
            if (name == null) continue;
            if (waitMinutes != null && (waitMinutes < 0 || waitMinutes > 720)) waitMinutes = null;
            String id = slug(name);
            double[] fallbackCoordinates = TORONTO_COORDINATES.get(id);
            Double latitude = decimal(row, "latitude", "lat");
            Double longitude = decimal(row, "longitude", "lng", "lon");
            if (fallbackCoordinates != null) {
                if (latitude == null) latitude = fallbackCoordinates[0];
                if (longitude == null) longitude = fallbackCoordinates[1];
            }

            if (latitude != null && (!Double.isFinite(latitude) || Math.abs(latitude) > 90)) latitude = null;
            if (longitude != null && (!Double.isFinite(longitude) || Math.abs(longitude) > 180)) longitude = null;
            Instant observed = instant(row, "observed_at", "as_of", "updated_at", "last_updated", "timestamp");
            String source = observed == null ? "ER_COMPASS_FETCH_TIME" : "ER_COMPASS_OBSERVED";
            if (observed == null) observed = instant(row, "fetched_at");
            if (observed == null || observed.isAfter(Instant.now().plusSeconds(60))) waitMinutes = null;
            boolean emergency = "er".equalsIgnoreCase(text(row, "kind")) && bool(row, "pediatric") != null;
            boolean pediatric = Boolean.TRUE.equals(bool(row, "pediatric"));
            waits.add(new LiveWait(
                    id, name, city, text(row, "province"), text(row, "address"), latitude, longitude,
                    waitMinutes, observed, List.of(), endpoint, emergency, pediatric, bool(row, "open_now"), source));
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
            if (value.isNumber()) {
                double number = value.asDouble();
                return Double.isFinite(number) && number >= 0 && number <= 720 ? (int) Math.round(number) : null;
            }
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
        for (String key : keys) {
            String raw = text(row, key);
            if (raw == null) continue;
            try { return Instant.parse(raw); } catch (Exception ignored) {}
        }
        return null;
    }

    private Boolean bool(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public record LiveWait(String id, String name, String city, String province, String address,
                           Double latitude, Double longitude, Integer waitMinutes, Instant observedAt,
                           List<Integer> recentWaits, String sourceUrl, boolean emergency, boolean pediatric,
                           Boolean openNow, String observationSource) {
        public LiveWait(String id, String name, String city, String province, String address,
                        Double latitude, Double longitude, Integer waitMinutes, Instant observedAt,
                        List<Integer> recentWaits, String sourceUrl) {
            this(id, name, city, province, address, latitude, longitude, waitMinutes, observedAt,
                    recentWaits, sourceUrl, true, false, true, "ER_COMPASS_OBSERVED");
        }
    }
}
