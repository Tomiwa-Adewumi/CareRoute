# CareRoute

CareRoute is a Toronto prototype for comparing **available reported emergency-department waits and estimated travel**. It combines a Java/Spring Boot API, PostgreSQL ingestion pipeline, and Next.js/TypeScript interface, with an exploratory forecasting benchmark.

> CareRoute does not diagnose, assess clinical suitability, or replace emergency services. If someone may be experiencing a medical emergency, call 911. Emergency departments triage by severity. The comparison is limited to adult facilities and incomplete reporting coverage.

## Comparison behavior

The production default is `LATEST_WAIT_BASELINE`:

```text
comparison score = latest reported wait + optional travel + 0.2 × data age in minutes
```

A facility receives a score only when its feed metadata identifies an open adult emergency department, its wait is available, and both its latest observation and last feed presence are no more than 30 minutes old. Closed, unknown, pediatric, missing, and stale facilities remain visible without a score. Unknown travel excludes a facility from a personalized comparison; location-free comparisons explicitly omit travel.

The score is a comparison aid, not an individual wait prediction or an assurance that a hospital is clinically appropriate. Fetch-time observations are labeled because the original measurement time is unknown.

The page checks for updates every minute and on focus. The backend ingests every ten minutes. Failed page refreshes remove prior rankings and offer a retry. Location is opt-in and can be disabled.

## Forecasting experiments

Per-hospital linear regression and exponential smoothing are offline challengers. The evaluator compares them with latest-wait and moving-average baselines on identical eligible targets. Only observations with verified source timestamps enter the primary benchmark; historical reconstructed sparkline points, fetch-time observations, and legacy rows are excluded. New ingestion does not manufacture sparkline timestamps.

Regression accepts an explicit arrival timestamp, so its forecast horizon includes observation age. Its residual-based range is a **heuristic range**, not a calibrated 95% interval. Error metrics and range coverage are withheld until ten eligible predictions exist. This reporting threshold does not establish validation.

An earlier, mixed-provenance snapshot reported latest-wait MAE of 11.6 minutes and regression MAE of 20.6 minutes. Those numbers are historical and are **not a validated benchmark under the current protocol**. The simpler baseline remains the production default. A challenger requires reproducible improvement across facilities and arrival horizons, plus ranking-level evaluation, before promotion; there is no automatic promotion based on ten samples.

[Forecasting and limitations](docs/forecasting.md) · [Evaluation protocol](docs/evaluation.md) · [Architecture](docs/architecture.md) · [CI](../../actions)

## Stack

- Java 17, Spring Boot 4.1, Spring Data JPA, REST APIs
- PostgreSQL, Flyway, H2 for ephemeral local development
- Next.js 16, React, TypeScript
- JUnit, Mockito, PostgreSQL/Testcontainers integration tests, Playwright, ESLint
- GitHub Actions, Docker-compatible backend, static frontend export

## Quick start

Requires Java 17+, Maven 3.9+, and Node.js 22 LTS.

```bash
cd backend
mvn spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open http://localhost:3000. The frontend defaults to http://localhost:8080. Local H2 history resets when the backend exits. Configure `NEXT_PUBLIC_API_BASE_URL` at build time for a deployed backend.

## Verification

```bash
cd backend
mvn verify
```

The full suite needs Docker for Testcontainers. CI also supplies a PostgreSQL 17 service for repeated-ingestion and migration tests. Without Docker, run the unit and H2 API tests with `mvn '-Dtest=!*Postgres*' test`; this does not validate PostgreSQL migrations.

```bash
cd frontend
npm ci
npm run lint
npm run build
npx playwright install chromium
npm run test:e2e
```

Browser tests cover opt-in and denied location, missing/stale facilities, refresh failure/recovery, periodic/focus refresh, and withholding small-sample metrics. Backend tests exercise real service calls, HTTP feed/routing adapters, cache eviction, forecast timestamps, timestamp provenance, and availability transitions.

## Configuration

For durable PostgreSQL storage, set these as deployment environment variables:

```text
DATABASE_URL=jdbc:postgresql://.../careroute?sslmode=require
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
FLYWAY_ENABLED=true
DB_DDL_AUTO=validate
CAREROUTE_INGESTION_CITY=Toronto
CAREROUTE_INGESTION_INTERVAL_MS=600000
CAREROUTE_ALLOWED_ORIGINS=https://your-frontend.example.com
```

Migration V3 adds facility availability metadata. Existing facilities remain unranked until a successful ingestion establishes their current availability. Legacy observations are retained but excluded from the new primary comparison and benchmark queries. No history is deleted.

```text
GET /api/recommendations
GET /api/recommendations?lat=43.6532&lng=-79.3832
GET /api/model/card
GET /api/model/metrics
GET /api/data/health
GET /actuator/health
```

Recommendation rows include `status`. Unavailable rows have null `score` and `scoreBreakdown`, with an explanation. Wait, travel, timestamp and range fields can be null. Consumers must handle these explicitly. Legacy `predictedWaitMinutes` fields hold the latest-wait baseline for available observations; the frontend labels them as reported waits.

## Location handling

Coordinates stay in browser state while location is enabled and are sent to the API on each refresh. The API sends coordinates to OSRM for road routing. Rounded coordinates are retained as in-memory cache keys: successful routes expire after five minutes, fallback routes after 30 seconds, and scheduled cleanup runs every minute. The cache is bounded to 1,000 entries. The application does not write coordinates to the database; routing failure logs contain only exception class names, and Tomcat access logging is disabled by default. Hosting and routing providers may process request logs independently.
