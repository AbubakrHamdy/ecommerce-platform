# E-Commerce Platform

A production-style e-commerce REST API built with **Java 21** and **Spring Boot 4**, with an Angular storefront and admin dashboard planned on top.

> **Status:** in active development. Authentication is complete; the catalog is next. See the [roadmap](#roadmap).

## Features so far

- **JWT authentication** with 15-minute access tokens and rotating 7-day refresh tokens, including stolen-token detection ([details](docs/authentication.md))
- **Role-based access** (`CUSTOMER`, `ADMIN`) with method-level `@PreAuthorize`
- **Password reset by email** with single-use, time-limited links
- **Consistent JSON errors** for validation, 401/403, 404, 409 and unexpected failures
- **Versioned schema** with Flyway migrations and dev-only seed data
- **Interactive API docs** with Swagger UI, including an "Authorize" button for tokens

## Tech stack

| Area | Technology |
|---|---|
| Language / framework | Java 21, Spring Boot 4.1, Maven |
| Security | Spring Security 7, OAuth2 Resource Server (JWT, HS256), BCrypt |
| Persistence | Spring Data JPA, Hibernate 7, PostgreSQL 17, Flyway |
| Caching | Redis 7 |
| Email | Spring Mail, async sending, Mailpit for local development |
| Mapping / validation | MapStruct, Jakarta Bean Validation, Lombok |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, AssertJ |
| Infrastructure | Docker Compose |

## Getting started

**Prerequisites:** JDK 21 and Docker Desktop. Maven is included via the wrapper.

```bash
git clone https://github.com/AbubakrHamdy/ecommerce-platform.git
cd ecommerce-platform
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

On Windows use `mvnw.cmd` instead of `./mvnw`. In IntelliJ, set **Active profiles** to `dev` in the run configuration.

The `dev` profile is required locally: it provides the development JWT secret and loads the seed data.

| Service | URL |
|---|---|
| Swagger UI | <http://localhost:3330/swagger-ui.html> |
| OpenAPI spec | <http://localhost:3330/v3/api-docs> |
| Mailpit (captured emails) | <http://localhost:8025> |
| PostgreSQL | `localhost:5432`, database / user / password: `ecommerce` |
| Redis | `localhost:6379` |

### Seed accounts (dev profile only)

| Role | Email | Password |
|---|---|---|
| Admin | `admin@example.com` | `Admin@123` |
| Customer | `customer@example.com` | `Customer@123` |

## Project structure

```
src/main/java/com/abubakr/ecommerceplatform
├── config/        Security, JWT, OpenAPI, async and typed properties
├── controller/    REST endpoints
├── dto/           Request/response records, grouped by feature
├── entity/        JPA entities
├── enums/         Role, OrderStatus, PaymentProvider, PaymentStatus
├── exception/     Custom exceptions and the global exception handler
├── mapper/        MapStruct mappers
├── repository/    Spring Data repositories
├── security/      Token creation and JSON 401/403 handling
└── service/       Business logic

src/main/resources
├── db/migration/  Flyway schema migrations (all environments)
└── db/testdata/   Seed data (dev profile only)
```

## Documentation

- [Authentication](docs/authentication.md): token design, endpoints, password reset, security decisions

## Roadmap

- [x] Project setup: Docker Compose, Flyway, Swagger, global error handling
- [x] Authentication: JWT, refresh tokens, roles, password reset email
- [ ] Catalog: categories, products, image upload, search and filters, Redis cache
- [ ] Cart and orders: checkout, stock handling, order status flow
- [ ] Payments: Stripe and Paymob, idempotent webhooks
- [ ] Order emails
- [ ] Admin statistics
- [ ] Angular storefront
- [ ] Angular admin dashboard
- [ ] Integration tests, deployment, live demo
