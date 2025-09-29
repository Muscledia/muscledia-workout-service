# 🏋️ Muscledia Workout Service

A reactive microservice built with Spring WebFlux for managing workouts, exercises, and fitness analytics.

## 🔐 Authentication

Most endpoints require JWT Bearer token authentication. Include the token in the `Authorization` header:

```
Authorization: Bearer <your-jwt-token>
```

## 📋 API Endpoints Overview

### 🔑 Authentication Test (`/api/v1/auth`)

| Method | Endpoint      | Return Type                 | Auth     | Description           |
| ------ | ------------- | --------------------------- | -------- | --------------------- |
| `GET`  | `/me`         | `Mono<Map<String, Object>>` | ✅       | Get current user info |
| `GET`  | `/admin-test` | `Mono<Map<String, String>>` | ✅ Admin | Test admin access     |
| `GET`  | `/user-test`  | `Mono<Map<String, String>>` | ✅ User  | Test user access      |
| `GET`  | `/test`       | `Mono<Map<String, String>>` | ❌       | Health check          |

### 🏋️ Workouts (`/api/v1/workouts`)

| Method   | Endpoint       | Return Type     | Auth     | Description                |
| -------- | -------------- | --------------- | -------- | -------------------------- |
| `GET`    | `/`            | `Flux<Workout>` | ✅       | Get user's workouts        |
| `GET`    | `/{workoutId}` | `Mono<Workout>` | ✅       | Get workout by ID          |
| `POST`   | `/`            | `Mono<Workout>` | ✅       | Create new workout         |
| `DELETE` | `/{workoutId}` | `Mono<Void>`    | ✅       | Delete workout             |
| `GET`    | `/date-range`  | `Flux<Workout>` | ✅       | Get workouts by date range |
| `GET`    | `/admin/all`   | `Flux<Workout>` | ✅ Admin | Get all workouts (admin)   |

### 📊 Analytics (`/api/v1/analytics`)

| Method   | Endpoint                                  | Return Type                                      | Auth | Description               |
| -------- | ----------------------------------------- | ------------------------------------------------ | ---- | ------------------------- |
| `GET`    | `/dashboard`                              | `Flux<WorkoutAnalyticsResponse>`                 | ✅   | Dashboard analytics       |
| `GET`    | `/period/{period}`                        | `Mono<ResponseEntity<WorkoutAnalyticsResponse>>` | ✅   | Period-specific analytics |
| `GET`    | `/history/{period}`                       | `Flux<WorkoutAnalyticsResponse>`                 | ✅   | Historical analytics      |
| `GET`    | `/personal-records`                       | `Flux<PersonalRecord>`                           | ✅   | All personal records      |
| `GET`    | `/personal-records/recent`                | `Mono<ResponseEntity<List<PersonalRecord>>>`     | ✅   | Recent PRs                |
| `GET`    | `/personal-records/exercise/{exerciseId}` | `Flux<PersonalRecord>`                           | ✅   | Exercise PRs              |
| `GET`    | `/personal-records/statistics`            | `Mono<PRStatistics>`                             | ✅   | PR statistics             |
| `GET`    | `/progress/{exerciseId}`                  | `Mono<ResponseEntity<ProgressTrackingResponse>>` | ✅   | Exercise progress         |
| `POST`   | `/check-pr`                               | `Mono<ResponseEntity<Boolean>>`                  | ✅   | Check potential PR        |
| `POST`   | `/refresh`                                | `Mono<ResponseEntity<String>>`                   | ✅   | Refresh analytics         |
| `DELETE` | `/personal-records/{prId}`                | `Mono<ResponseEntity<String>>`                   | ✅   | Delete PR record          |

### 💪 Exercises (`/api/v1/exercises`)

