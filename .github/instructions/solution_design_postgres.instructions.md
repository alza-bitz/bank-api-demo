---
applyTo: '**'
---

# Application layer
- Integration tests will use the real persistence layer, backed by a Postgres container managed with clj-test-containers/clj-test-containers

# Persistence layer
- Use the DDD repository pattern.
- Repository will handle create account, view account, plus all domain events.
- Repository will be updating or inserting on account, account_event tables.
- Repository will then return event result.
- Use the next.jdbc library for all database operations.
- Use with-logging from next.jdbc to log the SQL and params before any database operations, and to log the results afterwards.
- Avoid SQL strings unless absolutely necessary.
- 
- Unit tests should mock the next.jdbc functions and macros where possible.
- Integration tests will use the real jdbc.next functions, backed by a Postgres container managed with clj-test-containers/clj-test-containers
- Test containers should be made available to tests using use-fixtures and with-binding
- Test containers should only be made available to tests after asserting a successful connection
- To get the mapped port for a test container: `(get (:mapped-ports started-container) 5432)`
- To stop a test container: `(tc/stop! started-container)`


- There are two tables, account and account_event
- For account, primary key is account number, auto increment
- For account_event, primary key is sequence number, auto increment
- For account_event there is an foreign key to account number in account
- For account creation, the db transaction would only insert in account table 
- For account view, the db transaction would only select from account table
- For account events, the db transaction would update account table and insert in account_event table
