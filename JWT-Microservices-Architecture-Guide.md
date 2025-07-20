# JWT Authentication & Authorization Architecture for Muscledia Microservices

## 🏗️ Architecture Overview

### Service Roles:

- **User Service**: JWT creation, user authentication, token refresh (Servlet-based)
- **API Gateway** (Optional): Central routing, header injection, rate limiting
- **Workout Service**: JWT validation, user context extraction, authorization (Reactive WebFlux) ✅
- **Gamification Service**: JWT validation, user context extraction, authorization (Reactive WebFlux)

## 📋 Implementation Steps

### 1️⃣ **Shared JWT Configuration (All Services)**

#### `application.yaml` (Common across services)

```yaml
application:
  security:
    jwt:
      secret-key: ${JWT_SECRET_KEY:your-256-bit-secret}
      expiration: ${JWT_EXPIRATION:86400000} # 24 hours
      refresh-token-expiration: ${REFRESH_TOKEN_EXPIRATION:604800000} # 7 days
      issuer: ${JWT_ISSUER:muscledia-auth-service}
```

#### Common JWT Utility (Shared Library)

```java
// Create a shared library: muscledia-common
@Component
public class JwtUtil {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @Value("${application.security.jwt.issuer}")
    private String issuer;

    // Token Generation (User Service Only)
    public String generateToken(Long userId, String username, String role, Set<String> permissions) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("permissions", permissions);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Token Validation (All Services)
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Extract Claims (All Services)
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermissions(String token) {
        List<String> permissions = extractAllClaims(token).get("permissions", List.class);
        return new HashSet<>(permissions != null ? permissions : Collections.emptyList());
    }

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

### 2️⃣ **User Service Implementation (Servlet-based) - UNCHANGED**

#### Authentication Controller

```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<UserInfo> validateToken(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        UserInfo userInfo = authService.validateToken(token);
        return ResponseEntity.ok(userInfo);
    }
}
```

### 3️⃣ **🔥 CRITICAL: Reactive Services Implementation (WebFlux) - YOUR CURRENT PATTERN**

#### JWT Authentication Web Filter (REACTIVE - Keep Your Current Implementation ✅)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            return onError(exchange, "Invalid token");
        }

        return createAuthentication(token)
                .flatMap(auth -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .onErrorResume(error -> onError(exchange, error.getMessage()));
    }

    private Mono<Authentication> createAuthentication(String token) {
        return Mono.fromCallable(() -> {
            Long userId = jwtUtil.extractUserId(token);
            String username = jwtUtil.extractUsername(token);
            String role = jwtUtil.extractRole(token);
            Set<String> permissions = jwtUtil.extractPermissions(token);

            UserPrincipal principal = new UserPrincipal(userId, username, role, permissions);

            Collection<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(permission -> new SimpleGrantedAuthority("PERMISSION_" + permission))
                    .collect(Collectors.toList());

            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            return new JwtAuthenticationToken(principal, token, authorities);
        });
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
```

#### Authentication Service (REACTIVE - Keep Your Current Implementation ✅)

```java
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    public Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> ((UserPrincipal) auth.getPrincipal()).getUserId())
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user")));
    }

    public Mono<UserPrincipal> getCurrentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> (UserPrincipal) auth.getPrincipal())
                .switchIfEmpty(Mono.error(new UnauthorizedException("No authenticated user")));
    }

    public Mono<Boolean> hasPermission(String permission) {
        return getCurrentUser()
                .map(user -> user.hasPermission(permission));
    }
}
```

#### User Principal (Enhanced - ✅ Already Updated)

```java
@Data
@AllArgsConstructor
public class UserPrincipal {
    private Long userId;
    private String username;
    private String role;
    private Set<String> permissions;

    public boolean hasRole(String role) {
        return this.role.equals(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
```

### 4️⃣ **Security Configuration - REACTIVE (Keep Your Current ✅)**

```java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        // Public endpoints
                        .pathMatchers("/api/v1/exercises/**").permitAll()
                        .pathMatchers("/api/v1/muscle-groups/**").permitAll()
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Admin endpoints
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")

                        // Protected endpoints
                        .anyExchange().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
```

### 5️⃣ **🔥 CRITICAL: Controller Implementation - REACTIVE (Use Your Current Pattern)**

#### ❌ WRONG (Servlet-based from guide):

