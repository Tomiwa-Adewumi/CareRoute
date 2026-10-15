# CareRoute

CareRoute is an explainable decision-support application for comparing nearby emergency departments using travel time, reported wait time, and data freshness.

> **Safety:** CareRoute does not diagnose symptoms or replace emergency services. If someone may be experiencing a medical emergency, call 911 or go to the nearest emergency department.

## What makes it different

Most wait-time tools show hospital data independently. CareRoute ranks options by **estimated time-to-care** and explains the tradeoff behind each recommendation.

Current MVP score:

```text
score = reported wait minutes + travel minutes + stale-data penalty
```

The scoring model is deliberately deterministic and explainable. Future ML forecasting will estimate near-future wait times from historical observations, while AI can summarize the recommendation without making medical decisions.

## Stack

- **Frontend:** Next.js 16 + TypeScript
- **Backend:** Java 17 + Spring Boot 4.1
- **CI:** GitHub Actions
- **Frontend hosting:** Vercel
- **Backend hosting:** Render-compatible Java service
- **Planned database:** PostgreSQL / Neon

## Repository structure

```text
CareRoute/
├── frontend/              # Next.js application
├── backend/               # Spring Boot REST API
├── .github/workflows/     # CI pipeline
└── README.md
```

## Run locally

### Backend

```bash
cd backend
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

```text
GET /api/recommendations
GET /actuator/health
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`.

For a deployed backend, set:

```text
NEXT_PUBLIC_API_BASE_URL=https://your-api-host.example.com
```

For the backend, set the allowed frontend origin with:

```text
CAREROUTE_ALLOWED_ORIGINS=https://your-vercel-domain.vercel.app
```

## MVP implemented

- Java domain model for hospital recommendation inputs
- deterministic recommendation engine
- travel + wait + freshness scoring
- human-readable ranking explanations
- REST API
- responsive Next.js recommendation dashboard
- Spring Boot health endpoint
- backend unit tests
- GitHub Actions backend test + frontend build pipeline

## Next engineering milestones

1. Replace seed hospitals with a provider-based ingestion interface.
2. Add PostgreSQL for hospitals and timestamped wait observations.
3. Add Toronto public/hospital data adapters with normalization and retries.
4. Add geospatial routing/travel-time integration.
5. Add Redis caching and source-health/data-freshness monitoring.
6. Train a wait-time forecasting model from historical observations.
7. Add observability, rate limiting, and deployment environments.

## Product principle

AI explains evidence; it does not diagnose the patient or decide whether emergency care is medically necessary.
