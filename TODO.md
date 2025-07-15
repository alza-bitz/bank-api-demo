# Persistence layer
- In the repository, use HugSQL instead of SQL strings
- In the repository, do nil pruning in a result set builder
- SQL migrations
- Datomic repository impl

# Persistence layer (asynchronous API)
- Saving account events includes any operation id if present.

# Application layer (asynchronous API)
- The order of operations could be preserved? For example, consider an account with balance 20, and the result of executing the following operations in either order: withdraw 30, deposit 10 (first command fails, second succeeds, resulting balance 30) vs deposit 10, withdraw 30 (both commands succeed, resulting balance 0)
- A scheduled cleanup function that closes the per-operation result channels and removes them from the state atom after a certain time period has elapsed since the message was put, e.g. 1 minute. Would require a timestamp in the per-operation result channel message?

# Interface layer
- Can reitit be used without ring and/or jetty? What are the other options?
- Consider a http-integration-test using a real http client 
- Health checks
- HATEOAS via HAL or alternatives

# System layer
- Fix Docker Compose persistent volume permissions error

# Missing unit tests
- API tests for withdraw request/response, transfer request/response
- Transfer handler test

# Missing integration tests
- Create account validation test, 400 error for invalid request body
- Deposit validation test, 400 error for invalid request body
- Withdraw validation test, 400 error for invalid request body
- Transfer validation test, 400 error for invalid request body
- Handler test, 400 error for invalid withdraw amount e.g. negative amount
- Repository test, find account events, exception thrown when account not found instead of empty list

# Dev setup
- Run all unit tests on save, or every n minutes
- Minimal Github Actions workflow to run all tests on push

# Other
- Reducible view over account events so it can be processed with a transducer, rather than fully loading all events into memory
- Group integration tests using clojure.test/testing
- Remove unnecessary comments