```java
// DON'T USE THIS - This is servlet-based
@GetMapping
public ResponseEntity<List<WorkoutDto>> getUserWorkouts(Authentication authentication) {
    UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
    List<WorkoutDto> workouts = workoutService.getUserWorkouts(user.getUserId());
    return ResponseEntity.ok(workouts);
}
```

#### ✅ CORRECT (Reactive - Your Current Pattern):

```java
// USE THIS - This is reactive and matches your current implementation
@GetMapping
public Flux<WorkoutResponse> getUserWorkouts() {
    return authenticationService.getCurrentUserId()
            .flatMapMany(workoutService::getUserWorkouts);
}

@PostMapping
public Mono<WorkoutResponse> createWorkout(@RequestBody CreateWorkoutRequest request) {
    return authenticationService.getCurrentUserId()
            .flatMap(userId -> workoutService.createWorkout(request, userId));
}

@PreAuthorize("@workoutService.isWorkoutOwner(#workoutId, #userId)")
@PutMapping("/{workoutId}")
public Mono<WorkoutResponse> updateWorkout(
        @PathVariable String workoutId,
        @RequestBody UpdateWorkoutRequest request) {
    return authenticationService.getCurrentUserId()
            .flatMap(userId -> workoutService.updateWorkout(workoutId, request, userId));
}
```

## 🔐 Authorization Strategies

### Role-Based Access Control (RBAC)

```java
// Method level
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasRole('USER')")

// Path level in SecurityConfig
.pathMatchers("/api/admin/**").hasRole("ADMIN")
```

### Permission-Based Access Control

```java
// Method level
@PreAuthorize("hasPermission('WORKOUT_READ')")
@PreAuthorize("hasPermission('WORKOUT_WRITE')")

// Service level
public Mono<Workout> getWorkout(String workoutId) {
    return authenticationService.hasPermission("WORKOUT_READ")
            .filter(hasPermission -> hasPermission)
            .switchIfEmpty(Mono.error(new ForbiddenException()))
            .then(workoutRepository.findById(workoutId));
}
```

### Resource-Based Authorization

```java
@PreAuthorize("@workoutService.isWorkoutOwner(#workoutId, authentication.principal.userId)")
@PutMapping("/{workoutId}")
public Mono<WorkoutResponse> updateWorkout(@PathVariable String workoutId, @RequestBody UpdateWorkoutRequest request) {
    return authenticationService.getCurrentUserId()
            .flatMap(userId -> workoutService.updateWorkout(workoutId, request, userId));
}
```

## 🌐 API Gateway Integration (Optional)

If using Spring Cloud Gateway:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: workout-service
          uri: lb://workout-service
          predicates:
            - Path=/api/v1/workouts/**
          filters:
            - name: AddRequestHeader
              args:
                name: X-User-Id
                value: "#{@jwtUtil.extractUserId(request.headers['Authorization'][0])}"
```

## 📊 Comparison: Your Current vs Examples

| Aspect              | Your Current (Reactive)          | Examples Shown (Servlet)            |
| ------------------- | -------------------------------- | ----------------------------------- |
| **Framework**       | ✅ Spring WebFlux                | Spring MVC                          |
| **Filter Type**     | ✅ WebFilter                     | OncePerRequestFilter                |
| **Security Config** | ✅ @EnableWebFluxSecurity        | @EnableWebSecurity                  |
| **Authentication**  | ✅ JwtAuthenticationToken        | UsernamePasswordAuthenticationToken |
| **Context**         | ✅ ReactiveSecurityContextHolder | SecurityContextHolder               |
| **Return Types**    | ✅ Mono/Flux                     | ResponseEntity/List                 |

## ✅ Your Implementation Status

**Strengths:**

- ✅ Correct reactive architecture
- ✅ Proper JWT validation
- ✅ Good authentication service
- ✅ Appropriate security configuration

**Recommendations:**

- ✅ Your current implementation is already following best practices
- ✅ Continue with reactive patterns as shown
- ✅ Consider adding permission-based authorization
- ✅ Add refresh token support

## 🚀 Next Steps

1. **Standardize** JWT utility across all services
2. **Implement** refresh token mechanism
3. **Add** permission-based authorization
4. **Create** shared authentication library
5. **Consider** API Gateway for cross-cutting concerns
