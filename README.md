# CareRoute

CareRoute is an explainable decision-support application for comparing emergency departments using reported wait time, estimated travel time, and data freshness.

> **Safety:** CareRoute does not diagnose symptoms or replace emergency services. If someone may be experiencing a medical emergency, call 911 or go to the nearest emergency department. ERs triage by medical severity, not arrival order.

## What makes it different

CareRoute does more than display a wait-time table. It continuously ingests public ER wait data, stores timestamped observations, and ranks options with an explainable score.

```text
score = reported wait minutes + travel minutes + stale-data penalty
```

The scoring model is deliberately deterministic. Future ML forecasting will estimate the wait at expected arrival time from the observation history; AI can explain evidence but will not diagnose or decide whether emergency care is necessary.

## Stack

- **Frontend:** Next.js 16 + TypeScript
- **Backend:** Java 17 + Spring Boot 4.1
- **Database:** Supabase Postgres
- **Migrations:** Flyway
- **Persistence:** Spring Data JPA
- **Live data:** adapter-based ingestion; initial source is ER Compass' public current-wait endpoint
- **CI:** GitHub Actions
- **Frontend hosting:** Vercel
- **Backend hosting:** Render-compatible container

## Data flow

```text
public ER feeds / aggregators
          ↓
   ingestion adapters
          ↓
 normalize + validate
          ↓
  Supabase Postgres
  ├── hospitals
  └── wait_time_observations
          ↓
 latest observation per hospital
          ↓
 recommendation engine
          ↓
   Spring Boot REST API
          ↓
      Next.js UI
```

The backend currently polls Ontario data every 10 minutes and persists Toronto observations. The adapter boundary is intentional: direct hospital-specific sources can be added without changing the persistence or recommendation layers.

## Repository structure

```text
CareRoute/
├── frontend/
├── backend/
│   └── src/main/resources/db/migration/
├── .github/workflows/
└── README.md
```

## Backend configuration

Supabase recommends a direct connection or Session Pooler for persistent Spring/JPA backends, with SSL required. Keep the database URL in an environment variable rather than source control.

```text
SUPABASE_DB_URL=jdbc:postgresql://...:5432/postgres?user=...&password=...&sslmode=require
CAREROUTE_INGESTION_CITY=Toronto
CAREROUTE_INGESTION_INTERVAL_MS=600000
CAREROUTE_ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app
```

Run locally:

```bash
cd backend
mvn spring-boot:run
```

API:

```text
GET /api/recommendations
GET /api/recommendations?lat=43.6532&lng=-79.3832
GET /actuator/health
```

When latitude/longitude are supplied, the MVP estimates travel time using straight-line distance plus a conservative city-speed assumption. A routing provider is a later milestone.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

For a deployed backend:

```text
NEXT_PUBLIC_API_BASE_URL=https://your-api-host.example.com
```

## Implemented

- Java/Spring Boot REST backend
- deterministic recommendation engine
- wait + travel + freshness scoring
- scheduled live wait-time ingestion
- resilient JSON normalization for external feed fields
- Toronto filtering
- Supabase/Postgres schema
- Flyway migrations
- JPA persistence and latest-observation queries
- historical wait-time snapshots
- responsive Next.js dashboard
- health endpoint and CI

## Next milestones

1. Create the dedicated CareRoute Supabase project and apply the migration.
2. Add direct first-party adapters where practical, starting with UHN/Sunnybrook.
3. Replace approximate travel time with a routing API.
4. Add source-health, retries/backoff, deduplication, and stale-feed alerts.
5. Add Redis caching for recommendation reads.
6. Train wait-at-arrival forecasts from historical observations.
7. Add observability, rate limiting, and deployment environments.

## Product principle

AI explains evidence; it does not diagnose the patient or determine whether emergency care is medically necessary.
