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

## Prerequisites

- Java 17+
- Maven 3.9+
- Redis (port 6379) — or use Docker Compose
- Kafka (port 29092) — or use Docker Compose

## Quick Start with Docker Compose

```bash
docker compose up --build
```

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

# Start Kafka (adjust paths to your installation)
# Kafka 2.8+ with KRaft mode:
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
| `GET` | `/api/cart` | Get all cart items for user |
| `POST` | `/api/cart/items` | Add item to cart |
| `PUT` | `/api/cart/items/{productId}` | Update item quantity |
| `DELETE` | `/api/cart/items/{productId}` | Remove item from cart |
| `DELETE` | `/api/cart` | Clear entire cart |
| `GET` | `/api/cart/total` | Calculate cart total |
| `POST` | `/api/cart/checkout` | Publish checkout event + clear cart |

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
2. Spring Security's `JwtDecoder` validates the token signature using the shared `JWT_SECRET`.
3. The authenticated user identity (`Principal`) is injected into controller methods.
4. The `sub` (subject) claim of the JWT is used as the `userId` for cart operations.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | HMAC-SHA256 secret used to validate JWT Bearer tokens | `dev-secret-change-me-in-production` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers | `localhost:29092` |

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
  security:
    oauth2:
      resource-server:
        jwt:
          secret: ${JWT_SECRET:dev-secret-change-me-in-production}

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