| Method   | Endpoint                                   | Return Type                      | Auth     | Description                   |
| -------- | ------------------------------------------ | -------------------------------- | -------- | ----------------------------- |
| `GET`    | `/{id}`                                    | `Mono<ResponseEntity<Exercise>>` | ❌       | Get exercise by ID            |
| `GET`    | `/name/{name}`                             | `Mono<ResponseEntity<Exercise>>` | ❌       | Get exercise by name          |
| `GET`    | `/search`                                  | `Flux<Exercise>`                 | ❌       | Search exercises              |
| `GET`    | `/search/name`                             | `Flux<Exercise>`                 | ❌       | Search by name                |
| `GET`    | `/equipment/{equipment}`                   | `Flux<Exercise>`                 | ❌       | Filter by equipment           |
| `GET`    | `/difficulty/{difficulty}`                 | `Flux<Exercise>`                 | ❌       | Filter by difficulty          |
| `GET`    | `/muscle/{muscle}`                         | `Flux<Exercise>`                 | ❌       | Filter by muscle              |
| `GET`    | `/difficulty/{difficulty}/muscle/{muscle}` | `Flux<Exercise>`                 | ❌       | Filter by difficulty & muscle |
| `GET`    | `/`                                        | `Flux<Exercise>`                 | ❌       | Get all exercises             |
| `POST`   | `/`                                        | `Mono<Exercise>`                 | ✅ Admin | Create exercise               |
| `PUT`    | `/{id}`                                    | `Mono<Exercise>`                 | ✅ Admin | Update exercise               |
| `DELETE` | `/{id}`                                    | `Mono<Void>`                     | ✅ Admin | Delete exercise               |

### 🎯 Muscle Groups (`/api/v1/muscle-groups`)

| Method   | Endpoint                  | Return Type                         | Auth     | Description            |
| -------- | ------------------------- | ----------------------------------- | -------- | ---------------------- |
| `GET`    | `/{id}`                   | `Mono<ResponseEntity<MuscleGroup>>` | ❌       | Get muscle group by ID |
| `GET`    | `/name/{name}`            | `Mono<ResponseEntity<MuscleGroup>>` | ❌       | Get by name            |
| `GET`    | `/latin-name/{latinName}` | `Mono<ResponseEntity<MuscleGroup>>` | ❌       | Get by Latin name      |
| `GET`    | `/search`                 | `Flux<MuscleGroup>`                 | ❌       | Search muscle groups   |
| `GET`    | `/search/name`            | `Flux<MuscleGroup>`                 | ❌       | Search by name         |
| `GET`    | `/search/latin-name`      | `Flux<MuscleGroup>`                 | ❌       | Search by Latin name   |
| `GET`    | `/names`                  | `Flux<MuscleGroup>`                 | ❌       | Get by multiple names  |
| `GET`    | `/with-descriptions`      | `Flux<MuscleGroup>`                 | ❌       | Get with descriptions  |
| `GET`    | `/`                       | `Flux<MuscleGroup>`                 | ❌       | Get all muscle groups  |
| `POST`   | `/`                       | `Mono<MuscleGroup>`                 | ✅ Admin | Create muscle group    |
| `PUT`    | `/{id}`                   | `Mono<MuscleGroup>`                 | ✅ Admin | Update muscle group    |
| `DELETE` | `/{id}`                   | `Mono<Void>`                        | ✅ Admin | Delete muscle group    |

### 📋 Workout Plans (`/api/v1/workout-plans`)

| Method   | Endpoint         | Return Type                         | Auth     | Description            |
| -------- | ---------------- | ----------------------------------- | -------- | ---------------------- |
| `GET`    | `/public/{id}`   | `Mono<ResponseEntity<WorkoutPlan>>` | ❌       | Get public plan by ID  |
| `GET`    | `/public`        | `Flux<WorkoutPlan>`                 | ❌       | Get all public plans   |
| `GET`    | `/public/search` | `Flux<WorkoutPlan>`                 | ❌       | Search public plans    |
| `GET`    | `/personal`      | `Flux<WorkoutPlan>`                 | ✅       | Get personal plans     |
| `GET`    | `/my-created`    | `Flux<WorkoutPlan>`                 | ✅       | Get user-created plans |
| `POST`   | `/personal`      | `Mono<WorkoutPlan>`                 | ✅       | Create personal plan   |
| `POST`   | `/public`        | `Mono<WorkoutPlan>`                 | ✅ Admin | Create public plan     |
| `GET`    | `/`              | `Flux<WorkoutPlan>`                 | ❌       | Get all plans          |
| `PUT`    | `/{id}`          | `Mono<WorkoutPlan>`                 | ✅ Admin | Update plan            |
| `DELETE` | `/{id}`          | `Mono<Void>`                        | ✅ Admin | Delete plan            |

