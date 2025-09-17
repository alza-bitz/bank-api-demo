# Banking API Demo

## Summary

A feature-complete Clojure-based HTTP API for managing bank accounts. The solution addresses the requirements outlined in the [problem statement](.github/instructions/problem_statement_and_requirements.instructions.md) and follows the architecture described in the [solution design](.github/instructions/solution_design.instructions.md).

## Features

All currently implemented features are summarised here. The [problem statement and requirements instructions](.github/instructions/problem_statement_and_requirements.instructions.md) give more details for each feature.

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

## Prerequisites

- Java 11 or higher
- Clojure CLI
- Docker and Docker Compose (for PostgreSQL)
- Curl and JQ command-line tools (to follow the usage examples)

Alternatively, use an editor or environment that supports [dev containers](https://containers.dev). The supplied [devcontainer.json](.devcontainer/devcontainer.json) will install all the above prerequisites.

## Usage

### 1. Start PostgreSQL

```bash
$ docker-compose up -d postgres
```

This will start a PostgreSQL container. The database schema will be automatically created when the application starts.

### 2. Run the Application

```bash
$ clojure -M:run
```

The application will start on `http://localhost:3000`

### 3. Test the API

#### Synchronous API (default)

Create an account:
```bash
$ curl -X POST http://localhost:3000/account \
    -H "Content-Type: application/json" \
    -d '{"name": "John Doe"}' |
  jq
```

View an account:
```bash
$ curl http://localhost:3000/account/1 | jq
```

Deposit money:
```bash
$ curl -X POST http://localhost:3000/account/1/deposit \
    -H "Content-Type: application/json" \
    -d '{"amount": 100}' |
  jq
```

Withdraw money:
```bash
$ curl -X POST http://localhost:3000/account/1/withdraw \
    -H "Content-Type: application/json" \
    -d '{"amount": 50}' |
  jq
```

Transfer money:
```bash
$ curl -X POST http://localhost:3000/account/1/send \
    -H "Content-Type: application/json" \
    -d '{"amount": 25, "account-number": 2}' |
  jq
```

Get account audit log:
```bash
$ curl http://localhost:3000/account/1/audit | jq
```

#### Asynchronous API

For asynchronous processing, add `?async=true` to any endpoint:

```bash
# Submit async operation
$ curl -X POST "http://localhost:3000/account?async=true" \
    -H "Content-Type: application/json" \
    -d '{"name": "Jane Doe"}' |
  jq
# Returns: {"operation-id": "uuid", "status": "submitted"}

# Check operation result
$ curl http://localhost:3000/operation/{operation-id} | jq
# Returns: {"status": "completed", "result": {...}}
```

## API Documentation

The API documentation is available at `http://localhost:3000/swagger` when the application is running.

## API Endpoints

### Synchronous Operations
- `POST /account` - Create account
- `GET /account/{id}` - View account
- `POST /account/{id}/deposit` - Deposit money
- `POST /account/{id}/withdraw` - Withdraw money
- `POST /account/{id}/send` - Transfer money
- `GET /account/{id}/audit` - Get audit log

### Asynchronous Operations
Add `?async=true` to any of the above endpoints to use async mode.

### Operation Results
- `GET /operation/{id}` - Get async operation result

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

### Approach

The [development approach instructions](.github/instructions/development_approach.instructions.md) provide details on the development approach and methodology used.

### Running Tests

Unit tests:
```bash
$ clojure -M:test
```

Integration tests:
```bash
$ clojure -M:integration
```

### REPL Development

Start a REPL with development dependencies:
```bash
$ clj -M:dev
```

In the REPL:
```clojure
(require '[bank.system :as system])
(system/start-system!)
;; Make changes...
(system/restart-system!)
(system/stop-system!)
```

## Technical Architecture

For detailed technical information about the tech stack and architectural decisions, see the separate [Technical Architecture](docs/technical-architecture.md) documentation.

## Acknowledgements

This project was developed as a take-home assessment for a Clojure Engineer position, implementing the requirements specified in the [problem statement](.github/instructions/problem_statement_and_requirements.instructions.md).

## License

Copyright © 2025 Alex Coyle

Distributed under the [Eclipse Public License](LICENSE) either version 1.0 or (at your option) any later version.

The problem statement and requirements are copyright © 2025 [WPP Media](https://wppmedia.com).