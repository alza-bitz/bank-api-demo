# Architecture

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

## Performance Features

- **Concurrent Processing**: Handles up to 1000 concurrent requests
- **Connection Pooling**: HikariCP for efficient database connections
- **Async Processing**: Non-blocking operation handling with configurable consumer pools
- **Database Transactions**: ACID compliance for all banking operations

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
