# Muscledia - Workout Service

Reactive microservice for managing workouts, exercises, fitness analytics, and personal records. Built with Spring WebFlux for non-blocking, high-concurrency operations.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3, Spring WebFlux (reactive) |
| Database | MongoDB (reactive driver) |
| Auth | JWT Bearer tokens (issued by User Service) |
| External APIs | Hevy API, ExerciseDB API |
| Build | Maven |
| Docs | OpenAPI / Swagger UI |

---

## Architecture

This service is part of the Muscledia microservices ecosystem. It operates reactively — all operations return `Mono<T>` (single result) or `Flux<T>` (stream of results) rather than blocking threads.

**Responsibilities:**
- Workout logging and retrieval
- Exercise and muscle group catalogue management
- Fitness analytics, progress tracking, and personal record detection
- Workout plan and routine folder management
- External fitness data ingestion (Hevy, ExerciseDB)

**Key design decisions:**
- Spring WebFlux chosen over Spring MVC because workout analytics and AI recommendation calls are I/O-bound — non-blocking threads handle concurrent requests without thread exhaustion
- MongoDB's reactive driver (`ReactiveMongoRepository`) pairs naturally with WebFlux — no blocking database calls in the request pipeline
- JWT validation is stateless — tokens issued by the User Service are validated locally without a round-trip to an auth server

---

## Authentication

All protected endpoints require a JWT Bearer token issued by the `muscledia-user-service`.

```
Authorization: Bearer <your-jwt-token>
```

**Access levels:**
- Public — no token required
- Authenticated — valid JWT required
- Admin — valid JWT with `ADMIN` role required

---

## API Reference

**Base URL:** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**OpenAPI spec:** `http://localhost:8080/v3/api-docs`

---

### Workouts — `/api/v1/workouts`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/` | Authenticated | Get authenticated user's workouts |
| GET | `/{workoutId}` | Authenticated | Get workout by ID |
| POST | `/` | Authenticated | Create new workout |
| DELETE | `/{workoutId}` | Authenticated | Delete workout |
| GET | `/date-range` | Authenticated | Get workouts within a date range |
| GET | `/admin/all` | Admin | Get all workouts across users |

---

### Analytics — `/api/v1/analytics`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/dashboard` | Authenticated | Full dashboard analytics for user |
| GET | `/period/{period}` | Authenticated | Analytics for a specific period |
| GET | `/history/{period}` | Authenticated | Historical analytics over time |
| GET | `/personal-records` | Authenticated | All personal records |
| GET | `/personal-records/recent` | Authenticated | Most recent PRs |
| GET | `/personal-records/exercise/{exerciseId}` | Authenticated | PRs for a specific exercise |
| GET | `/personal-records/statistics` | Authenticated | Aggregate PR statistics |
| GET | `/progress/{exerciseId}` | Authenticated | Progress tracking for an exercise |
| POST | `/check-pr` | Authenticated | Check if a new set is a personal record |
| POST | `/refresh` | Authenticated | Refresh analytics cache |
| DELETE | `/personal-records/{prId}` | Authenticated | Delete a PR record |

**Personal record detection:** when a workout is logged, the service compares the recorded weight and reps against the stored maximum for that exercise. If a new record is set, a `PersonalRecordEvent` is published to Kafka for the Gamification Service to consume.

---

### Exercises — `/api/v1/exercises`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/` | Public | Get all exercises |
| GET | `/{id}` | Public | Get exercise by ID |
| GET | `/name/{name}` | Public | Get exercise by name |
| GET | `/search` | Public | Search exercises |
| GET | `/equipment/{equipment}` | Public | Filter by equipment type |
| GET | `/difficulty/{difficulty}` | Public | Filter by difficulty level |
| GET | `/muscle/{muscle}` | Public | Filter by target muscle |
| GET | `/difficulty/{difficulty}/muscle/{muscle}` | Public | Filter by difficulty and muscle |
| POST | `/` | Admin | Create exercise |
| PUT | `/{id}` | Admin | Update exercise |
| DELETE | `/{id}` | Admin | Delete exercise |

