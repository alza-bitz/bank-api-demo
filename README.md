# Banking API

A feature-complete Clojure-based HTTP API for managing banking accounts with Domain-Driven Design (DDD) architecture and asynchronous processing capabilities.

## Features

- ✅ Create bank accounts with unique account numbers
- ✅ View account details
- ✅ Deposit money to accounts
- ✅ Withdraw money from accounts with validation
- ✅ Transfer money between accounts with validation
- ✅ Account audit log with transaction history
- ✅ Synchronous and asynchronous API modes
- ✅ Concurrent processing of up to 1000 requests
- ✅ OpenAPI 3.x documentation
- ✅ Comprehensive error handling

## Architecture

The application follows DDD layered architecture with both synchronous and asynchronous processing:

- **Domain Layer**: Core business logic, entities, and domain events
- **Persistence Layer**: Database operations using PostgreSQL with HikariCP connection pooling
- **Application Layer**: Use cases, business workflows, and async operation handling
- **Interface Layer**: HTTP API with JSON REST endpoints supporting both sync and async modes
- **System Layer**: Application lifecycle, dependency management, and Integrant configuration

## Technology Stack

- **Language**: Clojure 1.12
- **HTTP Server**: Jetty with Reitit routing
- **Database**: PostgreSQL with next.jdbc and HikariCP
- **JSON Processing**: Jsonista and Muuntaja
- **Validation**: Malli for schema validation
- **Async Processing**: core.async for concurrent operations
- **Dependency Injection**: Integrant for system lifecycle
- **Testing**: clojure.test with testcontainers for integration tests
- **Logging**: tools.logging with log4j2

## Prerequisites

- Java 11 or higher
- Clojure CLI
- Docker and Docker Compose (for PostgreSQL)

## Quick Start

### 1. Start PostgreSQL

```bash
docker-compose up -d postgres
```

This will start a PostgreSQL container. The database schema will be automatically created when the application starts.

### 2. Run the Application

```bash
clojure -M:run
```

The application will start on `http://localhost:3000`

### 3. Test the API

#### Synchronous API (default)

Create an account:
```bash
curl -X POST http://localhost:3000/account \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe"}'
```

View an account:
```bash
curl http://localhost:3000/account/1
```

Deposit money:
```bash
curl -X POST http://localhost:3000/account/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}'
```

Withdraw money:
```bash
curl -X POST http://localhost:3000/account/1/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 50}'
```

Transfer money:
```bash
curl -X POST http://localhost:3000/account/1/send \
  -H "Content-Type: application/json" \
  -d '{"amount": 25, "account-number": 2}'
```

Get account audit log:
```bash
curl http://localhost:3000/account/1/audit
```

#### Asynchronous API

For asynchronous processing, add `?async=true` to any endpoint:

```bash
# Submit async operation
curl -X POST "http://localhost:3000/account?async=true" \
  -H "Content-Type: application/json" \
  -d '{"name": "Jane Doe"}'
# Returns: {"operation-id": "uuid", "status": "submitted"}

# Check operation result
curl http://localhost:3000/operation/{operation-id}
# Returns: {"status": "completed", "result": {...}}
```

## Configuration

The application can be configured using environment variables:

- `DATABASE_HOST`: PostgreSQL host (default: localhost)
- `DATABASE_PORT`: PostgreSQL port (default: 5432)
- `DATABASE_NAME`: Database name (default: bankdb)
- `DATABASE_USER`: Database user (default: bankuser)
- `DATABASE_PASSWORD`: Database password (default: bankpass)
- `HTTP_PORT`: HTTP server port (default: 3000)
- `ASYNC_CONSUMER_POOL_SIZE`: Number of async operation consumers (default: 10)
- `HIKARI_MAX_POOL_SIZE`: Maximum database connections (default: 10)

## Development

### Running Tests

Unit tests:
```bash
clojure -M:test
```

Integration tests:
```bash
clojure -M:integration
```

### REPL Development

Start a REPL with development dependencies:
```bash
clojure -M:dev
```

In the REPL:
```clojure
(require '[bank.system :as system])
(system/start-system!)
;; Make changes...
(system/restart-system!)
(system/stop-system!)
```

### API Documentation

The API documentation is available at `http://localhost:3000/swagger` when the application is running.

### API Endpoints

#### Synchronous Operations
- `POST /account` - Create account
- `GET /account/{id}` - View account
- `POST /account/{id}/deposit` - Deposit money
- `POST /account/{id}/withdraw` - Withdraw money
- `POST /account/{id}/send` - Transfer money
- `GET /account/{id}/audit` - Get audit log

#### Asynchronous Operations
Add `?async=true` to any of the above endpoints to use async mode.

#### Operation Results
- `GET /operation/{id}` - Get async operation result

### Performance Features

- **Concurrent Processing**: Handles up to 1000 concurrent requests
- **Connection Pooling**: HikariCP for efficient database connections
- **Async Processing**: Non-blocking operation handling with configurable consumer pools
- **Database Transactions**: ACID compliance for all banking operations

## Production Deployment

### Build Uberjar

```bash
clojure -T:uberjar
```

### Run Uberjar

```bash
java -jar target/bank-api.jar
```

## Database Schema

The application uses two main tables:

- `account`: Stores account information (account_number, name, balance)
- `account_event`: Stores account transaction events for audit trail (sequence, event_id, account_number, debit, credit, description)

## Testing

The application includes comprehensive test coverage:

- **Unit Tests**: Domain logic, application services, HTTP handlers, repository operations
- **Integration Tests**: End-to-end testing with PostgreSQL testcontainers
- **Concurrent Tests**: Validates 1000+ concurrent request processing
- **Async Tests**: Validates asynchronous operation lifecycle

### Test Coverage
- Domain layer: Account creation, validation, business rules
- Application layer: Sync and async service operations
- Interface layer: HTTP handlers, routing, error handling
- Persistence layer: Database operations, transaction handling

## License

Copyright © 2025