### 📁 Routine Folders (`/api/v1/routine-folders`)

| Method   | Endpoint                     | Return Type                           | Auth     | Description                 |
| -------- | ---------------------------- | ------------------------------------- | -------- | --------------------------- |
| `GET`    | `/public/{id}`               | `Mono<ResponseEntity<RoutineFolder>>` | ❌       | Get public folder by ID     |
| `GET`    | `/public`                    | `Flux<RoutineFolder>`                 | ❌       | Get all public folders      |
| `GET`    | `/public/hevy/{hevyId}`      | `Mono<ResponseEntity<RoutineFolder>>` | ❌       | Get by Hevy ID              |
| `GET`    | `/public/difficulty/{level}` | `Flux<RoutineFolder>`                 | ❌       | Filter by difficulty        |
| `GET`    | `/public/equipment/{type}`   | `Flux<RoutineFolder>`                 | ❌       | Filter by equipment         |
| `GET`    | `/public/split/{split}`      | `Flux<RoutineFolder>`                 | ❌       | Filter by workout split     |
| `GET`    | `/all`                       | `Flux<RoutineFolder>`                 | ❌       | Get all folders             |
| `POST`   | `/save/{publicId}`           | `Mono<RoutineFolder>`                 | ✅       | Save to personal collection |
| `GET`    | `/personal`                  | `Flux<RoutineFolder>`                 | ✅       | Get personal folders        |
| `POST`   | `/personal`                  | `Mono<RoutineFolder>`                 | ✅       | Create personal folder      |
| `POST`   | `/`                          | `Mono<RoutineFolder>`                 | ✅ Admin | Create folder               |
| `PUT`    | `/{id}`                      | `Mono<RoutineFolder>`                 | ✅ Admin | Update folder               |
| `DELETE` | `/{id}`                      | `Mono<Void>`                          | ✅ Admin | Delete folder               |

### ⚙️ Data Population (`/api/admin/data`)

| Method | Endpoint                  | Return Type                    | Auth     | Description                 |
| ------ | ------------------------- | ------------------------------ | -------- | --------------------------- |
| `POST` | `/populate-exercises`     | `Mono<ResponseEntity<String>>` | ✅ Admin | Populate exercises from API |
| `POST` | `/populate-muscle-groups` | `Mono<ResponseEntity<String>>` | ✅ Admin | Populate muscle groups      |
| `POST` | `/populate-all`           | `Mono<ResponseEntity<String>>` | ✅ Admin | Populate all reference data |
| `POST` | `/hevy/fetch-all`         | `Mono<ResponseEntity<String>>` | ✅ Admin | Fetch Hevy API data         |

## 🔧 Key Features

- **Reactive Architecture**: Built with Spring WebFlux for high-performance non-blocking operations
- **JWT Authentication**: Secure authentication with role-based and permission-based authorization
- **Comprehensive Analytics**: Workout analytics, progress tracking, and personal records
- **External API Integration**: Hevy API integration for workout routines
- **MongoDB**: Reactive MongoDB for scalable data storage
- **OpenAPI Documentation**: Interactive API documentation with Swagger UI

## 🚀 Getting Started

1. **Clone the repository**
2. **Configure MongoDB connection** in `application.yml`
3. **Set JWT configuration** (secret key, issuer)
4. **Run the application**: `./mvnw spring-boot:run`
5. **Access Swagger UI**: `http://localhost:8080/swagger-ui.html`

## 📈 Return Types Reference

- `Mono<T>`: Single asynchronous result (or empty)
- `Flux<T>`: Stream of 0-N asynchronous results
- `ResponseEntity<T>`: HTTP response with status codes
- `Void`: No return value (for delete operations)

## 🏗️ Architecture

This service is part of the Muscledia microservices architecture:

- **Reactive**: Uses Spring WebFlux for non-blocking operations
- **JWT Validation**: Validates tokens issued by User Service
- **MongoDB**: Stores workout data, analytics, and reference data
- **External APIs**: Integrates with Hevy and ExerciseDB APIs

## 📖 Additional Documentation

- [JWT Authentication Guide](JWT-Microservices-Architecture-Guide.md)
- [Integration Summary](Workout-Service-JWT-Integration-Summary.md)
