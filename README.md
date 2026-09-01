# cart-service

Lightweight shopping cart microservice for an e-commerce system. Manages user shopping carts and publishes checkout events to Kafka for downstream services (order, payment, inventory).

## Tech Stack

- **Framework:** Spring Boot 3.3.4
- **Language:** Java 17
- **Build Tool:** Maven
- **Persistence:** Spring Data Redis (HashOperations)
- **Messaging:** Spring for Apache Kafka
- **API Docs:** SpringDoc OpenAPI (Swagger UI)
- **Security:** Spring Security + JWT (HMAC-SHA256)
- **Container:** Docker (eclipse-temurin:17-jre-alpine)

## Architecture

### System Architecture

```text
┌───────────────────┐          ┌────────────────────┐
│    user-service     │  issues  │       Client         │
│  (issues JWT, HS256)│─────────▶│                      │
└───────────────────┘          └──────────┬──────────┘
                                            │ Authorization: Bearer <JWT>
                                            ▼
┌──────────────────────────────────────────────────────────────────┐
│                       cart-service  (:8082)                        │
│                                                                      │
│   ┌──────────────────────────────┐                                 │
│   │   Spring Security             │                                 │
│   │   OAuth2 Resource Server      │  validates JWT signature         │
│   │   (shared HS256 secret)       │                                 │
│   └──────────────┬────────────────┘                                 │
│                   ▼                                                  │
│   ┌──────────────────────────────┐                                 │
│   │        CartController          │  REST API                       │
│   └──────────────┬────────────────┘                                 │
│                   ▼                                                  │
│   ┌──────────────────────────────┐                                 │
│   │          CartService           │  business logic                 │
│   └───────┬───────────────┬───────┘                                 │
│           ▼                 ▼                                       │
│   ┌───────────────┐   ┌────────────────────┐                       │
│   │ CartRedis      │   │ CartEventProducer   │                       │
│   │ Repository     │   │                     │                       │
│   └───────┬───────┘   └──────────┬──────────┘                       │
└───────────┼───────────────────────┼───────────────────────────────┘
            ▼                       ▼
   ┌─────────────────┐    ┌───────────────────────┐
   │      Redis        │    │        Kafka            │
   │  cart:<userId>     │    │  topic: cart.checkout    │
   └─────────────────┘    └───────────┬───────────┘
                                        ▼
                            ┌───────────────────────────┐
                            │   Downstream consumers       │
                            │ (order, payment, inventory)   │
                            └───────────────────────────┘
```

### Request Flow (text)

```
HTTP Request (Authorization: Bearer <JWT>)
    ↓
[Spring Security OAuth2 Resource Server] → validates JWT using shared secret (HS256)
    ↓
CartController (REST API, port 8082)
    ↓
CartService (business logic)
    ↓
CartRedisRepository (Redis persistence via Spring Data Redis HashOperations)
    ↓
Redis (in-memory key-value store, key pattern: cart:<userId>)

Checkout flow (async event):
CartService.checkout()
    ↓
CartEventProducer.publishOrderCreated()
    ↓
Kafka topic: cart.checkout
    ↓
[Downstream consumers - order, payment, inventory]
```

### File Structure

```
cart-service/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/cart/
    │   │   ├── CartApplication.java          # Spring Boot entry point
    │   │   ├── config/
    │   │   │   ├── AppProperties.java         # Typed configuration (app.*)
    │   │   │   ├── OpenApiConfig.java         # Swagger bearer-auth scheme
    │   │   │   ├── RateLimitFilter.java       # In-memory rate limiter
    │   │   │   └── SecurityConfig.java        # JWT resource server + CORS
    │   │   ├── controller/
    │   │   │   └── CartController.java        # REST API (/api/v1/cart)
    │   │   ├── dto/
    │   │   │   └── UpdateQuantityRequest.java
    │   │   ├── exception/
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── messaging/
    │   │   │   └── CartEventProducer.java     # Publishes cart.checkout to Kafka
    │   │   ├── model/
    │   │   │   └── CartItem.java
    │   │   ├── repository/
    │   │   │   └── CartRedisRepository.java   # Redis persistence
    │   │   └── service/
    │   │       └── CartService.java           # Business logic
    │   └── resources/
    │       ├── application.yml                # Default config
    │       └── application-dev.yml            # Local dev overrides
    └── test/
        └── java/com/cart/
            ├── CartCheckoutContractTest.java          # Kafka contract test
            ├── controller/
            │   └── CartControllerTest.java            # REST + validation tests
            ├── security/
            │   └── CartControllerSecurityTest.java    # JWT auth tests
            └── service/
                └── CartServiceTest.java               # Business logic unit tests
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Redis (port 6379) — or use Docker Compose
- Kafka (port 29092) — or use Docker Compose

## Quick Start with Docker Compose

```bash
docker compose up --build
```

The image is built from source inside a multi-stage `Dockerfile`, so no local
`mvn package` is required first.

This starts Redis, Kafka, and the cart-service together on:
- **API:** http://localhost:8082
- **Swagger UI:** http://localhost:8082/swagger-ui.html
- **Redis:** localhost:6379
- **Kafka:** localhost:29092

## Build

```bash
mvn clean package
```

## Run (local, without Docker)

```bash
# Start Redis
redis-server

