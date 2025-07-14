---
applyTo: '**'
---

# Persistence layer
- Create a JdbcAccountRepository record implementing the AccountRepository protocol.
- The repository will be updating or inserting on account and account_event tables.
- The repository will use the next.jdbc library for all database operations.
- The local identity for account events can be implemented using the following sql statement: `insert into account_event (event_sequence, account_number) select coalesce(max(event_sequence), 0) + 1, ? from account_event where account_number = ?`
- For concurrent saving of events on the same account, the repository can catch any exception resulting from the unique constraint violation and retry the operation.
- Table column names should match the [problem_statement_and_requirements.instructions.md](problem_statement_and_requirements.instructions.md) except for conversion to snake_case.
- The repository should have a function that uses camel-snake-kebab to convert accounts and events into what next.jdbc calls "data hash maps" or "hash maps of columns and values", as needed.
- The repository should use as-unqualified-kebab-maps from next.jdbc to convert result sets into accounts and account events as needed.
- Any other differences between column names and the domain model should be handled by conversion functions in the repository.
- Use with-logging from next.jdbc for logging any sql statements before database operations and for logging the result afterwards.
- Avoid SQL strings unless absolutely necessary.
- 
- Unit tests should mock the next.jdbc functions where possible.
- Integration tests will use the real jdbc.next functions, backed by a Postgres container managed with clj-test-containers/clj-test-containers
- The Postgres container should be made available as a test fixture with use-fixtures and with-binding
- The Postgres container should only be made available to tests after asserting a successful connection
- To get the mapped port for a test container use `(get (:mapped-ports started-container) source-port)`
- To stop a test container use `(tc/stop! started-container)`

- There are two tables, account and account_event
- For account, primary key is account number, auto increment
- For account_event, primary key is sequence number, auto increment
- For account_event there is an foreign key to account number in account
- For account creation, the db transaction would only insert in account table 
- For account view, the db transaction would only select from account table
- For account events, the db transaction would update account table and insert in account_event table

# Application layer
- Integration tests will use the real persistence layer, backed by a Postgres container managed with clj-test-containers/clj-test-containers

# Interface layer
- Integration tests will use the real persistence layer, backed by a Postgres container managed with clj-test-containers/clj-test-containers

# Interface layer (asynchronous API)
- The concurrent tests should use a Hikari connection pool for the persistence layer.
- The concurrent tests might need to consider the size of pool and queue length for the Hikari connection pool.