---

### Muscle Groups — `/api/v1/muscle-groups`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/` | Public | Get all muscle groups |
| GET | `/{id}` | Public | Get muscle group by ID |
| GET | `/name/{name}` | Public | Get by common name |
| GET | `/latin-name/{latinName}` | Public | Get by anatomical Latin name |
| GET | `/search` | Public | Search muscle groups |
| GET | `/with-descriptions` | Public | Get with full descriptions |
| POST | `/` | Admin | Create muscle group |
| PUT | `/{id}` | Admin | Update muscle group |
| DELETE | `/{id}` | Admin | Delete muscle group |

---

### Workout Plans — `/api/v1/workout-plans`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/public` | Public | Get all public plans |
| GET | `/public/{id}` | Public | Get public plan by ID |
| GET | `/public/search` | Public | Search public plans |
| GET | `/personal` | Authenticated | Get user's personal plans |
| GET | `/my-created` | Authenticated | Get plans created by user |
| POST | `/personal` | Authenticated | Create personal plan |
| POST | `/public` | Admin | Create public plan |
| PUT | `/{id}` | Admin | Update plan |
| DELETE | `/{id}` | Admin | Delete plan |

---

### Routine Folders — `/api/v1/routine-folders`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/public` | Public | Get all public folders |
| GET | `/public/{id}` | Public | Get public folder by ID |
| GET | `/public/difficulty/{level}` | Public | Filter by difficulty |
| GET | `/public/equipment/{type}` | Public | Filter by equipment |
| GET | `/public/split/{split}` | Public | Filter by workout split |
| GET | `/personal` | Authenticated | Get user's personal folders |
| POST | `/save/{publicId}` | Authenticated | Save public folder to personal collection |
| POST | `/personal` | Authenticated | Create personal folder |
| POST | `/` | Admin | Create folder |
| PUT | `/{id}` | Admin | Update folder |
| DELETE | `/{id}` | Admin | Delete folder |

---

### Data Population — `/api/admin/data`

Admin-only endpoints for seeding reference data from external APIs.

| Method | Endpoint | Description |
|---|---|---|
| POST | `/populate-exercises` | Populate exercise catalogue from ExerciseDB |
| POST | `/populate-muscle-groups` | Populate muscle group definitions |
| POST | `/populate-all` | Populate all reference data in one call |
| POST | `/hevy/fetch-all` | Fetch and sync workout routines from Hevy API |

---

## Return Types

| Type | Meaning |
|---|---|
| `Mono<T>` | Single asynchronous result — completes with one item or empty |
| `Flux<T>` | Stream of 0-N asynchronous results |
| `ResponseEntity<T>` | HTTP response with explicit status code control |
| `Mono<Void>` | Completes with no return value — used for delete operations |

---

## Kafka Events Published

| Event | Trigger | Consumer |
|---|---|---|
| `WorkoutCompletedEvent` | User logs a workout | Gamification Service |
| `PersonalRecordEvent` | New PR detected during workout logging | Gamification Service |

Events are published asynchronously — the Workout Service does not wait for the Gamification Service to process them. If the Gamification Service is down, events queue in Kafka and are processed on recovery.

---

## Running Locally

**Prerequisites:** Java 21, Maven, MongoDB running locally or via Docker

```bash
# Start MongoDB
docker run -d -p 27017:27017 --name muscledia-mongodb mongo:latest
```

**application.yml configuration:**

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/muscledia_workouts

jwt:
  secret: <your-secret-key>
  issuer: muscledia-user-service
```

```bash
# Run the service
./mvnw spring-boot:run
```

**Endpoints after startup:**
- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

---

## Known Limitations

- Automated integration tests for Kafka event publishing not yet implemented — planned via Testcontainers
- Analytics refresh endpoint triggers a full recalculation — incremental updates are a planned improvement