# Start Kafka (adjust paths to your installation).
# The bundled docker-compose.yml runs Kafka with ZooKeeper (confluentinc images);
# a standalone install can also use KRaft mode:
kafka-server-start.sh config/kraft/server.properties

# Run the app
mvn spring-boot:run
```

Or run the packaged JAR directly:

```bash
java -jar target/cart-service-0.0.1-SNAPSHOT.jar
```

## Run with Dev Profile

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

## Test

```bash
mvn test
```

## API Endpoints

All endpoints require a valid JWT Bearer token in the `Authorization` header. The user identity is extracted from the token subject by Spring Security and passed to the controller as a `Principal`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/cart` | Get all cart items for user |
| `POST` | `/api/v1/cart/items` | Add item to cart |
| `PUT` | `/api/v1/cart/items/{productId}` | Update item quantity |
| `DELETE` | `/api/v1/cart/items/{productId}` | Remove item from cart |
| `DELETE` | `/api/v1/cart` | Clear entire cart |
| `GET` | `/api/v1/cart/total` | Calculate cart total |
| `POST` | `/api/v1/cart/checkout` | Publish checkout event + clear cart |

Adding the same `productId` again increments the stored quantity rather than
replacing it.

### Public (no auth)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/actuator/health` | Liveness/readiness health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape endpoint |
| `GET` | `/swagger-ui.html` | Swagger UI |
| `GET` | `/v3/api-docs` | OpenAPI spec |

### Idempotent checkout

`POST /api/v1/cart/checkout` accepts an optional `Idempotency-Key` header. A
repeated key (within `CHECKOUT_IDEMPOTENCY_TTL`) is acknowledged with `202`
without re-publishing the `cart.checkout` event, so client retries are safe.

### Rate limiting

Requests are limited per authenticated user (per client IP when anonymous) using
an in-memory fixed window. Responses carry `X-RateLimit-Limit` /
`X-RateLimit-Remaining`; exceeding the limit returns `429`. Disable with
`RATE_LIMIT_ENABLED=false`. For multi-instance deployments move the counter to
Redis.

### Observability

- Metrics: Micrometer + `/actuator/prometheus`
- Tracing: Micrometer Tracing (Brave bridge); sample rate via `TRACING_SAMPLE_RATE`

### Kafka producer resilience

The producer runs with `acks=all`, idempotence enabled and retries
(`delivery.timeout.ms=120000`). `checkout()` blocks on the broker ack, so a
failed publish surfaces as `409` and the cart is **not** cleared.

## API Documentation

Swagger UI is available at:

```
http://localhost:8082/swagger-ui.html
```

OpenAPI spec at:

```
http://localhost:8082/v3/api-docs
```

## Security

The service uses Spring Security as an OAuth2 resource server with JWT Bearer tokens validated using a shared HMAC-SHA256 (HS256) secret.

### Authentication Flow

1. Client sends a request with `Authorization: Bearer <token>` header.
2. Spring Security's `JwtDecoder` validates the token signature using the shared `JWT_SECRET` (bound to `app.jwt.secret`).
3. The authenticated user identity (`Principal`) is injected into controller methods.
4. The `sub` (subject) claim of the JWT is used as the `userId` for cart operations.

`/actuator/health/**`, `/actuator/prometheus`, `/actuator/info`, `/swagger-ui/**`
and `/v3/api-docs/**` are the only unauthenticated paths. CORS is configurable
via `CORS_ALLOWED_ORIGINS` / `CORS_ALLOWED_METHODS`.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | HMAC-SHA256 secret used to validate JWT Bearer tokens | `dev-secret-change-me-in-production` |
| `JWT_ALLOW_INSECURE_SECRET` | When `false`, the service refuses to start with the built-in dev secret | `true` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:29092` |
| `CART_TTL` | ISO-8601 duration a cart is kept in Redis; blank/`PT0S` disables expiry | `P7D` |
| `CHECKOUT_IDEMPOTENCY_TTL` | How long a processed `Idempotency-Key` is remembered | `PT1H` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins (patterns) | `*` |
| `CORS_ALLOWED_METHODS` | Comma-separated allowed HTTP methods | `GET,POST,PUT,DELETE,OPTIONS` |
| `RATE_LIMIT_ENABLED` | Enable the in-memory rate limiter | `true` |
| `RATE_LIMIT_RPM` | Requests per minute per caller | `120` |
| `TRACING_SAMPLE_RATE` | Trace sampling probability (0.0–1.0) | `0.1` |

## Kafka Events

### `cart.checkout` topic

Published when a user completes checkout.

**Payload:**
```json
{
  "orderId": "uuid",
  "userId": "user-123",
  "items": [
    {
      "productId": "p1",
      "name": "Laptop",
      "unitPrice": 1200.00,
      "quantity": 1
    }
  ],
  "totalAmount": 1200.00
}
```

## Configuration

### application.yml (default)

```yaml
server:
  port: 8082

spring:
  application:
    name: cart-service
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-me-in-production}
  cart:
    ttl: ${CART_TTL:P7D}   # ISO-8601 duration; blank/0 disables cart expiry

management:
  endpoints:
    web:
      exposure:
        include: health,info

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

### application-dev.yml (local development)

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:29092
```

## Docker

Build the image:

```bash
docker build -t cart-service .
```

Run the container:

```bash
docker run -p 8082:8082 \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:29092 \
  -e JWT_SECRET=dev-secret-change-me-in-production \
  cart-service
```

## License

Internal — all rights reserved.
