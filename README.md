# Banking API

A Clojure-based HTTP API for managing banking accounts with Domain-Driven Design (DDD) architecture.

## Features

- Create bank accounts
- View account details
- Deposit money to accounts
- Transfer money between accounts (planned)
- Withdraw money from accounts (planned)
- Account audit log (planned)

## Architecture

The application follows DDD layered architecture:

- **Domain Layer**: Core business logic and entities
- **Persistence Layer**: Database operations using PostgreSQL
- **Application Layer**: Use cases and business workflows
- **Interface Layer**: HTTP API with JSON REST endpoints
- **System Layer**: Application lifecycle and dependency management

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

## Configuration

The application can be configured using environment variables:

- `DATABASE_HOST`: PostgreSQL host (default: localhost)
- `DATABASE_PORT`: PostgreSQL port (default: 5432)
- `DATABASE_NAME`: Database name (default: bankdb)
- `DATABASE_USER`: Database user (default: bankuser)
- `DATABASE_PASSWORD`: Database password (default: bankpass)
- `HTTP_PORT`: HTTP server port (default: 3000)

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

- `account`: Stores account information
- `account_event`: Stores account transaction events for audit trail

## License

Copyright © 2025
